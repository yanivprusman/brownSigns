package com.automatelinux.brownSigns.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.brownSigns.geo.formatDistance
import com.automatelinux.brownSigns.geo.groupDigits
import com.automatelinux.brownSigns.ui.components.CategoryChips
import com.automatelinux.brownSigns.ui.components.RowHairline
import com.automatelinux.brownSigns.ui.components.SignpostIcon
import com.automatelinux.brownSigns.ui.components.SiteRow
import com.automatelinux.brownSigns.ui.theme.LocalSignColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The whole app: every brown-signed destination in the country, nearest first —
 * or, with a destination set, nearest to the road there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrownSignsScreen(state: BrownSignsUiState, actions: BrownSignsActions) {
    val colors = LocalSignColors.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // A new query, filter or ordering is a new question — answer it from the top.
    val orderedByRoute = (state.route as? RouteState.Ready)?.route?.plannedAt
    LaunchedEffect(state.query, state.selected, orderedByRoute) {
        listState.scrollToItem(0)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.page),
    ) {
        SignHeader(state, actions)

        Column(
            Modifier
                .background(colors.panel)
                .padding(bottom = 12.dp),
        ) {
            SearchField(
                query = state.query,
                onQueryChange = actions::onQueryChange,
                hint = "חפש יעד — מצדה, קיסריה, מוזיאון…",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            CategoryChips(
                counts = state.counts,
                selected = state.selected,
                total = state.counts.values.sum(),
                onToggle = actions::onToggleCategory,
                onClear = actions::onClearFilters,
                modifier = Modifier.fillMaxWidth(),
            )
            RefreshNote(state.refreshNote)
        }

        when {
            state.fatal != null -> FatalState(state.fatal)
            state.loading -> LoadingState()
            state.ranked.isEmpty() -> EmptyState(state, actions)
            else -> Box(Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = actions::onRefresh,
                    modifier = Modifier.fillMaxSize().background(colors.page),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // Clearance for the back-to-top control, so the last
                        // row is never parked underneath it.
                        contentPadding = PaddingValues(bottom = 88.dp),
                    ) {
                        items(state.ranked, key = { it.site.id }) { ranked ->
                            SiteRow(
                                ranked = ranked,
                                heading = state.heading,
                                onClick = { actions.onOpenSite(ranked.site) },
                            )
                            RowHairline()
                        }
                        item { DatasetFooter(state) }
                    }
                }

                // Six thousand rows deep, the way back to the nearest one should
                // not be a long swipe. Sits opposite the feedback bubble.
                BackToTop(
                    visible = listState.firstVisibleItemIndex > 8,
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                )
            }
        }
    }

    state.openSite?.let { site ->
        SiteDetailSheet(
            site = site,
            ranked = state.ranked.firstOrNull { it.site.id == site.id },
            heading = state.heading,
            isDestination = state.route.destination?.siteId == site.id,
            actions = actions,
            onDismiss = { actions.onOpenSite(null) },
        )
    }

    state.picker?.let { picker ->
        DestinationSheet(
            picker = picker,
            hasFix = state.location.position != null,
            actions = actions,
        )
    }
}

/** What the last refresh found. Says so for a moment, then gets out of the way. */
@Composable
private fun RefreshNote(note: String?) {
    val colors = LocalSignColors.current
    AnimatedVisibility(visible = note != null, enter = fadeIn(), exit = fadeOut()) {
        Text(
            text = note.orEmpty(),
            fontSize = 12.sp,
            color = colors.inkDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
}

/** Appears once the list has been scrolled past the places that are actually near. */
@Composable
private fun BackToTop(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSignColors.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .background(colors.signField, CircleShape)
                .border(1.5.dp, colors.signInk.copy(alpha = 0.85f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "חזרה לראש הרשימה",
                tint = colors.signInk,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** The sign at the top of the screen: brown field, white border, white lettering. */
@Composable
private fun SignHeader(state: BrownSignsUiState, actions: BrownSignsActions) {
    val colors = LocalSignColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.signField)
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(2.dp, colors.signInk.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = SignpostIcon,
                    contentDescription = null,
                    tint = colors.signInk,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "שלט חום",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.signInk,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${groupDigits(state.total)} יעדים",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.signInk.copy(alpha = 0.7f),
                )
            }
            OrderingLines(state, actions)
        }
    }
}

/**
 * What the list is ordered by, and the way to change it. Without a destination:
 * nearest to you, with the way in to setting one. With a route: nearest to the
 * road, and the way back out. While a route is still being worked out the list
 * is still nearest-first, so both lines show.
 */
@Composable
private fun OrderingLines(state: BrownSignsUiState, actions: BrownSignsActions) {
    when (val route = state.route) {
        RouteState.None -> Row(verticalAlignment = Alignment.CenterVertically) {
            LocationLine(state, actions, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            DestinationChip(onClick = actions::onOpenDestinationPicker)
        }
        is RouteState.Ready -> RouteBanner(route, actions)
        else -> {
            LocationLine(state, actions)
            RouteStatus(route, actions)
        }
    }
}

/**
 * What the ordering is actually based on. It says so out loud, because a list
 * sorted alphabetically and a list sorted by distance look identical.
 */
@Composable
private fun LocationLine(state: BrownSignsUiState, actions: BrownSignsActions, modifier: Modifier = Modifier) {
    val colors = LocalSignColors.current
    val (icon, text, action) = when (val loc = state.location) {
        LocationState.Pending -> Triple(
            Icons.Filled.MyLocation,
            "הפעל מיקום כדי לסדר לפי הקרוב אליך",
            actions::onRequestLocation,
        )
        LocationState.Denied -> Triple(
            Icons.Filled.LocationOff,
            "אין הרשאת מיקום — הרשימה לפי א״ב",
            actions::onRequestLocation,
        )
        LocationState.Searching -> Triple(
            Icons.Filled.MyLocation,
            "מחפש מיקום…",
            null,
        )
        is LocationState.Fixed -> Triple(
            Icons.Filled.MyLocation,
            loc.accuracyMetres?.let { "לפי הקרוב אליך · דיוק ${it.toInt()} מ׳" } ?: "לפי הקרוב אליך",
            null,
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (action != null) modifier.clickable(onClick = action) else modifier,
    ) {
        Icon(icon, contentDescription = null, tint = colors.signInk.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = colors.signInk.copy(alpha = 0.85f),
        )
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            SignPill("אפשר")
        }
    }
}

/** The way in to ordering by a route — on the sign, beside what the list is ordered by now. */
@Composable
private fun DestinationChip(onClick: () -> Unit) {
    val colors = LocalSignColors.current
    val shape = RoundedCornerShape(6.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, colors.signInk.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Icon(Icons.Filled.Route, contentDescription = null, tint = colors.signInk, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text("יעד נסיעה", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.signInk)
    }
}

/** The list is ordered by the road to a destination: where to, how far, and the way back out. */
@Composable
private fun RouteBanner(route: RouteState.Ready, actions: BrownSignsActions) {
    val colors = LocalSignColors.current
    val planned = route.route
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Tapping the destination changes it.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).clickable(onClick = actions::onOpenDestinationPicker),
        ) {
            Icon(
                Icons.Filled.Route,
                contentDescription = null,
                tint = colors.signInk.copy(alpha = 0.85f),
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
            Column {
                Text(
                    text = "לפי הקרבה לדרך אל ${planned.destination.name}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.signInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatDistance(planned.distanceM)} · ${formatDuration(planned.durationSec)} נסיעה",
                    fontSize = 12.sp,
                    color = colors.signInk.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }
        ClearRouteButton(actions::onClearRoute)
    }
}

/** A destination is set but the list is not ordered by it yet — and why. */
@Composable
private fun RouteStatus(route: RouteState, actions: BrownSignsActions) {
    val colors = LocalSignColors.current
    val name = route.destination?.name ?: return
    val text = when (route) {
        is RouteState.WaitingForFix -> "ממתין למיקום כדי לחשב דרך אל $name"
        is RouteState.Planning -> "מחשב דרך אל $name…"
        is RouteState.Failed -> "הדרך אל $name לא חושבה — ${route.reason}"
        else -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (route is RouteState.Planning) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                color = colors.signInk,
                strokeWidth = 1.5.dp,
            )
        } else {
            Icon(
                Icons.Filled.Route,
                contentDescription = null,
                tint = colors.signInk.copy(alpha = 0.8f),
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = colors.signInk.copy(alpha = 0.85f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (route is RouteState.Failed) {
            Spacer(Modifier.width(8.dp))
            SignPill("נסה שוב", onClick = actions::onRetryRoute)
        }
        ClearRouteButton(actions::onClearRoute)
    }
}

@Composable
private fun ClearRouteButton(onClick: () -> Unit) {
    val colors = LocalSignColors.current
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = "בטל את יעד הנסיעה",
            tint = colors.signInk,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A small bordered word on the sign. Clickable on its own only when given [onClick]. */
@Composable
private fun SignPill(text: String, onClick: (() -> Unit)? = null) {
    val colors = LocalSignColors.current
    val shape = RoundedCornerShape(5.dp)
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = colors.signInk,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, colors.signInk.copy(alpha = 0.6f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** "35 דק׳", "4:10 שע׳". */
private fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt()
    if (minutes < 60) return "$minutes דק׳"
    return "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')} שע׳"
}

@Composable
internal fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSignColors.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text(hint, color = colors.inkDim, fontSize = 15.sp) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.inkDim) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "נקה חיפוש",
                    tint = colors.inkDim,
                    modifier = Modifier.clickable { onQueryChange("") },
                )
            }
        },
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.signField,
            unfocusedBorderColor = colors.hairline,
            focusedTextColor = colors.ink,
            unfocusedTextColor = colors.ink,
            cursorColor = colors.signField,
            focusedContainerColor = colors.panel,
            unfocusedContainerColor = colors.panel,
        ),
    )
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalSignColors.current.signField)
    }
}

@Composable
private fun FatalState(message: String) {
    val colors = LocalSignColors.current
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("הרשימה לא נטענה", style = MaterialTheme.typography.titleLarge, color = colors.ink)
            Text(message, fontSize = 14.sp, color = colors.inkDim, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun EmptyState(state: BrownSignsUiState, actions: BrownSignsActions) {
    val colors = LocalSignColors.current
    val what = when {
        state.query.isNotEmpty() && state.selected.isNotEmpty() ->
            "אין ${state.selected.joinToString(" או ") { it.plural }} בשם \"${state.query}\""
        state.query.isNotEmpty() -> "לא נמצא יעד בשם \"${state.query}\""
        else -> "אין יעדים בסינון הזה"
    }
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(what, style = MaterialTheme.typography.titleMedium, color = colors.ink, textAlign = TextAlign.Center)
            if (state.selected.isNotEmpty()) {
                Text(
                    text = "בטל את הסינון",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.signInk,
                    modifier = Modifier
                        .background(colors.signField, RoundedCornerShape(8.dp))
                        .clickable(onClick = actions::onClearFilters)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Where the list came from, and when — at the bottom, where a source line belongs. */
@Composable
private fun DatasetFooter(state: BrownSignsUiState) {
    val colors = LocalSignColors.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "${groupDigits(state.total)} יעדים · מיפוי OpenStreetMap",
            fontSize = 12.sp,
            color = colors.inkDim,
            textAlign = TextAlign.Center,
        )
        state.refreshError?.let {
            Text(
                text = "העדכון האחרון נכשל: $it",
                fontSize = 12.sp,
                color = colors.inkDim,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}
