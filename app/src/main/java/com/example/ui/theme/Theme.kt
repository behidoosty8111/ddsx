package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.mfb.core.model.ThemeMode

private val MfbDarkColorScheme = darkColorScheme(
    primary = MfbPrimaryAccent,
    onPrimary = MfbDarkTextPrimary,
    primaryContainer = MfbDarkSurfaceSecondary,
    onPrimaryContainer = MfbDarkTextPrimary,
    secondary = MfbSecondaryAccent,
    onSecondary = MfbDarkBackground,
    secondaryContainer = MfbDarkSurfaceVariant,
    onSecondaryContainer = MfbDarkTextPrimary,
    background = MfbDarkBackground,
    onBackground = MfbDarkTextPrimary,
    surface = MfbDarkSurface,
    onSurface = MfbDarkTextPrimary,
    surfaceVariant = MfbDarkSurfaceSecondary,
    onSurfaceVariant = MfbDarkTextSecondary,
    outline = MfbDarkDivider,
    outlineVariant = MfbDarkSurfaceVariant,
    error = MfbError,
    onError = MfbDarkBackground
)

private val MfbLightColorScheme = lightColorScheme(
    primary = MfbPrimaryAccent,
    onPrimary = MfbLightSurface,
    primaryContainer = MfbLightSurfaceSecondary,
    onPrimaryContainer = MfbPrimaryAccent,
    secondary = MfbSecondaryAccent,
    onSecondary = MfbLightSurface,
    secondaryContainer = MfbLightSurfaceVariant,
    onSecondaryContainer = MfbLightTextPrimary,
    background = MfbLightBackground,
    onBackground = MfbLightTextPrimary,
    surface = MfbLightSurface,
    onSurface = MfbLightTextPrimary,
    surfaceVariant = MfbLightSurfaceSecondary,
    onSurfaceVariant = MfbLightTextSecondary,
    outline = MfbLightDivider,
    outlineVariant = MfbLightSurfaceVariant,
    error = MfbError,
    onError = MfbLightSurface
)

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = if (isDark) MfbDarkColorScheme else MfbLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
