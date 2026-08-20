package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Single source of truth for app and per-device pointer settings.
 *
 * Abstracts Room ([AirMouseDao]) access and SharedPreferences for
 * auto-reconnect preferences, so the ViewModel doesn't touch the
 * database or preferences directly.
 */
class SettingsRepository(
    private val dao: AirMouseDao,
    context: Context,
    scope: CoroutineScope
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Global settings ───────────────────────────────────────────────

    /** Observable global settings row (emits default if row doesn't exist yet). */
    val globalSettings: StateFlow<SettingsEntity> = dao.getSettingsFlow()
        .map { it ?: SettingsEntity() }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), SettingsEntity())

    /** Suspend to read the global settings row once (for non-Flow callers). */
    suspend fun getSettingsDirect(): SettingsEntity? = dao.getSettingsDirect()

    /** Write the global settings row. */
    suspend fun updateSettings(settings: SettingsEntity) = dao.updateSettings(settings)

    // ── Per-device pointer settings ───────────────────────────────────

    /** Observable per-device pointer profile for a given host address. */
    fun getDeviceSettingsFlow(address: String): Flow<DeviceSettingsEntity?> =
        dao.getDeviceSettingsFlow(address)

    /** Suspend to read a device profile once. */
    suspend fun getDeviceSettingsDirect(address: String): DeviceSettingsEntity? =
        dao.getDeviceSettingsDirect(address)

    /** Write (upsert) a per-device pointer profile. */
    suspend fun updateDeviceSettings(deviceSettings: DeviceSettingsEntity) =
        dao.updateDeviceSettings(deviceSettings)

    /** Delete a per-device profile so it falls back to global settings. */
    suspend fun deleteDeviceSettings(address: String) = dao.deleteDeviceSettings(address)

    /** All saved per-device profiles. */
    val allDeviceSettings: Flow<List<DeviceSettingsEntity>> = dao.getAllDeviceSettingsFlow()

    // ── Auto-reconnect preferences ────────────────────────────────────

    val autoReconnectEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECONNECT, true)

    fun setAutoReconnectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RECONNECT, enabled).apply()
    }

    val lastConnectedDeviceAddress: String?
        get() = prefs.getString(KEY_LAST_DEVICE, null)

    fun setLastConnectedDeviceAddress(address: String?) {
        prefs.edit().putString(KEY_LAST_DEVICE, address).apply()
    }

    companion object {
        private const val PREFS_NAME = "air_mouse_prefs"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect_enabled"
        private const val KEY_LAST_DEVICE = "last_connected_device_address"
    }
}
