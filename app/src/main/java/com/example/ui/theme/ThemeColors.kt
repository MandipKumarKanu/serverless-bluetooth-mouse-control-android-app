package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

object ThemeColors {
    // Background colors
    val background: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.background

    val surface: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface

    val surfaceVariant: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceVariant

    // Text colors
    val onBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onBackground

    val onSurface: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurface

    val onSurfaceVariant: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    // Border colors
    val border: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outline

    // Status colors
    val error: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.error

    val errorContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.errorContainer

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
        get() = MaterialTheme.colorScheme.primary

    @Composable
    @ReadOnlyComposable
    private fun isDarkTheme(): Boolean {
        return MaterialTheme.colorScheme.background.luminance() < 0.5f
    }
}
