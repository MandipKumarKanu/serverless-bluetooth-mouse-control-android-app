package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.GestureEntity
import com.example.data.GestureRepository
import com.example.data.ShortcutEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel focused on gesture and custom keyboard shortcut CRUD.
 *
 * Extracted from [AirMouseViewModel] to follow the Single Responsibility
 * Principle. Screens that only need gestures/shortcuts should depend on this
 * ViewModel instead of the full [AirMouseViewModel].
 *
 * Note: [executeGestureAction] stays in [AirMouseViewModel] because it
 * dispatches HID transmission calls (keyboard, media, mouse) that require
 * the Bluetooth connection.
 */
class GestureViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val gestureRepo = GestureRepository(db.airMouseDao())

    // ── Shortcuts ─────────────────────────────────────────────────────

    val shortcuts: StateFlow<List<ShortcutEntity>> = gestureRepo.shortcuts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addShortcut(name: String, modifiers: Int, keyCodes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            gestureRepo.insertShortcut(ShortcutEntity(name = name, modifiers = modifiers, keyCodes = keyCodes))
        }
    }

    fun deleteShortcut(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            gestureRepo.deleteShortcut(id)
        }
    }

    // ── Gestures ──────────────────────────────────────────────────────

    val gestures: StateFlow<List<GestureEntity>> = gestureRepo.gestures
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveGesture(gesture: GestureEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            gestureRepo.insertGesture(gesture)
        }
    }

    fun deleteGesture(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            gestureRepo.deleteGesture(id)
        }
    }
}
