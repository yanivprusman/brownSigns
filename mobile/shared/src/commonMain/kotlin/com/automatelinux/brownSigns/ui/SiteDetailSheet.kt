package com.automatelinux.brownSigns.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.brownSigns.data.RankedSite
import com.automatelinux.brownSigns.data.model.Site
import com.automatelinux.brownSigns.geo.compassPoint
import com.automatelinux.brownSigns.geo.formatDistance
import com.automatelinux.brownSigns.ui.components.DirectionArrow
import com.automatelinux.brownSigns.ui.components.SignPlate
import com.automatelinux.brownSigns.ui.theme.LocalSignColors
import com.automatelinux.brownSigns.ui.theme.MeasureTextStyle

/** One destination, opened: what it is, how far, and how to get there. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SiteDetailSheet(
    site: Site,
    ranked: RankedSite?,
    heading: Float?,
    actions: BrownSignsActions,
    onDismiss: () -> Unit,
) {
    val colors = LocalSignColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.panel,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            // The sign, full size — the same object the row showed in miniature.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.signField)
                    .padding(16.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(2.dp, colors.signInk.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        SignPlate(site.cat, size = 54.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = site.he,
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.signInk,
                            )
                            Text(
                                text = site.cat.he,
                                fontSize = 13.sp,
                                color = colors.signInk.copy(alpha = 0.75f),
                            )
                            // The other languages a real sign carries, in the
                            // order it carries them.
                            site.en?.let {
                                Text(it, fontSize = 14.sp, color = colors.signInk.copy(alpha = 0.8f))
                            }
                            site.ar?.let {
                                Text(it, fontSize = 14.sp, color = colors.signInk.copy(alpha = 0.65f))
                            }
                        }
                    }
                }
            }

            // Measurements
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                ranked?.metres?.let { metres ->
                    Measure("מרחק אווירי", formatDistance(metres))
                }
                ranked?.bearing?.let { bearing ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("כיוון", fontSize = 12.sp, color = colors.inkDim)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (heading != null) {
                                DirectionArrow(
                                    degrees = bearing.toFloat() - heading,
                                    color = colors.measure,
                                    size = 22.dp,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(compassPoint(bearing), style = MeasureTextStyle, color = colors.measure)
                        }
                    }
                }
                site.ele?.let { Measure("גובה", "$it מ׳") }
            }

            site.desc?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                )
            }

            val facts = buildList {
                site.op?.let { add("מפעיל" to it) }
                site.oh?.let { add("שעות" to it) }
                site.fee?.let { add("כניסה" to if (it) "בתשלום" else "חופשית") }
            }
            if (facts.isNotEmpty()) {
                Column(
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    facts.forEach { (label, value) ->
                        Row {
                            Text("$label: ", fontSize = 13.sp, color = colors.inkDim)
                            Text(value, fontSize = 13.sp, color = colors.ink, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Actions
            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionButton(
                    icon = Icons.Filled.Navigation,
                    label = "נווט לשם",
                    primary = true,
                    onClick = { actions.onNavigateTo(site) },
                )
                site.wikipediaUrl?.let { url ->
                    ActionButton(Icons.Filled.MenuBook, "ויקיפדיה", false) { actions.onOpenUrl(url) }
                }
                site.url?.let { url ->
                    ActionButton(Icons.Filled.Language, "אתר האתר", false) { actions.onOpenUrl(url) }
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun Measure(label: String, value: String) {
    val colors = LocalSignColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = colors.inkDim)
        Text(value, style = MeasureTextStyle, color = colors.measure)
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, primary: Boolean, onClick: () -> Unit) {
    val colors = LocalSignColors.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(if (primary) colors.signField else colors.panel, shape)
            .border(1.dp, if (primary) colors.signEdge else colors.hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (primary) colors.signInk else colors.signField,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) colors.signInk else colors.ink,
        )
    }
}
