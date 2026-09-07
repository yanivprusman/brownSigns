package com.automatelinux.brownSigns.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Attractions
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.outlined.Signpost
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.automatelinux.brownSigns.data.model.Category
import com.automatelinux.brownSigns.ui.theme.LocalSignColors

/** The pictogram a brown sign would carry for each kind of destination. */
fun Category.pictogram(): ImageVector = when (this) {
    Category.NATIONAL_PARK -> Icons.Filled.Park
    Category.NATURE_RESERVE -> Icons.Filled.Forest
    Category.MUSEUM -> Icons.Filled.Museum
    Category.ARCHAEOLOGY -> Icons.Filled.AccountBalance
    Category.HERITAGE -> Icons.Filled.Castle
    Category.ATTRACTION -> Icons.Filled.Attractions
    Category.VIEWPOINT -> Icons.Filled.Landscape
}

val SignpostIcon: ImageVector get() = Icons.Outlined.Signpost

/**
 * The sign itself, at any size: brown field, white inset border, white pictogram.
 * Every place the brown appears in the app is one of these.
 */
@Composable
fun SignPlate(
    category: Category,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val colors = LocalSignColors.current
    val corner = size / 5
    Box(
        modifier = modifier
            .size(size)
            .background(colors.signField, RoundedCornerShape(corner))
            .padding(size / 14)
            .border(
                width = if (size > 60.dp) 2.dp else 1.5.dp,
                color = colors.signInk.copy(alpha = 0.9f),
                shape = RoundedCornerShape(corner - size / 14),
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = category.pictogram(),
            contentDescription = category.he,
            tint = colors.signInk,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
