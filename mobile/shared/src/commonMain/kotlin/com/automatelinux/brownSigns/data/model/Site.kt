package com.automatelinux.brownSigns.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A destination a brown road sign points at.
 *
 * Field names are the short keys the dataset uses on the wire — the file ships
 * inside the APK and is re-downloaded on every update, so its size is the app's
 * cold-start cost and its download cost both.
 */
@Serializable
data class Site(
    /** OSM type-letter + id, e.g. "w1234567". Stable across dataset rebuilds. */
    val id: String,
    val cat: Category,
    /** Hebrew name (the English one, when OSM has no Hebrew). */
    val he: String,
    val lat: Double,
    val lon: Double,
    val en: String? = null,
    val ar: String? = null,
    /** Metres above sea level. */
    val ele: Int? = null,
    val desc: String? = null,
    /** Wikipedia article as "<lang>:<title>". */
    val wp: String? = null,
    val wd: String? = null,
    val url: String? = null,
    val img: String? = null,
    /** Operator, e.g. רשות הטבע והגנים. */
    val op: String? = null,
    /** Opening hours, in OSM syntax. */
    val oh: String? = null,
    val fee: Boolean? = null,
) {
    val wikipediaUrl: String?
        get() {
            val raw = wp ?: return null
            val lang = raw.substringBefore(':', "he")
            val title = raw.substringAfter(':', raw)
            if (title.isBlank()) return null
            return "https://$lang.wikipedia.org/wiki/" + title.replace(' ', '_')
        }

    /**
     * The place in Google Earth. A link, not an intent aimed at Earth's package:
     * Earth claims earth.google.com as a verified app link, so a phone that has
     * Earth opens the app and one that doesn't opens Earth on the web. It searches
     * the coordinates rather than the name — the pin lands on this exact spot.
     */
    val googleEarthUrl: String
        get() = "https://earth.google.com/web/search/$lat,$lon"
}

@Serializable
enum class Category(val he: String, val plural: String) {
    @SerialName("national_park") NATIONAL_PARK("גן לאומי", "גנים לאומיים"),
    @SerialName("nature_reserve") NATURE_RESERVE("שמורת טבע", "שמורות טבע"),
    @SerialName("museum") MUSEUM("מוזיאון", "מוזיאונים"),
    @SerialName("archaeology") ARCHAEOLOGY("אתר ארכיאולוגי", "ארכיאולוגיה"),
    @SerialName("heritage") HERITAGE("אתר מורשת", "מורשת"),
    @SerialName("attraction") ATTRACTION("אתר תיירות", "אטרקציות"),
    @SerialName("viewpoint") VIEWPOINT("תצפית", "תצפיות"),
}

@Serializable
data class SiteData(
    val version: String,
    val generatedAt: String,
    val source: String,
    val count: Int,
    val sites: List<Site>,
)

/** The freshness probe the phone calls on resume, instead of the 1.2 MB payload. */
@Serializable
data class SiteVersion(val version: String, val count: Int, val generatedAt: String)
