package com.example.data

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for gestures and custom keyboard shortcuts.
 *
 * Wraps [AirMouseDao] so the ViewModel never touches the DAO directly
 * for these entities.
 */
class GestureRepository(private val dao: AirMouseDao) {

    // ── Gestures ──────────────────────────────────────────────────────

    /** Observable list of saved gestures, newest first. */
    val gestures: Flow<List<GestureEntity>> = dao.getAllGesturesFlow()

    /** Insert or update a gesture. */
    suspend fun insertGesture(gesture: GestureEntity) = dao.insertGesture(gesture)

    /** Delete a gesture by its id. */
    suspend fun deleteGesture(id: Int) = dao.deleteGesture(id)

    // ── Shortcuts ─────────────────────────────────────────────────────

    /** Observable list of saved keyboard shortcuts. */
    val shortcuts: Flow<List<ShortcutEntity>> = dao.getAllShortcutsFlow()

    /** Insert or update a shortcut. */
    suspend fun insertShortcut(shortcut: ShortcutEntity) = dao.insertShortcut(shortcut)

    /** Delete a shortcut by its id. */
    suspend fun deleteShortcut(id: Int) = dao.deleteShortcut(id)
}
