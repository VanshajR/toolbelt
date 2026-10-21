package com.utilityapp.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.utilityapp.settings.AppSettings
import com.utilityapp.settings.Palette
import com.utilityapp.settings.ThemeMode

/** Whether the app is currently drawn dark, for colours that aren't part of the Material scheme. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** The accent each palette uses: (light theme, dark theme). */
fun Palette.accent(): Pair<Color, Color> = when (this) {
    Palette.WALLPAPER, Palette.INDIGO -> Color(0xFF4A41A8) to Color(0xFFC4BFFF)
    Palette.TEAL -> Color(0xFF1F6F5C) to Color(0xFF8FD5BF)
    Palette.ROSE -> Color(0xFFA23B5B) to Color(0xFFFFB1C5)
    Palette.AMBER -> Color(0xFF8A5A00) to Color(0xFFFFC46B)
    Palette.GRAPHITE -> Color(0xFF17171C) to Color(0xFFE4E4E8)
}

@Composable
fun ToolbeltTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = if (settings.palette == Palette.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val (light, night) = settings.palette.accent()
        if (dark) darkScheme(night) else lightScheme(light)
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

// Neutral, slightly warm surfaces; only the accent changes between palettes.

private fun lightScheme(accent: Color): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = lerp(accent, Color.White, 0.85f),
    onPrimaryContainer = lerp(accent, Color.Black, 0.3f),
    secondaryContainer = lerp(accent, Color.White, 0.88f),
    onSecondaryContainer = lerp(accent, Color.Black, 0.3f),
    background = Color(0xFFF4F4F1),
    onBackground = Color(0xFF17171C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17171C),
    surfaceVariant = Color(0xFFECECE7),
    onSurfaceVariant = Color(0xFF5C5D66),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE2E2DC),
    outline = Color(0xFFD6D6CF),
    outlineVariant = Color(0xFFECECE7),
    error = Color(0xFFB3261E),
)

private fun darkScheme(accent: Color): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF17171C),
    primaryContainer = lerp(accent, Color.Black, 0.65f),
    onPrimaryContainer = accent,
    secondaryContainer = lerp(accent, Color.Black, 0.7f),
    onSecondaryContainer = accent,
    background = Color(0xFF111114),
    onBackground = Color(0xFFEDEDF0),
    surface = Color(0xFF1C1C21),
    onSurface = Color(0xFFEDEDF0),
    surfaceVariant = Color(0xFF2A2A30),
    onSurfaceVariant = Color(0xFFB4B4BD),
    surfaceContainerLowest = Color(0xFF111114),
    surfaceContainerLow = Color(0xFF1C1C21),
    surfaceContainer = Color(0xFF1C1C21),
    surfaceContainerHigh = Color(0xFF232329),
    surfaceContainerHighest = Color(0xFF3A3A42),
    outline = Color(0xFF3A3A42),
    outlineVariant = Color(0xFF2A2A30),
    error = Color(0xFFFFB4AB),
)
