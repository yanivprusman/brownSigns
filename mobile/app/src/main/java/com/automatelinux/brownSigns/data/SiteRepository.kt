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
 * where it is useful. The backend is only how a newer list arrives.
 *
 * "Newer" is decided by `generatedAt`, never by the version differing. The
 * version is a content hash, so a rolled-back backend, or a dev server still
 * holding a copy it read at boot, reports a version that is merely *different* —
 * and a repository that treated different as newer would quietly replace a good
 * dataset with a stale one, which is precisely what happened the first time.
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

    /**
     * Reads whichever copy was generated last — an app update can ship a newer
     * dataset than the one previously downloaded, and then the download is dead
     * weight and is deleted. Parsing ~6,000 sites is off the main thread.
     */
    suspend fun load(): SiteData = withContext(Dispatchers.IO) {
        val bundled = readBundled()
        val local = if (downloaded.exists()) runCatching { downloaded.readText() }.getOrElse {
            Log.w(TAG, "downloaded dataset unreadable, discarding it", it)
            downloaded.delete()
            null
        } else null

        val text = if (local != null && generatedAt(local) > generatedAt(bundled)) {
            local
        } else {
            if (local != null) {
                Log.i(TAG, "downloaded dataset is not newer than the bundled one — discarding it")
                downloaded.delete()
            }
            bundled
        }
        json.decodeFromString<SiteData>(text)
    }

    private fun readBundled(): String =
        context.assets.open(ASSET).bufferedReader().use { it.readText() }

    /**
     * Takes a newer dataset from the backend if there is one. Returns null when
     * what we hold is already current, and throws when the backend cannot be
     * reached — the caller decides whether that is worth telling the user about.
     */
    suspend fun refresh(current: SiteData): SiteData? = withContext(Dispatchers.IO) {
        val latest = json.decodeFromString<SiteVersion>(get("api/sites/version"))
        if (latest.version == current.version) return@withContext null
        if (latest.generatedAt <= current.generatedAt) {
            Log.i(TAG, "backend holds ${latest.version} from ${latest.generatedAt}, older than ours — ignoring")
            return@withContext null
        }

        Log.i(TAG, "dataset ${current.version} -> ${latest.version} (${latest.count} sites)")
        val body = get("api/sites")
        // Parse before writing: a truncated download must not replace a good file.
        val parsed = json.decodeFromString<SiteData>(body)
        val tmp = File(context.filesDir, "sites.json.new")
        tmp.writeText(body)
        if (!tmp.renameTo(downloaded)) {
            tmp.delete()
            throw IllegalStateException("could not replace ${downloaded.name}")
        }
        parsed
    }

    /**
     * The ISO-8601 timestamp out of the payload's head, without parsing 1.2 MB
     * of it. The strings sort correctly as text — same length, same zone, UTC.
     */
    private fun generatedAt(payload: String): String =
        GENERATED_AT.find(payload.take(HEAD_BYTES))?.groupValues?.get(1) ?: ""

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
        const val HEAD_BYTES = 512
        val GENERATED_AT = Regex("\"generatedAt\"\\s*:\\s*\"([^\"]+)\"")
    }
}
