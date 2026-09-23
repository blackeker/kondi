package com.myanim.kondi.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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
    dynamicColor: Boolean = true, // Default to true for Material You support
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val palette = animeTheme.getPalette()
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicDarkColorScheme(context)
        }
        else -> {
            darkColorScheme(
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
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}