package com.automatelinux.brownSigns.data

import com.automatelinux.brownSigns.data.model.Category
import com.automatelinux.brownSigns.data.model.Site
import com.automatelinux.brownSigns.geo.LatLon
import com.automatelinux.brownSigns.geo.bearingDegrees
import com.automatelinux.brownSigns.geo.distanceMetres
import com.automatelinux.brownSigns.geo.rankingDistance

/** One site as the list shows it: how far, and in which direction. */
data class RankedSite(
    val site: Site,
    /** Null until there is a location fix. */
    val metres: Double?,
    /** Degrees clockwise from true north; null until there is a location fix. */
    val bearing: Double?,
)

/**
 * Hebrew typed into a search box rarely carries niqqud, geresh or quotes, while
 * OSM names often do — so both sides are stripped before they are compared.
 */
fun normaliseForSearch(text: String): String {
    val out = StringBuilder(text.length)
    for (ch in text) {
        when {
            ch in '֑'..'ׇ' -> Unit                    // niqqud + cantillation
            ch == '׳' || ch == '״' -> Unit            // geresh, gershayim
            ch == '"' || ch == '\'' || ch == '`' -> Unit
            ch == '-' || ch == '–' || ch == '—' -> out.append(' ')
            else -> out.append(ch.lowercaseChar())
        }
    }
    return out.toString().trim().replace(Regex("\\s+"), " ")
}

private fun Site.matches(needle: String): Boolean =
    normaliseForSearch(he).contains(needle) ||
        en?.let { normaliseForSearch(it).contains(needle) } == true ||
        ar?.let { normaliseForSearch(it).contains(needle) } == true

/**
 * Filter, then order: nearest first when there is a fix, alphabetically when
 * there is not. The two orders are deliberately different states rather than one
 * order with a stand-in origin — a list silently sorted from a guessed point
 * would look exactly like a correct one.
 */
fun rankSites(
    sites: List<Site>,
    origin: LatLon?,
    categories: Set<Category> = emptySet(),
    query: String = "",
): List<RankedSite> {
    val needle = normaliseForSearch(query)
    val filtered = sites.asSequence()
        .filter { categories.isEmpty() || it.cat in categories }
        .filter { needle.isEmpty() || it.matches(needle) }
        .toList()

    if (origin == null) {
        return filtered
            .sortedWith(compareBy(HEBREW_ORDER) { it.he })
            .map { RankedSite(it, null, null) }
    }

    return filtered
        .sortedBy { rankingDistance(origin, LatLon(it.lat, it.lon)) }
        .map {
            val there = LatLon(it.lat, it.lon)
            RankedSite(it, distanceMetres(origin, there), bearingDegrees(origin, there))
        }
}

/**
 * Hebrew sorts by code point in the right order already; the comparator exists so
 * a leading quote or bracket in an OSM name does not float it to the top.
 */
private val HEBREW_ORDER = Comparator<String> { a, b ->
    normaliseForSearch(a).compareTo(normaliseForSearch(b))
}

/** How many sites of each category survive the current query — drives the chip counts. */
fun countByCategory(sites: List<Site>, query: String = ""): Map<Category, Int> {
    val needle = normaliseForSearch(query)
    val counts = mutableMapOf<Category, Int>()
    for (s in sites) {
        if (needle.isNotEmpty() && !s.matches(needle)) continue
        counts[s.cat] = (counts[s.cat] ?: 0) + 1
    }
    return counts
}
