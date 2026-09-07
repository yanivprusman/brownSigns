package com.automatelinux.brownSigns.data

import android.content.Context
import android.util.Log
import com.automatelinux.brownSigns.BuildConfig
import com.automatelinux.brownSigns.data.model.SiteData
import com.automatelinux.brownSigns.data.model.SiteVersion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The list of destinations.
 *
 * The APK carries the whole dataset in its assets, so the app is complete the
 * first time it opens, on a mountain road with no signal — which is exactly
 * where it is useful. The backend is only how a newer list arrives: a downloaded
 * copy in filesDir supersedes the bundled one, and is used from then on.
 */
@Singleton
class SiteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val downloaded: File get() = File(context.filesDir, "sites.json")

    /** Reads whichever copy is current. Parsing ~6,000 sites is off the main thread. */
    suspend fun load(): SiteData = withContext(Dispatchers.IO) {
        val text = if (downloaded.exists()) {
            runCatching { downloaded.readText() }.getOrElse {
                Log.w(TAG, "downloaded dataset unreadable, deleting it", it)
                downloaded.delete()
                readBundled()
            }
        } else {
            readBundled()
        }
        json.decodeFromString<SiteData>(text)
    }

    private fun readBundled(): String =
        context.assets.open(ASSET).bufferedReader().use { it.readText() }

    /**
     * Asks the backend whether a newer dataset exists and, if so, takes it.
     * Returns the new data, or null when what we hold is already current.
     * Throws when the backend cannot be reached — the caller decides whether
     * that is worth telling the user about.
     */
    suspend fun refresh(currentVersion: String): SiteData? = withContext(Dispatchers.IO) {
        val latest = get("api/sites/version").let { json.decodeFromString<SiteVersion>(it) }
        if (latest.version == currentVersion) return@withContext null

        Log.i(TAG, "dataset $currentVersion -> ${latest.version} (${latest.count} sites)")
        val body = get("api/sites")
        val parsed = json.decodeFromString<SiteData>(body)
        // Parse before writing: a truncated download must not replace a good file.
        val tmp = File(context.filesDir, "sites.json.new")
        tmp.writeText(body)
        if (!tmp.renameTo(downloaded)) {
            tmp.delete()
            throw IllegalStateException("could not replace ${downloaded.name}")
        }
        parsed
    }

    private fun get(path: String): String {
        val url = BuildConfig.API_BASE_URL.trimEnd('/') + "/" + path
        http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("$url -> HTTP ${res.code}")
            return res.body?.string() ?: throw IllegalStateException("$url -> empty body")
        }
    }

    private companion object {
        const val TAG = "SiteRepository"
        const val ASSET = "sites.json"
    }
}
