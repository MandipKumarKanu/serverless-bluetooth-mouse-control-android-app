package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ConnectionHistoryRepository
import com.example.data.DeviceSettingsEntity
import com.example.data.SettingsEntity
import com.example.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel focused on app settings and per-device pointer profiles.
 *
 * Extracted from [AirMouseViewModel] to follow the Single Responsibility
 * Principle. Screens that only need settings should depend on this ViewModel
 * instead of the full [AirMouseViewModel].
 *
 * Accepts pre-built repositories to avoid creating duplicate instances.
 */
class SettingsViewModel(
    application: Application,
    private val settingsRepo: SettingsRepository,
    private val connectionHistoryRepo: ConnectionHistoryRepository
) : AndroidViewModel(application) {

    // ── Global settings ───────────────────────────────────────────────

    val globalSettings: StateFlow<SettingsEntity> = settingsRepo.globalSettings

    /**
     * Effective settings for the currently connected device: global settings
     * with the connected device's pointer overrides applied.
     *
     * Pass the connected device address via [connectedDeviceAddress] to
     * activate per-device overrides; pass null for pure global settings.
     */
    fun settingsStateForDevice(connectedDeviceAddress: String?): StateFlow<SettingsEntity> {
        val deviceSettings = if (connectedDeviceAddress == null) {
            flowOf(null)
        } else {
            settingsRepo.getDeviceSettingsFlow(connectedDeviceAddress)
        }
        return combine(globalSettings, deviceSettings) { global, device ->
            if (device == null) global
            else global.copy(
                sensitivity = device.sensitivity,
                smoothing = device.smoothing,
                deadZone = device.deadZone,
                acceleration = device.acceleration,
                scrollSpeed = device.scrollSpeed,
                invertX = device.invertX,
                invertY = device.invertY
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsEntity())
    }

    /** Write settings. Routes pointer fields to device profile when connected. */
    fun updateSettings(newSettings: SettingsEntity, connectedDeviceAddress: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (connectedDeviceAddress == null) {
                settingsRepo.updateSettings(newSettings)
            } else {
                settingsRepo.updateDeviceSettings(
                    DeviceSettingsEntity(
                        deviceAddress = connectedDeviceAddress,
                        sensitivity = newSettings.sensitivity,
                        smoothing = newSettings.smoothing,
                        deadZone = newSettings.deadZone,
                        acceleration = newSettings.acceleration,
                        scrollSpeed = newSettings.scrollSpeed,
                        invertX = newSettings.invertX,
                        invertY = newSettings.invertY
                    )
                )
                val global = settingsRepo.getSettingsDirect() ?: SettingsEntity()
                settingsRepo.updateSettings(
                    newSettings.copy(
                        sensitivity = global.sensitivity,
                        smoothing = global.smoothing,
                        deadZone = global.deadZone,
                        acceleration = global.acceleration,
                        scrollSpeed = global.scrollSpeed,
                        invertX = global.invertX,
                        invertY = global.invertY
                    )
                )
            }
        }
    }

    // ── Per-device profiles ───────────────────────────────────────────

    val deviceProfiles: StateFlow<List<DeviceSettingsEntity>> = settingsRepo.allDeviceSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateDeviceSettingsForDevice(deviceSettings: DeviceSettingsEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.updateDeviceSettings(deviceSettings)
        }
    }

    fun resetDeviceSettingsForDevice(address: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.deleteDeviceSettings(address)
        }
    }

    // ── Connection history ────────────────────────────────────────────

    fun clearConnectionHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            connectionHistoryRepo.clear()
        }
    }
}
