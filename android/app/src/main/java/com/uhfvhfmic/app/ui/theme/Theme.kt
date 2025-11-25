package com.uhfvhfmic.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * UHF/VHF Mic App Theme
 * © 2024 Iwinosa Aigbokhaevbo. All Rights Reserved.
 */

// Radio-themed colors
private val RadioRed = Color(0xFFE53935)
private val RadioRedDark = Color(0xFFB71C1C)
private val RadioOrange = Color(0xFFFF9800)
private val RadioGreen = Color(0xFF4CAF50)
private val DarkBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)
private val DarkCard = Color(0xFF2D2D2D)

private val DarkColorScheme = darkColorScheme(
    primary = RadioRed,
    onPrimary = Color.White,
    primaryContainer = RadioRedDark,
    onPrimaryContainer = Color.White,
    secondary = RadioOrange,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF5D4037),
    onSecondaryContainer = Color.White,
    tertiary = RadioGreen,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkCard,
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = Color(0xFFCF6679),
    onError = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = RadioRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFCDD2),
    onPrimaryContainer = RadioRedDark,
    secondary = RadioOrange,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFFFE0B2),
    onSecondaryContainer = Color(0xFFE65100),
    tertiary = RadioGreen,
    onTertiary = Color.White,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1C1C1C),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF666666),
    error = Color(0xFFB00020),
    onError = Color.White
)

@Composable
fun UHFVHFMicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled for consistent radio theme
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
