package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

object ThemeColors {
    // Background colors
    val background: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDarkTheme()) DarkBackground else LightBackground

    val surface: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDarkTheme()) DarkSurface else LightSurface

    val surfaceVariant: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDarkTheme()) DarkSurfaceVariant else LightSurfaceVariant

    // Text colors
    val onBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDarkTheme()) DarkOnBackground else LightOnBackground

    val onSurface: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDarkTheme()) DarkOnSurface else LightOnSurface

    val onSurfaceVariant: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDarkTheme()) DarkOnSurfaceVariant else LightOnSurfaceVariant

    // Border colors
    val border: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDarkTheme()) DarkBorder else LightBorder

    // Status colors
    val error: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDarkTheme()) DarkError else LightError

    val errorContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDarkTheme()) DarkErrorContainer else LightErrorContainer

    val success: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDarkTheme()) DarkSuccess else LightSuccess

    val successContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDarkTheme()) DarkSuccessContainer else LightSuccessContainer

    val primary: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDarkTheme()) Cyan else CyanDark

    @Composable
    @ReadOnlyComposable
    private fun isDarkTheme(): Boolean {
        return MaterialTheme.colorScheme.background == DarkBackground ||
               MaterialTheme.colorScheme.surface == DarkSurface
    }
}
