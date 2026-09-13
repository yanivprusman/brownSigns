package com.automatelinux.brownSigns.data

import android.content.Context
import android.util.Log
import com.automatelinux.brownSigns.BuildConfig
import com.automatelinux.brownSigns.data.model.Destination
import com.automatelinux.brownSigns.data.model.Place
import com.automatelinux.brownSigns.data.model.PlaceSearchResponse
import com.automatelinux.brownSigns.data.model.PlannedRoute
import com.automatelinux.brownSigns.data.model.RouteResponse
import com.automatelinux.brownSigns.geo.LatLon
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** The backend answered, and the answer was an error. */
class BackendException(val status: Int, message: String) : Exception(message)

/** The backend could not be reached at all — no signal, or the server is down. */
class BackendUnreachable(url: String, cause: IOException) : Exception("$url: ${cause.message}", cause)

/**
 * Destinations and the road to them.
 *
 * The road is worked out by the backend once per destination and then kept in a
 * file: the trip that wants "what is along the way" goes through places with no
 * signal, and a route held only in memory would be gone the first time Android
 * reclaimed the app.
 */
@Singleton
class RouteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val saved: File get() = File(context.filesDir, "route.json")

    suspend fun searchPlaces(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        json.decodeFromString<PlaceSearchResponse>(get("api/places?q=$q")).places
    }

    suspend fun route(from: LatLon, to: Destination): RouteResponse = withContext(Dispatchers.IO) {
        json.decodeFromString<RouteResponse>(get("api/route?from=${from.lat},${from.lon}&to=${to.lat},${to.lon}"))
    }

    /** The route in use when the app last ran, or null. An unreadable file is deleted, not guessed at. */
    suspend fun loadSaved(): PlannedRoute? = withContext(Dispatchers.IO) {
        if (!saved.exists()) return@withContext null
        runCatching { json.decodeFromString<PlannedRoute>(saved.readText()) }.getOrElse {
            Log.w(TAG, "saved route unreadable — deleting it", it)
            saved.delete()
            null
        }
    }

    suspend fun save(route: PlannedRoute) {
        withContext(Dispatchers.IO) {
            // Written beside, then renamed: a half-written route must not replace a whole one.
            val tmp = File(context.filesDir, "route.json.new")
            tmp.writeText(json.encodeToString(PlannedRoute.serializer(), route))
            if (!tmp.renameTo(saved)) {
                tmp.delete()
                throw IllegalStateException("could not replace ${saved.name}")
            }
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) { saved.delete() }
    }

    private fun get(path: String): String {
        val url = BuildConfig.API_BASE_URL.trimEnd('/') + "/" + path
        val (code, body) = try {
            http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                res.code to res.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw BackendUnreachable(url, e)
        }
        if (code !in 200..299) throw BackendException(code, errorOf(body) ?: "HTTP $code")
        return body
    }

    /** The `error` field the backend puts in every failure it answers with. */
    private fun errorOf(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
    }.getOrNull()

    private companion object {
        const val TAG = "RouteRepository"
    }
}
