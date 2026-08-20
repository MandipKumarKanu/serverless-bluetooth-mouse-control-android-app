package com.example.ui.screens

/**
 * Type-safe route definitions for Compose Navigation.
 *
 * Replaces the raw string constants in [Routes] with a sealed class hierarchy
 * so that navigation calls are checked at compile time. Every route is an
 * `object` (no arguments needed for this app) and implements [NavRoute]
 * which provides the string path for the NavHost.
 *
 * Usage:
 * ```kotlin
 * // Before (fragile string):
 * navController.navigate("touchpad")
 *
 * // After (compile-time checked):
 * navController.navigate(NavRoute.Touchpad)
 * ```
 */
sealed class NavRoute(val path: String) {

    data object Splash : NavRoute("splash")
    data object Permissions : NavRoute("permissions")
    data object Dashboard : NavRoute("dashboard")
    data object Touchpad : NavRoute("touchpad")
    data object AirMouse : NavRoute("air_mouse")
    data object Keyboard : NavRoute("keyboard")
    data object MediaRemote : NavRoute("media_remote")
    data object Presentation : NavRoute("presentation")
    data object Shortcuts : NavRoute("shortcuts")
    data object Settings : NavRoute("settings")
    data object DeviceSettings : NavRoute("device_settings")
    data object About : NavRoute("about")
    data object Gamepad : NavRoute("gamepad")
    data object Gesture : NavRoute("gesture")

    companion object {
        /** All routes for the NavHost composable graph. */
        val all: List<NavRoute> = listOf(
            Splash, Permissions, Dashboard, Touchpad, AirMouse,
            Keyboard, MediaRemote, Presentation, Shortcuts, Settings,
            DeviceSettings, About, Gamepad, Gesture
        )
    }
}

/**
 * Extension on [androidx.navigation.NavController] for type-safe navigation.
 *
 * ```kotlin
 * navController.navigateTo(NavRoute.Dashboard)
 * navController.navigateTo(NavRoute.Permissions) {
 *     popUpTo(NavRoute.Splash.path) { inclusive = true }
 * }
 * ```
 */
fun androidx.navigation.NavController.navigateTo(
    route: NavRoute,
    builder: androidx.navigation.NavOptionsBuilder.() -> Unit = {}
) {
    navigate(route.path, builder)
}
