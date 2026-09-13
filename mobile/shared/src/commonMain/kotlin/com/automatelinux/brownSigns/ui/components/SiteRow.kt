package com.automatelinux.brownSigns.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.brownSigns.data.RankedSite
import com.automatelinux.brownSigns.data.RouteSpot
import com.automatelinux.brownSigns.geo.compassPoint
import com.automatelinux.brownSigns.geo.formatDistance
import com.automatelinux.brownSigns.geo.formatDistanceParts
import com.automatelinux.brownSigns.ui.theme.LocalSignColors
import com.automatelinux.brownSigns.ui.theme.MeasureTextStyle
import kotlin.math.roundToInt

/**
 * One destination. Reads the way a sign does: pictogram, where it is, how far —
 * and, when the phone has a compass, an arrow that keeps pointing at it.
 */
@Composable
fun SiteRow(
    ranked: RankedSite,
    heading: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSignColors.current
    val site = ranked.site

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 76.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignPlate(site.cat, size = 46.dp)

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = site.he,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(site.cat.he)
                    site.en?.let { append(" · ").append(it) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(10.dp))

        // The measurement column. Fixed width so the figures line up down the
        // list the way they would on a gantry.
        Column(
            modifier = Modifier.width(84.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val spot = ranked.onRoute
            // Ordered by a route, the figure is how far the site is from the road;
            // otherwise, how far it is from the phone.
            val metres = spot?.offMetres ?: ranked.metres
            if (metres == null) {
                Text(
                    text = "—",
                    style = MeasureTextStyle,
                    color = colors.inkDim,
                )
            } else {
                val (figure, unit) = formatDistanceParts(metres)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = figure,
                        style = MeasureTextStyle,
                        color = colors.measure,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = unit,
                        fontSize = 11.sp,
                        color = colors.measure,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                if (spot != null) {
                    Text(
                        text = routeProgress(spot),
                        fontSize = 11.sp,
                        color = colors.inkDim,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    val bearing = ranked.bearing
                    if (bearing != null) {
                        if (heading != null) {
                            DirectionArrow(
                                degrees = (bearing.toFloat() - heading),
                                color = colors.signField.let { if (colors.isNight) colors.measure else it },
                                size = 24.dp,
                            )
                        } else {
                            // No compass: name the direction instead of drawing an
                            // arrow that would be pointing at nothing.
                            Text(
                                text = compassPoint(bearing),
                                fontSize = 11.sp,
                                color = colors.inkDim,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Where along the road a site comes up: "בעוד 12 ק״מ", "לידך", "מאחוריך" — or,
 * when the phone is not on the route, the kilometre of the route it is at.
 */
fun routeProgress(spot: RouteSpot): String {
    val ahead = spot.aheadMetres ?: return "בק״מ ${(spot.alongMetres / 1_000).roundToInt()}"
    return when {
        ahead < -ALONGSIDE_M -> "מאחוריך"
        ahead <= ALONGSIDE_M -> "לידך"
        else -> "בעוד ${formatDistance(ahead)}"
    }
}

/** Within this far along the road, a site is beside the phone rather than ahead of it or behind. */
private const val ALONGSIDE_M = 300.0

/** The hairline between rows — structure, not decoration: it starts where the text does. */
@Composable
fun RowHairline(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = 76.dp),
        thickness = 1.dp,
        color = LocalSignColors.current.hairline,
    )
}
