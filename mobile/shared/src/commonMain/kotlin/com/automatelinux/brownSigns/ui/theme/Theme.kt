package com.automatelinux.brownSigns.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The palette is the sign: Israeli tourist signage is a brown field with white
 * lettering and a white inset border, so the app is built out of that one
 * material rather than decorated with it.
 */
object Sign {
    val Brown = Color(0xFF5E4128)
    val BrownDeep = Color(0xFF3B2817)
    val BrownLight = Color(0xFF7C5936)
    val BrownEdge = Color(0xFF8C6845)
    val White = Color(0xFFFFFFFF)
    val Cream = Color(0xFFF6F1E9)
    val CreamDim = Color(0xFFE7DECF)
    val Ink = Color(0xFF241A11)
    val InkDim = Color(0xFF6B5B4A)
    val NightBg = Color(0xFF17120E)
    val NightPanel = Color(0xFF241C15)
    val NightInk = Color(0xFFF0E7DA)
    val NightInkDim = Color(0xFF9E8E7C)
    /** Reserved for the live-distance figures, so they read as measurement, not decoration. */
    val Amber = Color(0xFFE0A33C)
}

/** Colours the sign metaphor needs that Material's scheme has no slot for. */
data class SignColors(
    val page: Color,
    val panel: Color,
    val ink: Color,
    val inkDim: Color,
    val signField: Color,
    val signInk: Color,
    val signEdge: Color,
    val measure: Color,
    val hairline: Color,
    val isNight: Boolean,
)

private val DayColors = SignColors(
    page = Sign.Cream,
    panel = Color.White,
    ink = Sign.Ink,
    inkDim = Sign.InkDim,
    signField = Sign.Brown,
    signInk = Sign.White,
    signEdge = Sign.BrownEdge,
    measure = Sign.BrownDeep,
    hairline = Sign.CreamDim,
    isNight = false,
)

private val NightColors = SignColors(
    page = Sign.NightBg,
    panel = Sign.NightPanel,
    ink = Sign.NightInk,
    inkDim = Sign.NightInkDim,
    signField = Sign.BrownDeep,
    signInk = Sign.White,
    signEdge = Sign.BrownLight,
    measure = Sign.Amber,
    hairline = Color(0xFF352A20),
    isNight = true,
)

val LocalSignColors = staticCompositionLocalOf { DayColors }

private val LightScheme = lightColorScheme(
    primary = Sign.Brown,
    onPrimary = Sign.White,
    primaryContainer = Sign.BrownLight,
    onPrimaryContainer = Sign.White,
    secondary = Sign.BrownLight,
    background = Sign.Cream,
    onBackground = Sign.Ink,
    surface = Color.White,
    onSurface = Sign.Ink,
    surfaceVariant = Sign.CreamDim,
    onSurfaceVariant = Sign.InkDim,
    outline = Sign.CreamDim,
)

private val DarkScheme = darkColorScheme(
    primary = Sign.BrownLight,
    onPrimary = Sign.White,
    primaryContainer = Sign.BrownDeep,
    onPrimaryContainer = Sign.White,
    secondary = Sign.BrownEdge,
    background = Sign.NightBg,
    onBackground = Sign.NightInk,
    surface = Sign.NightPanel,
    onSurface = Sign.NightInk,
    surfaceVariant = Color(0xFF352A20),
    onSurfaceVariant = Sign.NightInkDim,
    outline = Color(0xFF352A20),
)

// Road signage is set tight and heavy so it reads at speed; the list borrows that.
private val SignTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Bold),
        bodyMedium = bodyMedium.copy(lineHeight = 21.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Bold),
    )
}

/** The tabular-ish figure style the distance column uses. */
val MeasureTextStyle = TextStyle(
    fontWeight = FontWeight.Black,
    fontSize = 19.sp,
    letterSpacing = (-0.6).sp,
)

@Composable
fun AppTheme(night: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSignColors provides if (night) NightColors else DayColors) {
        MaterialTheme(
            colorScheme = if (night) DarkScheme else LightScheme,
            typography = SignTypography,
            content = content,
        )
    }
}
