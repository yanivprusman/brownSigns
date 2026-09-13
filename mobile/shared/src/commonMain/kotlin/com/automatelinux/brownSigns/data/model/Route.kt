package com.automatelinux.brownSigns.data.model

import kotlinx.serialization.Serializable

/**
 * Where the user is driving to: a town or address from the place search, or one
 * of the sites in the list itself — "what is on the way to Masada" is the
 * question ordering by a route exists for.
 */
@Serializable
data class Destination(
    val name: String,
    /** What tells two places of the same name apart — a town, a neighbourhood, a category. */
    val detail: String = "",
    val lat: Double,
    val lon: Double,
    /** Set when the destination is a site from the dataset. */
    val siteId: String? = null,
)

fun Site.asDestination() = Destination(name = he, detail = cat.he, lat = lat, lon = lon, siteId = id)

/** One answer from the backend's place search. */
@Serializable
data class Place(val name: String, val area: String = "", val lat: Double, val lon: Double)

@Serializable
data class PlaceSearchResponse(val places: List<Place>)

/** The road the backend found, already thinned to what measuring against it needs. */
@Serializable
data class RouteResponse(
    val distanceM: Double,
    val durationSec: Double,
    /** lat, lon, lat, lon, … */
    val path: List<Double>,
)

/**
 * The route the list is ordered by. Kept on the device, because the trip it is
 * for goes where the signal does not.
 */
@Serializable
data class PlannedRoute(
    val destination: Destination,
    val distanceM: Double,
    val durationSec: Double,
    /** lat, lon, lat, lon, … */
    val path: List<Double>,
    /** Epoch milliseconds. */
    val plannedAt: Long,
)
