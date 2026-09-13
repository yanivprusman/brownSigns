package com.automatelinux.brownSigns.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.brownSigns.data.model.Destination
import com.automatelinux.brownSigns.data.model.asDestination
import com.automatelinux.brownSigns.ui.components.SignPlate
import com.automatelinux.brownSigns.ui.theme.LocalSignColors

/**
 * "Where are you driving to?" — the question that turns the list from what is
 * near you into what is along your way.
 *
 * Sites from the list come first and need no connection: they are the names the
 * user reads on the signs, and a site is the destination this exists for. Towns
 * and addresses from the backend's place search come underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationSheet(
    picker: DestinationPickerState,
    hasFix: Boolean,
    actions: BrownSignsActions,
) {
    val colors = LocalSignColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = actions::onCloseDestinationPicker,
        sheetState = sheetState,
        containerColor = colors.panel,
    ) {
        // A fixed height, so the sheet does not jump as results come and go.
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding(),
        ) {
            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("לאן נוסעים?", style = MaterialTheme.typography.titleLarge, color = colors.ink)
                Text(
                    text = if (hasFix) "הרשימה תסודר לפי הקרבה לדרך לשם"
                    else "עוד אין מיקום — הדרך תחושב ברגע שיימצא",
                    fontSize = 13.sp,
                    color = colors.inkDim,
                )
            }

            SearchField(
                query = picker.query,
                onQueryChange = actions::onDestinationQueryChange,
                hint = "יישוב, כתובת, או אתר מהרשימה",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .focusRequester(focus),
            )

            val placeIconTint = if (colors.isNight) colors.measure else colors.signField
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (picker.query.trim().length < 2) {
                    item { Note("הקלד לפחות שתי אותיות") }
                } else {
                    if (picker.sites.isNotEmpty()) {
                        item { SectionLabel("אתרים מהרשימה") }
                        items(picker.sites, key = { "site:${it.id}" }) { site ->
                            ResultRow(
                                title = site.he,
                                detail = site.cat.he,
                                onClick = { actions.onChooseDestination(site.asDestination()) },
                            ) { SignPlate(site.cat, size = 34.dp) }
                        }
                    }
                    item { SectionLabel("מקומות") }
                    when {
                        picker.places.isNotEmpty() -> items(picker.places) { place ->
                            ResultRow(
                                title = place.name,
                                detail = place.area,
                                onClick = {
                                    actions.onChooseDestination(
                                        Destination(name = place.name, detail = place.area, lat = place.lat, lon = place.lon),
                                    )
                                },
                            ) {
                                Icon(
                                    Icons.Filled.Place,
                                    contentDescription = null,
                                    tint = placeIconTint,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                        picker.searching -> item { Searching() }
                        picker.error != null -> item { Note("חיפוש המקומות לא הצליח — ${picker.error}") }
                        else -> item { Note("לא נמצא מקום בשם \"${picker.query.trim()}\"") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = LocalSignColors.current.inkDim,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = LocalSignColors.current.inkDim,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun Searching() {
    val colors = LocalSignColors.current
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), color = colors.signField, strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text("מחפש…", fontSize = 14.sp, color = colors.inkDim)
    }
}

@Composable
private fun ResultRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    val colors = LocalSignColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 58.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
