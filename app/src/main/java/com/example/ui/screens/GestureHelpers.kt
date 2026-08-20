package com.example.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.gesture.GestureActions
import com.example.gesture.GesturePoint
import com.example.gesture.deserializeGesturePoints

/**
 * Parse points string back to GesturePoint list (JSON with legacy fallback).
 */
fun parsePoints(pointsStr: String): List<GesturePoint> = deserializeGesturePoints(pointsStr)

/**
 * Quick-action buttons shown below the gesture canvas.
 * Each entry: (label, actionKey, icon).
 */
@Suppress("DEPRECATION")
fun getQuickActions(): List<Triple<String, String, ImageVector>> {
    return listOf(
        Triple("Copy", "copy", Icons.Default.ContentCopy),
        Triple("Paste", "paste", Icons.Default.ContentPaste),
        Triple("Undo", "undo", Icons.Default.Undo),
        Triple("Redo", "redo", Icons.Default.Redo),
        Triple("Vol +", "vol_up", Icons.Default.VolumeUp),
        Triple("Vol -", "vol_down", Icons.Default.VolumeDown),
        Triple("Play/Pause", "play_pause", Icons.Default.PlayArrow),
        Triple("Next Track", "next_track", Icons.Default.SkipNext),
        Triple("Prev Track", "prev_track", Icons.Default.SkipPrevious)
    )
}

/**
 * All assignable actions for the gesture registration dialog.
 * Every action supported by AirMouseViewModel.executeGestureAction is
 * assignable, so the UI can never offer an action that doesn't work.
 */
fun getAssignableActions(): List<Pair<String, String>> {
    return listOf(
        // Keyboard
        "copy", "paste", "undo", "redo", "select_all", "save", "close",
        "tab", "enter", "esc", "delete", "backspace",
        // Media
        "play_pause", "next_track", "prev_track", "vol_up", "vol_down", "mute",
        // Mouse
        "left_click", "right_click", "middle_click", "scroll_up", "scroll_down",
        // Presentation
        "next_slide", "prev_slide", "fullscreen", "black_screen"
    ).map { GestureActions.getActionLabel(it) to it }
}
