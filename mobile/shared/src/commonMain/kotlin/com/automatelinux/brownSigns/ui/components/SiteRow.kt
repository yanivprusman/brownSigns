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
import com.automatelinux.brownSigns.geo.compassPoint
import com.automatelinux.brownSigns.geo.formatDistanceParts
import com.automatelinux.brownSigns.ui.theme.LocalSignColors
import com.automatelinux.brownSigns.ui.theme.MeasureTextStyle

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
            val metres = ranked.metres
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

/** The hairline between rows — structure, not decoration: it starts where the text does. */
@Composable
fun RowHairline(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = 76.dp),
        thickness = 1.dp,
        color = LocalSignColors.current.hairline,
    )
}
