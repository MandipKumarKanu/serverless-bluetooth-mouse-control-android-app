package com.example.gesture

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Represents a single point in a gesture path.
 *
 * Persisted as JSON in [com.example.data.GestureEntity.points] via the Moshi
 * adapters below (previously stored as the data class toString() output, which
 * was fragile; the legacy format is still parsed as a fallback).
 */
@JsonClass(generateAdapter = true)
data class GesturePoint(
    val x: Float,
    val y: Float,
    val timestamp: Long = System.currentTimeMillis()
)

private val gestureMoshi: Moshi by lazy { Moshi.Builder().build() }

private val gestureListAdapter: JsonAdapter<List<GesturePoint>> by lazy {
    gestureMoshi.adapter(Types.newParameterizedType(List::class.java, GesturePoint::class.java))
}

/** Serialize a list of gesture points to a JSON string for Room persistence. */
fun serializeGesturePoints(points: List<GesturePoint>): String = gestureListAdapter.toJson(points)

/**
 * Deserialize gesture points from a persisted string.
 * Falls back to parsing the legacy data-class toString() format so gestures
 * saved by older app versions keep working.
 */
fun deserializeGesturePoints(pointsStr: String): List<GesturePoint> {
    if (pointsStr.isBlank()) return emptyList()
    return try {
        gestureListAdapter.fromJson(pointsStr) ?: emptyList()
    } catch (e: Exception) {
        parseLegacyGesturePoints(pointsStr)
    }
}

/** Fallback parser for gestures persisted with the old data-class toString() format. */
private fun parseLegacyGesturePoints(pointsStr: String): List<GesturePoint> {
    return try {
        val cleaned = pointsStr.removePrefix("[").removeSuffix("]")
        if (cleaned.isBlank()) return emptyList()

        cleaned.split("), ").map { pointStr ->
            val coords = pointStr.removePrefix("GesturePoint(x=").removeSuffix(")")
                .split(", y=")
            GesturePoint(
                x = coords[0].toFloatOrNull() ?: 0f,
                y = coords[1].split(", timestamp=")[0].toFloatOrNull() ?: 0f
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Classify a gesture action string into the stored actionType category
 * ("keyboard", "media", or "mouse") so persisted gestures are categorized
 * correctly instead of always being tagged "keyboard".
 */
fun gestureActionTypeFor(action: String): String {
    val mediaActions = setOf("play_pause", "next_track", "prev_track", "vol_up", "vol_down", "mute")
    val mouseActions = setOf("left_click", "right_click", "middle_click", "scroll_up", "scroll_down")
    return when {
        action in mediaActions -> "media"
        action in mouseActions -> "mouse"
        else -> "keyboard"
    }
}

/**
 * Action types for gestures
 */
enum class GestureActionType {
    KEYBOARD,   // Keyboard key combination
    MEDIA,      // Media control (volume, play/pause)
    MOUSE,      // Mouse action (click, scroll)
    SHORTCUT    // Custom shortcut
}

/**
 * Pre-defined gesture actions
 */
object GestureActions {
    // Keyboard actions
    const val ACTION_COPY = "copy"      // Ctrl+C
    const val ACTION_PASTE = "paste"    // Ctrl+V
    const val ACTION_UNDO = "undo"      // Ctrl+Z
    const val ACTION_REDO = "redo"      // Ctrl+Y
    const val ACTION_SELECT_ALL = "select_all" // Ctrl+A
    const val ACTION_SAVE = "save"      // Ctrl+S
    const val ACTION_CLOSE = "close"    // Ctrl+W
    const val ACTION_TAB = "tab"        // Tab key
    const val ACTION_ENTER = "enter"    // Enter key
    const val ACTION_ESC = "esc"        // Escape key
    const val ACTION_DELETE = "delete"  // Delete key
    const val ACTION_BACKSPACE = "backspace" // Backspace key

    // Media actions
    const val ACTION_PLAY_PAUSE = "play_pause"
    const val ACTION_NEXT_TRACK = "next_track"
    const val ACTION_PREV_TRACK = "prev_track"
    const val ACTION_VOL_UP = "vol_up"
    const val ACTION_VOL_DOWN = "vol_down"
    const val ACTION_MUTE = "mute"

    // Mouse actions
    const val ACTION_LEFT_CLICK = "left_click"
    const val ACTION_RIGHT_CLICK = "right_click"
    const val ACTION_MIDDLE_CLICK = "middle_click"
    const val ACTION_SCROLL_UP = "scroll_up"
    const val ACTION_SCROLL_DOWN = "scroll_down"

    // Presentation actions
    const val ACTION_NEXT_SLIDE = "next_slide"
    const val ACTION_PREV_SLIDE = "prev_slide"
    const val ACTION_FULLSCREEN = "fullscreen"
    const val ACTION_BLACK_SCREEN = "black_screen"

    fun getActionLabel(action: String): String {
        return when (action) {
            ACTION_COPY -> "Copy (Ctrl+C)"
            ACTION_PASTE -> "Paste (Ctrl+V)"
            ACTION_UNDO -> "Undo (Ctrl+Z)"
            ACTION_REDO -> "Redo (Ctrl+Y)"
            ACTION_SELECT_ALL -> "Select All (Ctrl+A)"
            ACTION_SAVE -> "Save (Ctrl+S)"
            ACTION_CLOSE -> "Close (Ctrl+W)"
            ACTION_TAB -> "Tab"
            ACTION_ENTER -> "Enter"
            ACTION_ESC -> "Escape"
            ACTION_DELETE -> "Delete"
            ACTION_BACKSPACE -> "Backspace"
            ACTION_PLAY_PAUSE -> "Play/Pause"
            ACTION_NEXT_TRACK -> "Next Track"
            ACTION_PREV_TRACK -> "Previous Track"
            ACTION_VOL_UP -> "Volume Up"
            ACTION_VOL_DOWN -> "Volume Down"
            ACTION_MUTE -> "Mute"
            ACTION_LEFT_CLICK -> "Left Click"
            ACTION_RIGHT_CLICK -> "Right Click"
            ACTION_MIDDLE_CLICK -> "Middle Click"
            ACTION_SCROLL_UP -> "Scroll Up"
            ACTION_SCROLL_DOWN -> "Scroll Down"
            ACTION_NEXT_SLIDE -> "Next Slide"
            ACTION_PREV_SLIDE -> "Previous Slide"
            ACTION_FULLSCREEN -> "Fullscreen (F5)"
            ACTION_BLACK_SCREEN -> "Black Screen (B)"
            else -> action
        }
    }
}
