package com.automatelinux.brownSigns.ui

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatelinux.brownSigns.data.SiteRepository
import com.automatelinux.brownSigns.data.countByCategory
import com.automatelinux.brownSigns.data.model.Category
import com.automatelinux.brownSigns.data.model.Site
import com.automatelinux.brownSigns.data.model.SiteData
import com.automatelinux.brownSigns.data.rankSites
import com.automatelinux.brownSigns.geo.LatLon
import com.automatelinux.brownSigns.location.LocationSource
import com.automatelinux.brownSigns.util.ScreenTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class BrownSignsViewModel @Inject constructor(
    private val repository: SiteRepository,
    private val locationSource: LocationSource,
) : ViewModel(), BrownSignsActions {

    private val _state = MutableStateFlow(BrownSignsUiState())
    val state: StateFlow<BrownSignsUiState> = _state.asStateFlow()

    /** Things only an Activity can do — firing an Intent at another app. */
    private val _effects = MutableSharedFlow<Effect>(extraBufferCapacity = 4)
    val effects = _effects

    sealed interface Effect {
        data class OpenUrl(val url: String) : Effect
        data class Navigate(val site: Site) : Effect
        data object AskLocationPermission : Effect
    }

    private var held: SiteData? = null
    private var all: List<Site> = emptyList()
    private var lastFix: Location? = null
    private var rankingJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val data = repository.load()
                held = data
                all = data.sites
                _state.value = _state.value.copy(
                    loading = false,
                    total = data.count,
                    datasetVersion = data.version,
                    counts = countByCategory(all),
                )
                rerank()
                if (locationSource.hasPermission()) startLocation() else askPermission()
                refreshInBackground(data)
            } catch (e: Exception) {
                Log.e(TAG, "dataset failed to load", e)
                _state.value = _state.value.copy(loading = false, fatal = e.message ?: e.toString())
            }
        }
    }

    /** Called by the Activity once the permission dialog is answered. */
    fun onPermissionResult(granted: Boolean) {
        if (granted) startLocation()
        else _state.value = _state.value.copy(location = LocationState.Denied)
    }

    private fun askPermission() {
        _state.value = _state.value.copy(location = LocationState.Pending)
        _effects.tryEmit(Effect.AskLocationPermission)
    }

    private fun startLocation() {
        _state.value = _state.value.copy(location = LocationState.Searching)

        viewModelScope.launch {
            locationSource.positions().collect { fix ->
                lastFix = fix
                _state.value = _state.value.copy(
                    location = LocationState.Fixed(
                        at = LatLon(fix.latitude, fix.longitude),
                        accuracyMetres = if (fix.hasAccuracy()) fix.accuracy else null,
                    ),
                )
                rerank()
            }
        }

        viewModelScope.launch {
            var last = Float.NaN
            locationSource.headings { lastFix }
                // The arrow is redrawn, not re-sorted — but a raw sensor at 60 Hz
                // would still recompose the visible rows sixty times a second.
                .filter { deg ->
                    val moved = last.isNaN() || angularDelta(deg, last) > 1.5f
                    if (moved) last = deg
                    moved
                }
                .collect { _state.value = _state.value.copy(heading = it) }
        }
    }

    private fun angularDelta(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    private fun refreshInBackground(current: SiteData) {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true, refreshError = null)
            try {
                val newer = repository.refresh(current)
                if (newer != null) {
                    held = newer
                    all = newer.sites
                    _state.value = _state.value.copy(
                        total = newer.count,
                        datasetVersion = newer.version,
                        counts = countByCategory(all, _state.value.query),
                    )
                    rerank()
                }
                _state.value = _state.value.copy(refreshing = false)
            } catch (e: Exception) {
                // The bundled list is already on screen and correct; a failed
                // update is a footnote, not a failure of the app.
                Log.i(TAG, "dataset refresh skipped: ${e.message}")
                _state.value = _state.value.copy(refreshing = false, refreshError = e.message)
            }
        }
    }

    /** Filter + sort 6,000 sites off the main thread; the newest request wins. */
    private fun rerank() {
        val snapshot = _state.value
        rankingJob?.cancel()
        rankingJob = viewModelScope.launch {
            val ranked = withContext(Dispatchers.Default) {
                rankSites(
                    sites = all,
                    origin = snapshot.location.position,
                    categories = snapshot.selected,
                    query = snapshot.query,
                )
            }
            _state.value = _state.value.copy(ranked = ranked)
        }
    }

    override fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query, counts = countByCategory(all, query))
        rerank()
    }

    override fun onToggleCategory(category: Category) {
        val next = _state.value.selected.toMutableSet()
        if (!next.add(category)) next.remove(category)
        _state.value = _state.value.copy(selected = next)
        rerank()
    }

    override fun onClearFilters() {
        _state.value = _state.value.copy(selected = emptySet())
        rerank()
    }

    override fun onRequestLocation() = askPermission()

    override fun onOpenSite(site: Site?) {
        ScreenTracker.currentScreen = if (site == null) "רשימת יעדים" else "פרטי יעד"
        _state.value = _state.value.copy(openSite = site)
    }

    override fun onNavigateTo(site: Site) {
        _effects.tryEmit(Effect.Navigate(site))
    }

    override fun onOpenUrl(url: String) {
        _effects.tryEmit(Effect.OpenUrl(url))
    }

    override fun onRefresh() {
        held?.let { refreshInBackground(it) }
    }

    private companion object { const val TAG = "BrownSignsVM" }
}
