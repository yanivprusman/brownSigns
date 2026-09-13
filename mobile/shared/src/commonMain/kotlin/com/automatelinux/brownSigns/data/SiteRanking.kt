package com.automatelinux.brownSigns.data

import com.automatelinux.brownSigns.data.model.Category
import com.automatelinux.brownSigns.data.model.Site
import com.automatelinux.brownSigns.geo.LatLon
import com.automatelinux.brownSigns.geo.RoutePosition
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
    /** Where the site lies against the planned road; null unless the list is ordered by one. */
    val onRoute: RouteSpot? = null,
)

/** A site measured against the road the user is driving. */
data class RouteSpot(
    /** How far the site is from the road. */
    val offMetres: Double,
    /** How far along the road, from where it starts, the site comes up. */
    val alongMetres: Double,
    /**
     * Road still to drive until the site — negative once it is behind. Null when
     * there is no fix or the phone is not on the road: "in 12 km" measured from
     * somewhere off the route would be a number about a different trip.
     */
    val aheadMetres: Double?,
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

private fun filterSites(sites: List<Site>, categories: Set<Category>, query: String): List<Site> {
    val needle = normaliseForSearch(query)
    return sites.asSequence()
        .filter { categories.isEmpty() || it.cat in categories }
        .filter { needle.isEmpty() || it.matches(needle) }
        .toList()
}

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
    val filtered = filterSites(sites, categories, query)

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
 * Filter, then order by how far each site lies from the planned road.
 *
 * Distances are compared in [OFF_ROUTE_STEP_M] steps, and sites in the same step
 * come in the order the road reaches them: 30 m or 80 m off the road is the same
 * stop to a driver, and the top of the list then reads like the road itself.
 *
 * The order depends only on the route, never on where the phone is, so it does
 * not reshuffle while driving — only the "in N km" figure moves.
 *
 * [positions] must hold every site in [sites]; the caller measures them together.
 */
fun rankAlongRoute(
    sites: List<Site>,
    positions: Map<String, RoutePosition>,
    origin: LatLon?,
    originOnRoute: RoutePosition?,
    categories: Set<Category> = emptySet(),
    query: String = "",
): List<RankedSite> {
    val from = originOnRoute?.takeIf { it.offMetres <= ON_ROUTE_M }
    return filterSites(sites, categories, query)
        .map { it to positions.getValue(it.id) }
        .sortedWith(
            compareBy<Pair<Site, RoutePosition>>(
                { (it.second.offMetres / OFF_ROUTE_STEP_M).toInt() },
                { it.second.alongMetres },
            ),
        )
        .map { (site, position) ->
            val there = LatLon(site.lat, site.lon)
            RankedSite(
                site = site,
                metres = origin?.let { distanceMetres(it, there) },
                bearing = origin?.let { bearingDegrees(it, there) },
                onRoute = RouteSpot(
                    offMetres = position.offMetres,
                    alongMetres = position.alongMetres,
                    aheadMetres = from?.let { position.alongMetres - it.alongMetres },
                ),
            )
        }
}

/**
 * Sites whose name matches, for choosing one as a destination: names that start
 * with what was typed first, then the kinds of place people drive to on purpose.
 */
fun sitesNamed(sites: List<Site>, query: String, limit: Int = 5): List<Site> {
    val needle = normaliseForSearch(query)
    if (needle.length < 2) return emptyList()
    return sites.asSequence()
        .filter { it.matches(needle) }
        .sortedWith(
            compareBy<Site>(
                { !normaliseForSearch(it.he).startsWith(needle) },
                { it.cat.ordinal },
                { normaliseForSearch(it.he) },
            ),
        )
        .take(limit)
        .toList()
}

/**
 * Hebrew sorts by code point in the right order already; the comparator exists so
 * a leading quote or bracket in an OSM name does not float it to the top.
 */
private val HEBREW_ORDER = Comparator<String> { a, b ->
    normaliseForSearch(a).compareTo(normaliseForSearch(b))
}

private const val OFF_ROUTE_STEP_M = 100.0

/** Farther than this from the road, the phone is not on the route and "in N km" is not said. */
private const val ON_ROUTE_M = 2_000.0

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
