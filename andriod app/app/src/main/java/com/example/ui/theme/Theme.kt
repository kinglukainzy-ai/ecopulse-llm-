package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Custom palette: Forest/Nature
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),      // Soft Forest Green
    secondary = Color(0xFF4DB6AC),    // Soft Teal
    tertiary = Color(0xFFFFB74D),     // Earthy Amber
    background = Color(0xFF111E16),   // Deep Forest Dark
    surface = Color(0xFF1B2E23),      // Dark Slate Green
    onPrimary = Color(0xFF00330C),
    onSecondary = Color(0xFF003732),
    onBackground = Color(0xFFE8F5E9),
    onSurface = Color(0xFFE8F5E9)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),     // Forest Green
    secondary = Color(0xFF00796B),   // Dark Teal
    tertiary = Color(0xFFE65100),    // Rich Burnt Orange
    background = Color(0xFFF1F8F5),  // Pale Sage
    surface = Color(0xFFFFFFFF),     // White Surface
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1B2E23),
    onSurface = Color(0xFF1B2E23)
)

@Composable
fun EcoPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Set to false to force our custom thematic palette for brand recognition!
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
        content = content
    )
}

