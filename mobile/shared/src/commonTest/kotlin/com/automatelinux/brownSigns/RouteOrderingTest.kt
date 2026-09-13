package com.automatelinux.brownSigns

import com.automatelinux.brownSigns.data.model.Category
import com.automatelinux.brownSigns.data.model.Site
import com.automatelinux.brownSigns.data.rankAlongRoute
import com.automatelinux.brownSigns.data.sitesNamed
import com.automatelinux.brownSigns.geo.LatLon
import com.automatelinux.brownSigns.geo.RouteLine
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteOrderingTest {
    /** Due north along longitude 35°, from 31.0°N to 31.1°N — about 11.1 km. */
    private val straight = RouteLine(listOf(31.0, 35.0, 31.1, 35.0))

    private fun near(expected: Double, actual: Double, tolerance: Double) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, got $actual")

    private fun site(id: String, lat: Double, lon: Double, name: String = id, cat: Category = Category.VIEWPOINT) =
        Site(id = id, cat = cat, he = name, lat = lat, lon = lon)

    private fun positions(route: RouteLine, sites: List<Site>) =
        sites.associate { it.id to route.locate(LatLon(it.lat, it.lon)) }

    @Test
    fun lengthOfAStraightRoute() = near(11_119.5, straight.lengthMetres, 5.0)

    @Test
    fun aPointBesideTheRoadIsMeasuredSquareToIt() {
        val p = straight.locate(LatLon(31.05, 35.01))
        near(952.6, p.offMetres, 5.0) // 0.01° of longitude at 31.05°N
        near(5_559.7, p.alongMetres, 5.0)
    }

    @Test
    fun beforeTheStartTheNearestPointIsTheStart() {
        val p = straight.locate(LatLon(30.99, 35.0))
        near(1_111.9, p.offMetres, 5.0)
        assertEquals(0.0, p.alongMetres)
    }

    @Test
    fun alongCountsEveryEarlierSegment() {
        // North, then east along 31.1°N.
        val bent = RouteLine(listOf(31.0, 35.0, 31.1, 35.0, 31.1, 35.1))
        val p = bent.locate(LatLon(31.11, 35.05))
        near(1_111.9, p.offMetres, 5.0)
        near(11_119.5 + 4_760.6, p.alongMetres, 20.0)
    }

    @Test
    fun sitesOnTheRoadComeInDrivingOrderAndFarOnesLast() {
        val sites = listOf(
            site("far", 31.02, 35.03), // ~2.9 km east, near the start
            site("late", 31.08, 35.0003), // ~30 m off, 8.9 km along
            site("early", 31.02, 35.0005), // ~50 m off, 2.2 km along
        )
        val ranked = rankAlongRoute(sites, positions(straight, sites), origin = null, originOnRoute = null)
        assertEquals(listOf("early", "late", "far"), ranked.map { it.site.id })
        assertNull(ranked.first().onRoute!!.aheadMetres)
    }

    @Test
    fun aheadIsMeasuredFromWhereThePhoneIsOnTheRoad() {
        val sites = listOf(site("passed", 31.02, 35.0), site("coming", 31.08, 35.0))
        val me = LatLon(31.05, 35.0)
        val ranked = rankAlongRoute(sites, positions(straight, sites), me, straight.locate(me))
        val ahead = ranked.associate { it.site.id to it.onRoute!!.aheadMetres!! }
        near(-3_335.8, ahead.getValue("passed"), 5.0)
        near(3_335.8, ahead.getValue("coming"), 5.0)
    }

    @Test
    fun offTheRouteThePhoneIsNotToldHowFarAhead() {
        val sites = listOf(site("a", 31.05, 35.0))
        val me = LatLon(31.05, 35.06) // ~5.7 km east of the road
        val ranked = rankAlongRoute(sites, positions(straight, sites), me, straight.locate(me))
        assertNull(ranked.single().onRoute!!.aheadMetres)
    }

    @Test
    fun categoriesAndSearchStillFilterARouteOrderedList() {
        val sites = listOf(
            site("park", 31.05, 35.0, "גן לאומי", Category.NATIONAL_PARK),
            site("view", 31.05, 35.0, "תצפית", Category.VIEWPOINT),
        )
        val ranked = rankAlongRoute(
            sites, positions(straight, sites), origin = null, originOnRoute = null,
            categories = setOf(Category.VIEWPOINT),
        )
        assertEquals(listOf("view"), ranked.map { it.site.id })
    }

    @Test
    fun destinationSearchPutsNamesThatStartWithTheQueryFirst() {
        val sites = listOf(
            site("1", 31.0, 35.0, "תצפית מצדה", Category.VIEWPOINT),
            site("2", 31.0, 35.0, "מצדה", Category.NATIONAL_PARK),
            site("3", 31.0, 35.0, "קיסריה", Category.NATIONAL_PARK),
        )
        assertEquals(listOf("2", "1"), sitesNamed(sites, "מצדה").map { it.id })
        assertEquals(emptyList(), sitesNamed(sites, "מ"))
    }
}
