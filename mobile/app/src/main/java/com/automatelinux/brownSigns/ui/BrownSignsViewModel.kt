package com.automatelinux.brownSigns.ui

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatelinux.brownSigns.data.BackendException
import com.automatelinux.brownSigns.data.BackendUnreachable
import com.automatelinux.brownSigns.data.RouteRepository
import com.automatelinux.brownSigns.data.SiteRepository
import com.automatelinux.brownSigns.data.countByCategory
import com.automatelinux.brownSigns.data.model.Category
import com.automatelinux.brownSigns.data.model.Destination
import com.automatelinux.brownSigns.data.model.PlannedRoute
import com.automatelinux.brownSigns.data.model.Site
import com.automatelinux.brownSigns.data.model.SiteData
import com.automatelinux.brownSigns.data.rankAlongRoute
import com.automatelinux.brownSigns.data.rankSites
import com.automatelinux.brownSigns.data.sitesNamed
import com.automatelinux.brownSigns.geo.LatLon
import com.automatelinux.brownSigns.geo.RouteLine
import com.automatelinux.brownSigns.geo.RoutePosition
import com.automatelinux.brownSigns.location.LocationSource
import com.automatelinux.brownSigns.util.ScreenTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class BrownSignsViewModel @Inject constructor(
    private val repository: SiteRepository,
    private val routes: RouteRepository,
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

    /**
     * A route measured against one particular copy of the dataset. They are held
     * together because ordering by a route needs a position for every site it
     * orders: a refreshed dataset is ordered by the route only once it has been
     * measured again, and until then the old copy and its positions stand.
     */
    private class RouteIndex(
        val route: PlannedRoute,
        val line: RouteLine,
        val sites: List<Site>,
        val positions: Map<String, RoutePosition>,
    )

    private var held: SiteData? = null
    private var all: List<Site> = emptyList()
    private var lastFix: Location? = null
    private var rankingJob: Job? = null
    private var refreshJob: Job? = null
    private var routeIndex: RouteIndex? = null
    private var routeJob: Job? = null
    private var placesJob: Job? = null

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
                restoreRoute()
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
                // A destination chosen before the first fix has been waiting for exactly this.
                (_state.value.route as? RouteState.WaitingForFix)?.let { planRoute(it.destination) }
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

    private fun refreshInBackground(current: SiteData, announce: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val startedAt = TimeSource.Monotonic.markNow()
            _state.value = _state.value.copy(refreshing = true, refreshError = null, refreshNote = null)
            var note: String? = null
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
                    // The route stays in use, measured again against the sites
                    // that are actually in the list now.
                    routeIndex?.let { remeasure(it.route) }
                    note = "הרשימה עודכנה — ${newer.count} אתרים"
                } else {
                    note = "הרשימה מעודכנת"
                }
                _state.value = _state.value.copy(refreshError = null)
            } catch (e: Exception) {
                // The bundled list is already on screen and correct; a failed
                // update is a footnote, not a failure of the app.
                Log.i(TAG, "dataset refresh skipped: ${e.message}")
                note = "העדכון לא הצליח — הרשימה שמורה במכשיר"
                _state.value = _state.value.copy(refreshError = e.message)
            }

            // The check usually answers in milliseconds. Ending there would look
            // exactly like a gesture the app ignored, so the spinner is held long
            // enough to read as an answer.
            val elapsed = startedAt.elapsedNow()
            if (announce && elapsed < MIN_SPINNER) delay(MIN_SPINNER - elapsed)
            _state.value = _state.value.copy(
                refreshing = false,
                refreshNote = if (announce) note else null,
            )
            if (announce) {
                delay(NOTE_LINGER)
                _state.value = _state.value.copy(refreshNote = null)
            }
        }
    }

    /** Filter + sort 6,000 sites off the main thread; the newest request wins. */
    private fun rerank() {
        val snapshot = _state.value
        val index = routeIndex?.takeIf { snapshot.route is RouteState.Ready }
        rankingJob?.cancel()
        rankingJob = viewModelScope.launch {
            val ranked = withContext(Dispatchers.Default) {
                val origin = snapshot.location.position
                if (index != null) {
                    rankAlongRoute(
                        sites = index.sites,
                        positions = index.positions,
                        origin = origin,
                        originOnRoute = origin?.let(index.line::locate),
                        categories = snapshot.selected,
                        query = snapshot.query,
                    )
                } else {
                    rankSites(
                        sites = all,
                        origin = origin,
                        categories = snapshot.selected,
                        query = snapshot.query,
                    )
                }
            }
            _state.value = _state.value.copy(ranked = ranked)
        }
    }

    // ── Ordering by a route ────────────────────────────────────────────────

    /**
     * A route planned before the app was closed is still the trip the user is on —
     * and quite likely somewhere with no signal to plan it again.
     */
    private fun restoreRoute() {
        routeJob = viewModelScope.launch {
            val saved = routes.loadSaved() ?: return@launch
            try {
                useRoute(saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "saved route could not be used — discarding it", e)
                routes.clear()
            }
        }
    }

    /**
     * Works out the road from where the phone is now to [destination], then orders
     * the list by it. With no position yet it waits for the first fix rather than
     * starting the road from a guess.
     */
    private fun planRoute(destination: Destination) {
        routeJob?.cancel()
        routeIndex = null
        val from = _state.value.location.position
        _state.value = _state.value.copy(
            route = if (from == null) RouteState.WaitingForFix(destination) else RouteState.Planning(destination),
        )
        rerank()
        routeJob = viewModelScope.launch {
            // Whatever route is on file belongs to the trip just replaced.
            routes.clear()
            if (from == null) return@launch
            try {
                val road = routes.route(from, destination)
                val planned = PlannedRoute(
                    destination = destination,
                    distanceM = road.distanceM,
                    durationSec = road.durationSec,
                    path = road.path,
                    plannedAt = System.currentTimeMillis(),
                )
                useRoute(planned)
                routes.save(planned)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "route to ${destination.name} failed", e)
                routeIndex = null
                _state.value = _state.value.copy(route = RouteState.Failed(destination, reasonFor(e)))
                rerank()
            }
        }
    }

    /** The same route against a new copy of the dataset — cancelled like any other route work. */
    private fun remeasure(route: PlannedRoute) {
        routeJob?.cancel()
        routeJob = viewModelScope.launch { useRoute(route) }
    }

    /** Measures every site against the road — once per route — then orders the list by it. */
    private suspend fun useRoute(route: PlannedRoute) {
        val sites = all
        val startedAt = TimeSource.Monotonic.markNow()
        val index = withContext(Dispatchers.Default) {
            val line = RouteLine(route.path)
            RouteIndex(route, line, sites, sites.associate { it.id to line.locate(LatLon(it.lat, it.lon)) })
        }
        Log.i(
            TAG,
            "measured ${sites.size} sites against ${route.path.size / 2} route points " +
                "to ${route.destination.name} in ${startedAt.elapsedNow().inWholeMilliseconds} ms",
        )
        routeIndex = index
        _state.value = _state.value.copy(route = RouteState.Ready(route))
        rerank()
    }

    /** What went wrong, in the words the header and the sheet show. */
    private fun reasonFor(e: Exception): String = when (e) {
        is BackendUnreachable -> "אין חיבור לשרת"
        is BackendException -> when (e.status) {
            404 -> "לא נמצאה דרך נסיעה לשם"
            422 -> "היעד הוא המקום שבו אתה נמצא"
            else -> "השרת החזיר שגיאה (${e.status})"
        }
        else -> e.message ?: e.toString()
    }

    override fun onOpenDestinationPicker() {
        ScreenTracker.currentScreen = "בחירת יעד נסיעה"
        _state.value = _state.value.copy(picker = DestinationPickerState())
    }

    override fun onCloseDestinationPicker() {
        placesJob?.cancel()
        ScreenTracker.currentScreen = "רשימת אתרים"
        _state.value = _state.value.copy(picker = null)
    }

    override fun onDestinationQueryChange(query: String) {
        val picker = _state.value.picker ?: return
        placesJob?.cancel()
        if (query.trim().length < MIN_PLACE_QUERY) {
            _state.value = _state.value.copy(picker = DestinationPickerState(query = query))
            return
        }
        _state.value = _state.value.copy(
            picker = picker.copy(query = query, places = emptyList(), searching = true, error = null),
        )
        placesJob = viewModelScope.launch {
            val sites = withContext(Dispatchers.Default) { sitesNamed(all, query) }
            updatePicker(query) { it.copy(sites = sites) }
            // Typing comes in bursts; the backend is asked once it pauses.
            delay(PLACE_SEARCH_PAUSE)
            try {
                val places = routes.searchPlaces(query, near = _state.value.location.position)
                updatePicker(query) { it.copy(places = places, searching = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.i(TAG, "place search for \"$query\" failed: ${e.message}")
                updatePicker(query) { it.copy(searching = false, error = reasonFor(e)) }
            }
        }
    }

    /** Applies a search result only to the query it was for, in a sheet that is still open. */
    private fun updatePicker(query: String, change: (DestinationPickerState) -> DestinationPickerState) {
        val picker = _state.value.picker ?: return
        if (picker.query != query) return
        _state.value = _state.value.copy(picker = change(picker))
    }

    override fun onChooseDestination(destination: Destination) {
        placesJob?.cancel()
        ScreenTracker.currentScreen = "רשימת אתרים"
        _state.value = _state.value.copy(picker = null, openSite = null)
        planRoute(destination)
    }

    override fun onRetryRoute() {
        (_state.value.route as? RouteState.Failed)?.let { planRoute(it.destination) }
    }

    override fun onClearRoute() {
        routeJob?.cancel()
        routeIndex = null
        _state.value = _state.value.copy(route = RouteState.None)
        rerank()
        viewModelScope.launch { routes.clear() }
    }

    // ── The list ───────────────────────────────────────────────────────────

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
        ScreenTracker.currentScreen = if (site == null) "רשימת אתרים" else "פרטי אתר"
        _state.value = _state.value.copy(openSite = site)
    }

    override fun onNavigateTo(site: Site) {
        _effects.tryEmit(Effect.Navigate(site))
    }

    override fun onOpenUrl(url: String) {
        _effects.tryEmit(Effect.OpenUrl(url))
    }

    /** Pull-to-refresh: the user asked, so the result is said out loud. */
    override fun onRefresh() {
        held?.let { refreshInBackground(it, announce = true) }
    }

    private companion object {
        const val TAG = "BrownSignsVM"
        val MIN_SPINNER = 600.milliseconds
        val NOTE_LINGER = 2_500.milliseconds
        const val MIN_PLACE_QUERY = 2
        val PLACE_SEARCH_PAUSE = 300.milliseconds
    }
}
