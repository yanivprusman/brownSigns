package com.automatelinux.brownSigns.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The phone's own answer to "where am I" and "which way am I facing".
 *
 * This goes through the platform's [LocationManager] rather than Play Services'
 * fused provider: the app asks for one modest thing — a position good to a few
 * tens of metres — and LocationManager delivers it on every Android device,
 * including one with no Google Play Services and an emulator being fed
 * `adb emu geo fix`. A fused provider would add a dependency to answer a
 * question that does not need it.
 */
@Singleton
class LocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Position updates, listening on GPS and network together — not one with the
     * other as a stand-in. They answer at different speeds and accuracies
     * (network first and coarse, GPS later and fine), and [isBetterThan] decides
     * which of the two currently describes where the phone is.
     *
     * 5 s and 25 m is far finer than the difference between two destinations
     * kilometres apart, and costs a fraction of what a navigation app would.
     */
    @SuppressLint("MissingPermission")
    fun positions(): Flow<Location> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val lm = manager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        if (providers.isEmpty()) {
            close()
            return@callbackFlow
        }

        var best: Location? = null
        fun offer(candidate: Location?) {
            if (candidate == null) return
            if (candidate.isBetterThan(best)) {
                best = candidate
                trySend(candidate)
            }
        }

        // A remembered fix makes the list correct immediately instead of after
        // the first satellite lock.
        providers.forEach { offer(runCatching { lm.getLastKnownLocation(it) }.getOrNull()) }

        val listener = LocationListener { offer(it) }
        providers.forEach {
            lm.requestLocationUpdates(it, UPDATE_INTERVAL_MS, UPDATE_DISTANCE_M, listener, Looper.getMainLooper())
        }
        awaitClose { lm.removeUpdates(listener) }
    }

    /**
     * Newer wins, unless the newer fix is markedly vaguer than a still-fresh one:
     * a 2 km cell-tower estimate should not displace a 10 m GPS fix from a minute ago.
     */
    private fun Location.isBetterThan(other: Location?): Boolean {
        if (other == null) return true
        val newerBy = time - other.time
        if (newerBy > STALE_AFTER_MS) return true
        if (newerBy < 0) return false
        if (!hasAccuracy()) return !other.hasAccuracy()
        if (!other.hasAccuracy()) return true
        return accuracy <= other.accuracy * 2f
    }

    /**
     * Heading in degrees clockwise from TRUE north, or an empty flow on a phone
     * with no rotation sensor — in which case the UI names the direction rather
     * than drawing an arrow that would be pointing nowhere.
     *
     * The rotation vector reports magnetic north; the declination at the current
     * position turns that into true north, which is what a bearing computed from
     * coordinates means.
     */
    fun headings(declinationAt: () -> Location?): Flow<Float> = callbackFlow {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotation = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotation == null) {
            close()
            return@callbackFlow
        }

        val matrix = FloatArray(9)
        val orientation = FloatArray(3)
        // A compass jitters by several degrees at rest. Averaging the heading as
        // a unit vector smooths it without the wrap-around artefact that
        // averaging the angle itself would produce at 0°/360°.
        var smoothSin = 0.0
        var smoothCos = 0.0
        var seeded = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, orientation)
                val magnetic = Math.toDegrees(orientation[0].toDouble())
                val declination = declinationAt()?.let {
                    GeomagneticField(
                        it.latitude.toFloat(),
                        it.longitude.toFloat(),
                        it.altitude.toFloat(),
                        System.currentTimeMillis(),
                    ).declination.toDouble()
                } ?: 0.0
                val trueNorth = Math.toRadians(magnetic + declination)

                if (!seeded) {
                    smoothSin = sin(trueNorth); smoothCos = cos(trueNorth); seeded = true
                } else {
                    smoothSin += (sin(trueNorth) - smoothSin) * SMOOTHING
                    smoothCos += (cos(trueNorth) - smoothCos) * SMOOTHING
                }
                val degrees = (Math.toDegrees(atan2(smoothSin, smoothCos)) + 360.0) % 360.0
                trySend(degrees.toFloat())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensors.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensors.unregisterListener(listener) }
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 5_000L
        const val UPDATE_DISTANCE_M = 25f
        const val STALE_AFTER_MS = 60_000L
        const val SMOOTHING = 0.12
    }
}
