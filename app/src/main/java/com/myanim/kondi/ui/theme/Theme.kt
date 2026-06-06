package com.myanim.kondi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = White,
    secondary = LightGrey,
    tertiary = MediumGrey,
    background = PureBlack,
    surface = DarkGrey,
    onPrimary = PureBlack,
    onSecondary = White,
    onTertiary = White,
    onBackground = TextHigh,
    onSurface = TextHigh,
    outline = BorderGrey,
    surfaceVariant = DarkGrey
)

@Composable
fun KondiTheme(
    animeTheme: AnimeTheme = AnimeTheme.DEFAULT,
    content: @Composable () -> Unit
) {
    val palette = animeTheme.getPalette()
    val colorScheme = darkColorScheme(
        primary = palette.primary,
        secondary = palette.secondary,
        tertiary = palette.accent,
        background = palette.background,
        surface = palette.surface,
        onPrimary = palette.onPrimary,
        onSecondary = palette.textHigh,
        onTertiary = White,
        onBackground = palette.textHigh,
        onSurface = palette.textHigh,
        outline = palette.secondary.copy(alpha = 0.3f),
        surfaceVariant = palette.surface
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}