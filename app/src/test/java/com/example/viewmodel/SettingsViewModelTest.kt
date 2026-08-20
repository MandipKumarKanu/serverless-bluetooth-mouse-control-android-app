package com.example.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DeviceSettingsEntity
import com.example.data.SettingsEntity
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
 * Unit tests for [SettingsViewModel] — exercises settings CRUD,
 * per-device profiles, and connection history through the shared
 * repository instances.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsViewModelTest {

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

    private fun newViewModel(): SettingsViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val db = AppDatabase.getDatabase(app, scope)
        val settingsRepo = com.example.data.SettingsRepository(db.airMouseDao(), app, scope)
        val connectionHistoryRepo = com.example.data.ConnectionHistoryRepository(db.airMouseDao())
        return SettingsViewModel(app, settingsRepo, connectionHistoryRepo)
    }

    @Test
    fun updateSettings_persistsWhenDisconnected(): Unit = runBlocking {
        val vm = newViewModel()

        vm.updateSettings(SettingsEntity(sensitivity = 2.0f, smoothing = 0.7f), connectedDeviceAddress = null)

        val settings = vm.globalSettings.first { it.sensitivity == 2.0f }
        assertEquals(2.0f, settings.sensitivity, 0.001f)
        assertEquals(0.7f, settings.smoothing, 0.001f)
    }

    @Test
    fun settingsStateForDevice_returnsGlobalWhenNoDevice(): Unit = runBlocking {
        val vm = newViewModel()

        val settings = vm.settingsStateForDevice(null).first()
        // Should be the default settings
        assertEquals(1.0f, settings.sensitivity, 0.001f)
    }

    @Test
    fun perDeviceProfile_persistsThenResets(): Unit = runBlocking {
        val vm = newViewModel()
        val address = "AA:BB:CC:DD:EE:FF"

        val profile = DeviceSettingsEntity(
            deviceAddress = address,
            sensitivity = 3.0f,
            smoothing = 0.9f,
            deadZone = 0.1f,
            acceleration = 2.0f,
            scrollSpeed = 1.5f,
            invertX = true,
            invertY = false
        )
        vm.updateDeviceSettingsForDevice(profile)

        val saved = vm.deviceProfiles.first { list -> list.any { it.deviceAddress == address } }
            .first { it.deviceAddress == address }
        assertEquals(3.0f, saved.sensitivity, 0.001f)
        assertEquals(true, saved.invertX)

        vm.resetDeviceSettingsForDevice(address)
        vm.deviceProfiles.first { list -> list.none { it.deviceAddress == address } }
    }

    @Test
    fun clearConnectionHistory_doesNotThrow(): Unit = runBlocking {
        val vm = newViewModel()
        vm.clearConnectionHistory()
        // Should be a safe no-op on an empty history
        assertTrue(true)
    }
}
