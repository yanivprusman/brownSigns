package com.automatelinux.brownSigns.ui

import com.automatelinux.brownSigns.data.RankedSite
import com.automatelinux.brownSigns.data.model.Category
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
    val openSite: Site? = null,
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
}
