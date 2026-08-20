package com.example.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GestureEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [GestureViewModel] — exercises gesture and shortcut
 * CRUD operations through the shared repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GestureViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        AppDatabase.resetForTesting()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AppDatabase.resetForTesting()
    }

    private fun newViewModel(): GestureViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val db = AppDatabase.getDatabase(app, scope)
        val gestureRepo = com.example.data.GestureRepository(db.airMouseDao())
        return GestureViewModel(app, gestureRepo)
    }

    @Test
    fun addShortcut_persists(): Unit = runBlocking {
        val vm = newViewModel()

        vm.addShortcut("Test Macro", 0x03, "6")

        val saved = vm.shortcuts.first { list -> list.any { it.name == "Test Macro" } }
            .first { it.name == "Test Macro" }
        assertEquals(0x03, saved.modifiers)
        assertEquals("6", saved.keyCodes)
    }

    @Test
    fun deleteShortcut_removesFromFlow(): Unit = runBlocking {
        val vm = newViewModel()

        vm.addShortcut("To Delete", 0x01, "10")
        val saved = vm.shortcuts.first { list -> list.any { it.name == "To Delete" } }
            .first { it.name == "To Delete" }

        vm.deleteShortcut(saved.id)
        vm.shortcuts.first { list -> list.none { it.name == "To Delete" } }
    }

    @Test
    fun saveGesture_persists(): Unit = runBlocking {
        val vm = newViewModel()

        vm.saveGesture(
            GestureEntity(
                name = "Circle",
                points = "[{\"x\":0.0,\"y\":0.0,\"timestamp\":1}]",
                actionType = "keyboard",
                actionData = "copy",
                modifiers = 0
            )
        )

        val saved = vm.gestures.first { list -> list.any { it.name == "Circle" } }
            .first { it.name == "Circle" }
        assertEquals("copy", saved.actionData)
        assertEquals("keyboard", saved.actionType)
    }

    @Test
    fun deleteGesture_removesFromFlow(): Unit = runBlocking {
        val vm = newViewModel()

        vm.saveGesture(
            GestureEntity(
                name = "ToDelete",
                points = "[]",
                actionType = "media",
                actionData = "play_pause"
            )
        )
        val saved = vm.gestures.first { list -> list.any { it.name == "ToDelete" } }
            .first { it.name == "ToDelete" }

        vm.deleteGesture(saved.id)
        vm.gestures.first { list -> list.none { it.name == "ToDelete" } }
    }

    @Test
    fun multipleShortcuts_coexist(): Unit = runBlocking {
        val vm = newViewModel()

        vm.addShortcut("Macro A", 0x01, "6")
        vm.addShortcut("Macro B", 0x04, "25")

        val shortcuts = vm.shortcuts.first { it.size >= 2 }
        assertTrue(shortcuts.any { it.name == "Macro A" })
        assertTrue(shortcuts.any { it.name == "Macro B" })
    }
}
