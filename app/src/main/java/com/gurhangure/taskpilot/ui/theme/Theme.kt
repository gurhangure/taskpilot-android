package com.gurhangure.taskpilot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TaskPilotColorScheme = darkColorScheme(
    primary = TaskPilotColors.Primary,
    onPrimary = TaskPilotColors.Background,
    primaryContainer = TaskPilotColors.SurfaceRaised,
    onPrimaryContainer = TaskPilotColors.TextPrimary,
    secondary = TaskPilotColors.Primary,
    onSecondary = TaskPilotColors.Background,
    secondaryContainer = TaskPilotColors.SurfaceRaised,
    onSecondaryContainer = TaskPilotColors.TextPrimary,
    tertiary = TaskPilotColors.Success,
    onTertiary = TaskPilotColors.Background,
    background = TaskPilotColors.Background,
    onBackground = TaskPilotColors.TextPrimary,
    surface = TaskPilotColors.Surface,
    onSurface = TaskPilotColors.TextPrimary,
    surfaceVariant = TaskPilotColors.SurfaceRaised,
    onSurfaceVariant = TaskPilotColors.TextSecondary,
    outline = TaskPilotColors.Border,
    error = TaskPilotColors.Error,
    onError = TaskPilotColors.Background,
    surfaceTint = TaskPilotColors.Primary,
)

/** Applies the fixed dark identity regardless of system theme or wallpaper colors. */
@Composable
fun TaskPilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TaskPilotColorScheme,
        typography = TaskPilotTypography,
        shapes = TaskPilotShapes,
        content = content,
    )
}
