package com.automatelinux.brownSigns.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.brownSigns.data.model.Category
import com.automatelinux.brownSigns.ui.theme.LocalSignColors

/**
 * The seven kinds of destination a brown sign points at, as filters. Each chip
 * carries its own pictogram, so the legend and the list teach each other.
 */
@Composable
fun CategoryChips(
    counts: Map<Category, Int>,
    selected: Set<Category>,
    total: Int,
    onToggle: (Category) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Chip(
                label = "הכול",
                count = total,
                selected = selected.isEmpty(),
                onClick = onClear,
            )
        }
        items(Category.entries.toList(), key = { it.name }) { cat ->
            Chip(
                label = cat.plural,
                count = counts[cat] ?: 0,
                selected = cat in selected,
                category = cat,
                onClick = { onToggle(cat) },
            )
        }
    }
}

@Composable
private fun Chip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    category: Category? = null,
) {
    val colors = LocalSignColors.current
    val shape = RoundedCornerShape(9.dp)
    // An empty category is still shown — that it has nothing nearby is an answer.
    val enabled = count > 0 || selected

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(if (selected) colors.signField else colors.panel, shape)
            .border(
                width = 1.dp,
                color = if (selected) colors.signEdge else colors.hairline,
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (category != null) {
            Icon(
                imageVector = category.pictogram(),
                contentDescription = null,
                tint = when {
                    selected -> colors.signInk
                    enabled -> colors.signField
                    else -> colors.inkDim
                },
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                selected -> colors.signInk
                enabled -> colors.ink
                else -> colors.inkDim
            },
        )
        Text(
            text = "$count",
            fontSize = 12.sp,
            color = if (selected) colors.signInk.copy(alpha = 0.75f) else colors.inkDim,
        )
    }
}
