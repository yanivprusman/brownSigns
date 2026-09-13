package com.automatelinux.brownSigns.geo

import kotlin.math.PI
import kotlin.math.cos

/** Where a point lies against a route. */
data class RoutePosition(
    /** Shortest distance from the point to the road, in metres. */
    val offMetres: Double,
    /** Distance along the road, from its start, to the point on it nearest this one. */
    val alongMetres: Double,
)

/**
 * A route to measure against. Built once per route and then asked about each of
 * the ~6,000 sites, so everything per segment is precomputed and the inner loop
 * is arithmetic over flat arrays.
 *
 * The nearest segment is found in a flat projection local to each segment
 * (longitude scaled by the cosine of its latitude); over a country this size that
 * cannot pick a different segment in any way the list would show. The distance to
 * the point found on it is then taken exactly.
 */
class RouteLine(path: List<Double>) {
    private val size = path.size / 2
    private val lat = DoubleArray(size) { path[2 * it] }
    private val lon = DoubleArray(size) { path[2 * it + 1] }
    private val cumulative = DoubleArray(size)
    private val scale: DoubleArray

    val lengthMetres: Double

    init {
        require(path.size % 2 == 0 && size >= 2) {
            "a route needs at least two lat,lon pairs; got ${path.size} numbers"
        }
        scale = DoubleArray(size - 1) { cos((lat[it] + lat[it + 1]) / 2 * PI / 180.0) }
        for (i in 1 until size) {
            cumulative[i] = cumulative[i - 1] +
                distanceMetres(LatLon(lat[i - 1], lon[i - 1]), LatLon(lat[i], lon[i]))
        }
        lengthMetres = cumulative[size - 1]
    }

    fun locate(point: LatLon): RoutePosition {
        var bestD2 = Double.MAX_VALUE
        var best = 0
        var bestT = 0.0
        for (i in 0 until size - 1) {
            val k = scale[i]
            val ax = lon[i] * k
            val ay = lat[i]
            val dx = lon[i + 1] * k - ax
            val dy = lat[i + 1] - ay
            val px = point.lon * k - ax
            val py = point.lat - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((px * dx + py * dy) / len2).coerceIn(0.0, 1.0)
            val ex = px - t * dx
            val ey = py - t * dy
            val d2 = ex * ex + ey * ey
            if (d2 < bestD2) {
                bestD2 = d2
                best = i
                bestT = t
            }
        }
        val nearest = LatLon(
            lat[best] + bestT * (lat[best + 1] - lat[best]),
            lon[best] + bestT * (lon[best + 1] - lon[best]),
        )
        return RoutePosition(
            offMetres = distanceMetres(point, nearest),
            alongMetres = cumulative[best] + bestT * (cumulative[best + 1] - cumulative[best]),
        )
    }
}
