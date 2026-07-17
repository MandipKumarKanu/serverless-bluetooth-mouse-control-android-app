package com.example.gesture

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single point in a gesture path
 */
data class GesturePoint(
    val x: Float,
    val y: Float,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a recorded gesture with its assigned action
 */
@Entity(tableName = "gestures")
data class GestureEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val points: String, // JSON string of GesturePoint list
    val actionType: String, // "keyboard", "media", "mouse"
    val actionData: String, // Key code, media action, etc.
    val modifiers: Int = 0, // Keyboard modifiers (Ctrl, Shift, Alt, Win)
    val createdAt: Long = System.currentTimeMillis()
)

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
