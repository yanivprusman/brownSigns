package com.automatelinux.brownSigns.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The arrow off a road sign, pointing at the real place.
 *
 * `degrees` is measured clockwise from straight up. Callers pass
 * `bearing - deviceHeading`, so the arrow points where the destination actually
 * is as the phone turns — the one thing a list of coordinates cannot say.
 */
@Composable
fun DirectionArrow(
    degrees: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
) {
    // The shortest-path rotation: without it a swing across 360° spins the arrow
    // the long way round the dial.
    val animated by animateFloatAsState(
        targetValue = degrees,
        label = "arrowRotation",
    )
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        rotate(animated, pivot = center) {
            val shaftHalf = w * 0.11f
            val headHalf = w * 0.30f
            val headBase = h * 0.42f
            val path = Path().apply {
                moveTo(w / 2, h * 0.06f)              // tip
                lineTo(w / 2 + headHalf, headBase)
                lineTo(w / 2 + shaftHalf, headBase)
                lineTo(w / 2 + shaftHalf, h * 0.94f)  // shaft
                lineTo(w / 2 - shaftHalf, h * 0.94f)
                lineTo(w / 2 - shaftHalf, headBase)
                lineTo(w / 2 - headHalf, headBase)
                close()
            }
            drawPath(path, color)
        }
    }
}
