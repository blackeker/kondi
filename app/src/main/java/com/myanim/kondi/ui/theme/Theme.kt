package com.myanim.kondi.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Uygulama teması.
 *
 * [dynamicColor] varsayılan olarak kapalıdır: marka renkleri (indigo/camgöbeği)
 * her cihazda aynı görünsün. Kullanıcı isterse açabilir.
 */
@Composable
fun KondiTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme(
            primary = PrimaryLight,
            onPrimary = Color0B,
            primaryContainer = PrimaryDark,
            onPrimaryContainer = Color0B,
            secondary = Secondary,
            onSecondary = Color0B,
            secondaryContainer = SecondaryDark,
            onSecondaryContainer = Color0B,
            tertiary = Accent,
            background = DarkBackground,
            onBackground = DarkOnBackground,
            surface = DarkSurface,
            onSurface = DarkOnSurface,
            surfaceVariant = DarkSurfaceElevated,
            onSurfaceVariant = DarkTextSecondary,
            outline = DarkOutline,
            outlineVariant = DarkOutlineVariant,
            error = DarkError,
            onError = Color0B
        )

        else -> lightColorScheme(
            primary = Primary,
            onPrimary = OnPrimary,
            primaryContainer = PrimaryContainer,
            onPrimaryContainer = PrimaryDark,
            secondary = Secondary,
            onSecondary = OnSecondary,
            secondaryContainer = SecondaryContainer,
            onSecondaryContainer = SecondaryDark,
            tertiary = Accent,
            background = Background,
            onBackground = OnBackground,
            surface = Surface,
            onSurface = OnSurface,
            surfaceVariant = SurfaceElevated,
            onSurfaceVariant = TextSecondary,
            outline = Outline,
            outlineVariant = OutlineVariant,
            error = Error,
            onError = OnError
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
