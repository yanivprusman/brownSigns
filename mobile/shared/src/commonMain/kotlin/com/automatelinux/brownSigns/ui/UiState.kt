package com.automatelinux.brownSigns.ui

import com.automatelinux.brownSigns.data.RankedSite
import com.automatelinux.brownSigns.data.model.Category
import com.automatelinux.brownSigns.data.model.Destination
import com.automatelinux.brownSigns.data.model.Place
import com.automatelinux.brownSigns.data.model.PlannedRoute
import com.automatelinux.brownSigns.data.model.Site
import com.automatelinux.brownSigns.geo.LatLon

/**
 * Where the phone thinks it is. These are separate states, not one nullable
 * position, because the list means something different in each: a denied
 * permission has to be asked for, a pending fix will resolve itself, and a
 * granted-but-unfixed phone indoors needs telling.
 */
sealed interface LocationState {
    /** Permission not asked for or not answered yet. */
    data object Pending : LocationState
    data object Denied : LocationState
    /** Permission granted, no fix yet — usually indoors. */
    data object Searching : LocationState
    data class Fixed(val at: LatLon, val accuracyMetres: Float?) : LocationState

    val position: LatLon? get() = (this as? Fixed)?.at
}

/**
 * The road the list can be ordered by. Only [Ready] orders it; in every other
 * state the list stays nearest-first and the header says what the route is
 * waiting on — a list that looked ordered by a route while it wasn't would be
 * wrong in a way nobody could see.
 */
sealed interface RouteState {
    val destination: Destination?

    data object None : RouteState {
        override val destination: Destination? = null
    }

    /** A destination is chosen and the phone has no position to start the road from yet. */
    data class WaitingForFix(override val destination: Destination) : RouteState

    data class Planning(override val destination: Destination) : RouteState

    data class Failed(override val destination: Destination, val reason: String) : RouteState

    data class Ready(val route: PlannedRoute) : RouteState {
        override val destination: Destination get() = route.destination
    }
}

/** The "where are you driving to" sheet, while it is open. */
data class DestinationPickerState(
    val query: String = "",
    /** Sites from the dataset whose name matches — found on the device. */
    val sites: List<Site> = emptyList(),
    /** Towns, addresses and places from the backend's search. */
    val places: List<Place> = emptyList(),
    val searching: Boolean = false,
    /** Why the place search failed; the matching sites above it are unaffected. */
    val error: String? = null,
)

data class BrownSignsUiState(
    val loading: Boolean = true,
    /** Set only when the dataset itself could not be read — the app has nothing to show. */
    val fatal: String? = null,
    val total: Int = 0,
    val ranked: List<RankedSite> = emptyList(),
    val counts: Map<Category, Int> = emptyMap(),
    val selected: Set<Category> = emptySet(),
    val query: String = "",
    val location: LocationState = LocationState.Pending,
    /** Device heading in degrees clockwise from true north; null when there is no compass. */
    val heading: Float? = null,
    val datasetVersion: String? = null,
    val refreshing: Boolean = false,
    /** Set when a refresh was attempted and failed — the bundled list is still on screen. */
    val refreshError: String? = null,
    /**
     * What the last refresh found, shown briefly and then cleared. A check that
     * finishes in ten milliseconds and says nothing is indistinguishable from a
     * gesture the app ignored.
     */
    val refreshNote: String? = null,
    val openSite: Site? = null,
    val route: RouteState = RouteState.None,
    /** Null while the destination sheet is closed. */
    val picker: DestinationPickerState? = null,
)

/** Everything the screen can ask the app to do. */
interface BrownSignsActions {
    fun onQueryChange(query: String)
    fun onToggleCategory(category: Category)
    fun onClearFilters()
    fun onRequestLocation()
    fun onOpenSite(site: Site?)
    fun onNavigateTo(site: Site)
    fun onOpenUrl(url: String)
    fun onRefresh()
    fun onOpenDestinationPicker()
    fun onCloseDestinationPicker()
    fun onDestinationQueryChange(query: String)
    fun onChooseDestination(destination: Destination)
    fun onRetryRoute()
    fun onClearRoute()
}
