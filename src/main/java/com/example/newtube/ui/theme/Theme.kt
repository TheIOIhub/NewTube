package com.example.newtube.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun NewTubeTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = md_theme_light_primary,
        secondary = md_theme_light_secondary,
        background = md_theme_light_background,
        surface = md_theme_light_surface,
        onPrimary = md_theme_light_onPrimary,
        onSecondary = md_theme_light_onSecondary,
        onBackground = md_theme_light_onBackground,
        onSurface = md_theme_light_onSurface
    )
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}