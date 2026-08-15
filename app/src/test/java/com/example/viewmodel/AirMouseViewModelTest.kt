package com.example.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GestureEntity
import com.example.data.SettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the ViewModel's Room-backed flows and SharedPreferences-backed
 * settings. The Room database singleton is reset between tests so each test
 * observes a clean database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AirMouseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        AppDatabase.resetForTesting()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AppDatabase.resetForTesting()
    }

    private fun newViewModel(): AirMouseViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return AirMouseViewModel(app)
    }

    @Test
    fun updateSettings_persistsThroughSettingsFlow() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        viewModel.updateSettings(SettingsEntity(sensitivity = 2.4f, smoothing = 0.6f, invertX = true))

        val settings = viewModel.settingsState.first { it.sensitivity == 2.4f }
        assertEquals(2.4f, settings.sensitivity, 0.001f)
        assertEquals(0.6f, settings.smoothing, 0.001f)
        assertTrue(settings.invertX)

        viewModel.clearForTest()
    }

    @Test
    fun customShortcuts_addThenDelete() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        viewModel.addCustomShortcut("My Macro", 0x01, "6")

        val afterAdd = viewModel.shortcutsState.first { list -> list.any { it.name == "My Macro" } }
        val saved = afterAdd.first { it.name == "My Macro" }
        assertEquals(0x01, saved.modifiers)
        assertEquals("6", saved.keyCodes)

        viewModel.deleteShortcut(saved.id)
        viewModel.shortcutsState.first { list -> list.none { it.name == "My Macro" } }

        viewModel.clearForTest()
    }

    @Test
    fun gestures_saveThenDelete() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        viewModel.saveGesture(
            GestureEntity(
                name = "Swipe Up",
                points = "[{\"x\":0.0,\"y\":0.0,\"timestamp\":1}]",
                actionType = "keyboard",
                actionData = "esc",
                modifiers = 0
            )
        )

        val saved = viewModel.gesturesState.first { list -> list.any { it.name == "Swipe Up" } }
            .first { it.name == "Swipe Up" }
        assertEquals("esc", saved.actionData)

        viewModel.deleteGesture(saved.id)
        viewModel.gesturesState.first { list -> list.none { it.name == "Swipe Up" } }

        viewModel.clearForTest()
    }

    @Test
    fun clearConnectionHistory_doesNotThrow() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        viewModel.clearConnectionHistory()
        // History starts empty in a fresh DB; clearing must be a no-op, not a crash
        assertTrue(viewModel.connectionHistory.first().isEmpty())
        viewModel.clearForTest()
    }

    @Test
    fun autoReconnectPrefs_persistInSharedPreferences() {
        val viewModel = newViewModel()

        assertEquals(true, viewModel.autoReconnectEnabled.value)
        viewModel.setAutoReconnectEnabled(false)
        assertFalse(viewModel.autoReconnectEnabled.value)

        assertNull(viewModel.lastConnectedDeviceAddress.value)
        viewModel.setLastConnectedDeviceAddress("AA:BB:CC:DD:EE:FF")
        assertEquals("AA:BB:CC:DD:EE:FF", viewModel.lastConnectedDeviceAddress.value)

        viewModel.clearForTest()
    }

    @Test
    fun connectHelpers_noCrashWhenDisconnected() = runTest(testDispatcher) {
        val viewModel = newViewModel()

        // Nothing is bonded under Robolectric; these must be safe no-ops.
        viewModel.connectToDeviceByAddress(null)
        viewModel.connectToDeviceByAddress("00:11:22:33:44:55")
        viewModel.cancelConnection()
        viewModel.disconnectDevice()

        // Transmissions while disconnected must fail safely (no exception).
        viewModel.sendTouchMove(10f, 10f)
        viewModel.sendScrollTicks(2)
        viewModel.sendMouseClick(1)
        viewModel.sendMediaAction(0x08)
        viewModel.sendKeyboardKey(0x01, 0x06)
        viewModel.sendText("hi")

        viewModel.clearForTest()
    }
}
