package com.myanim.kondi.ui.theme

import androidx.compose.ui.graphics.Color

enum class AnimeTheme(val displayName: String) {
    DEFAULT("Kondi Dark")
}

data class ThemePalette(
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val accent: Color,
    val onPrimary: Color = Color.White,
    val textHigh: Color = Color(0xFFEEEEEE)
)

val DefaultPalette = ThemePalette(
    primary = Color.White,
    secondary = Color(0xFF666666),
    background = Color(0xFF000000),
    surface = Color(0xFF111111),
    accent = Color(0xFF333333) // Medium Grey
)

fun AnimeTheme.getPalette(): ThemePalette = when(this) {
    AnimeTheme.DEFAULT -> DefaultPalette
}
