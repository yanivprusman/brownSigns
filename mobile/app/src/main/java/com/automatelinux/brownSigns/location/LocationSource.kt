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
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** The phone's own answer to "where am I" and "which way am I facing". */
@Singleton
class LocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Position updates. The list re-sorts on every fix, so this is deliberately
     * unhurried — 5 s and 25 m is far finer than the difference between two
     * destinations 12 km apart, and does not cost the battery a navigation app would.
     */
    @SuppressLint("MissingPermission")
    fun positions(): Flow<Location> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val client = LocationServices.getFusedLocationProviderClient(context)

        // The last known fix makes the list correct immediately instead of after
        // the first satellite lock.
        runCatching { client.lastLocation.await() }.getOrNull()?.let { trySend(it) }

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(25f)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    /**
     * Heading in degrees clockwise from TRUE north, or an empty flow on a phone
     * with no rotation sensor — in which case the UI names the direction rather
     * than drawing an arrow that would be pointing nowhere.
     *
     * The rotation vector reports magnetic north; `declinationAt` turns that into
     * true north, which is what a bearing computed from coordinates means.
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
        // A compass reading jitters by several degrees at rest. Averaging the
        // heading as a unit vector smooths it without the wrap-around artefact
        // that averaging the angle itself would produce at 0°/360°.
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
        const val SMOOTHING = 0.12
    }
}
