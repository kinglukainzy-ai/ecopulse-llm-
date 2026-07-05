package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = EcoGreenPrimary,
    onPrimary = EcoWhite,
    primaryContainer = EcoGreenSurface,
    onPrimaryContainer = EcoGreenDark,
    secondary = EcoTeal,
    onSecondary = EcoWhite,
    tertiary = EcoOrange,
    onTertiary = EcoWhite,
    background = EcoGreenPale,
    onBackground = EcoTextPrimary,
    surface = EcoWhite,
    onSurface = EcoTextPrimary,
    surfaceVariant = EcoGreenSurface,
    onSurfaceVariant = EcoTextSecondary,
    error = EcoRed,
    errorContainer = EcoRedSurface,
    onErrorContainer = EcoRed,
    outline = EcoDivider,
)

private val DarkColorScheme = darkColorScheme(
    primary = EcoGreenLight,
    onPrimary = EcoNavyDarker,
    primaryContainer = EcoGreenPrimary,
    onPrimaryContainer = EcoWhite,
    secondary = EcoTeal,
    onSecondary = EcoNavyDarker,
    tertiary = EcoOrange,
    background = EcoNavyDarker,
    onBackground = EcoTextOnDark,
    surface = EcoNavyDark,
    onSurface = EcoTextOnDark,
    surfaceVariant = EcoNavyDark,
    onSurfaceVariant = EcoTextOnDarkMuted,
    error = EcoRed,
)

/**
 * EcoPulseTheme — replaces the old placeholder Purple/Pink Material template
 * theme with the real EcoPulse brand palette from the design system mockup.
 *
 * dynamicColor defaults to false on purpose: EcoPulse's green/navy identity
 * is a deliberate brand choice, not something that should shift based on the
 * user's wallpaper (which is what dynamicColor=true would do on Android 12+).
 */
@Composable
fun EcoPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
