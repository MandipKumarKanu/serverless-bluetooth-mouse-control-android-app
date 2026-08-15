package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Maximum comfortable content width for the current window. Screen content is
 * centered within this width on large displays so rows and controls don't
 * stretch edge-to-edge on tablets or landscape phones. Mirrors Material 3
 * window size classes (medium >= 600dp, expanded >= 840dp).
 */
@Composable
fun rememberContentMaxWidth(): Dp {
    val screenWidth = LocalDensity.current.run { LocalConfiguration.current.screenWidthDp.dp }
    return when {
        screenWidth >= 840.dp -> 720.dp
        screenWidth >= 600.dp -> 600.dp
        else -> screenWidth
    }
}

/**
 * Standard screen body: fills the window and keeps content centered within
 * [rememberContentMaxWidth] on large screens, applying [horizontalPadding]
 * and optional vertical scrolling. Screens use this as their scrollable
 * content column so every screen gets the same fluid, centered layout.
 */
@Composable
fun AdaptiveScreenBody(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 0.dp,
    scrollable: Boolean = true,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = rememberContentMaxWidth())
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                .let { if (scrollable) it.verticalScroll(rememberScrollState()) else it },
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content
        )
    }
}

/**
 * Adaptive body for list screens: centers a [LazyColumn]-style body within
 * the comfortable width. Wrap a LazyColumn (or any full-size body) with this
 * from inside the screen's scaffold so lists don't stretch edge-to-edge.
 */
@Composable
fun AdaptiveListBody(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = rememberContentMaxWidth()),
            content = content
        )
    }
}

/**
 * Diameter for the app's large circular controls (air-mouse pad, media-remote
 * D-pad). Scales with the available window width so pads fill small screens
 * but don't balloon on tablets, bounded by [min] and [max].
 */
@Composable
fun responsiveControlDiameter(min: Dp = 180.dp, max: Dp = 300.dp): Dp {
    val screenWidth = LocalDensity.current.run { LocalConfiguration.current.screenWidthDp.dp }
    return (screenWidth * 0.55f).coerceIn(min, max)
}
