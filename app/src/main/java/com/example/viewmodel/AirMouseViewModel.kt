package com.example.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothHidManager
import com.example.bluetooth.getSafeName
import com.example.data.AppDatabase
import com.example.data.ConnectionHistoryEntity
import com.example.data.SettingsEntity
import com.example.data.ShortcutEntity
import com.example.sensor.MotionSensorManager
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AirMouseViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val dao = db.airMouseDao()

    val hidManager = BluetoothHidManager.getInstance(application)
    private val sensorManager = MotionSensorManager(application)

    // Room persistence states
    val settingsState: StateFlow<SettingsEntity> = dao.getSettingsFlow()
        .map { it ?: SettingsEntity() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsEntity()
        )

    val shortcutsState: StateFlow<List<ShortcutEntity>> = dao.getAllShortcutsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val connectionHistory: StateFlow<List<ConnectionHistoryEntity>> = dao.getRecentConnectionsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Bluetooth States from native service
    val bluetoothState: StateFlow<Int> = hidManager.connectionState
    val connectedDevice: StateFlow<BluetoothDevice?> = hidManager.connectedDevice
    val isProfileReady: StateFlow<Boolean> = hidManager.isProfileReady
    val isAppRegistered: StateFlow<Boolean> = hidManager.isAppRegistered
    val isBluetoothPowerOn: StateFlow<Boolean> = hidManager.isBluetoothEnabledFlow

    // Dynamic paired devices list
    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    // Battery level
    private val _batteryLevel = MutableStateFlow(0)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    // SharedPreferences for safe storage of auto-reconnect settings
    private val prefs = application.getSharedPreferences("air_mouse_prefs", Context.MODE_PRIVATE)

    private val _autoReconnectEnabled = MutableStateFlow(prefs.getBoolean("auto_reconnect_enabled", true))
    val autoReconnectEnabled: StateFlow<Boolean> = _autoReconnectEnabled.asStateFlow()

    private val _lastConnectedDeviceAddress = MutableStateFlow(prefs.getString("last_connected_device_address", null))
    val lastConnectedDeviceAddress: StateFlow<String?> = _lastConnectedDeviceAddress.asStateFlow()

    // Auto-reconnect job to prevent multiple concurrent reconnect attempts
    private var autoReconnectJob: Job? = null

    // Debounce state for auto-reconnect
    private var lastAutoReconnectTime = 0L
    private val autoReconnectDebounceMs = 3000L

    fun setAutoReconnectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_reconnect_enabled", enabled).apply()
        _autoReconnectEnabled.value = enabled
    }

    fun setLastConnectedDeviceAddress(address: String?) {
        prefs.edit().putString("last_connected_device_address", address).apply()
        _lastConnectedDeviceAddress.value = address
    }

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    init {
        // Fetch battery level
        fetchBatteryLevel()

        // Sync settings to sensor manager on database emission
        viewModelScope.launch {
            settingsState.collect { settings ->
                sensorManager.updateSettings(settings)
            }
        }

        // Auto-reconnect: Only trigger when profile becomes ready AND bluetooth is on
        // StateFlow already deduplicates, no need for distinctUntilChanged
        viewModelScope.launch {
            isProfileReady
                .collect { isReady ->
                    if (isReady && isBluetoothPowerOn.value && autoReconnectEnabled.value) {
                        delay(500) // Small delay to let connection stabilize
                        triggerAutoReconnect()
                    }
                }
        }

        // Also trigger auto-reconnect when bluetooth is turned on (if profile is already ready)
        viewModelScope.launch {
            isBluetoothPowerOn
                .collect { isOn ->
                    if (isOn && isProfileReady.value && autoReconnectEnabled.value) {
                        delay(500) // Small delay to let bluetooth stabilize
                        triggerAutoReconnect()
                    }
                }
        }

        // Flow collector for Bluetooth state feedback Toasts and connection history
        viewModelScope.launch(Dispatchers.Main) {
            var lastState: Int? = null
            var lastDevice: BluetoothDevice? = null

            bluetoothState.collect { state ->
                val currentDevice = connectedDevice.value
                val deviceName = currentDevice?.getSafeName() ?: lastDevice?.getSafeName() ?: "Device"

                if (lastState != null && lastState != state) {
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Toast.makeText(application, "Connected to $deviceName", Toast.LENGTH_SHORT).show()
                            currentDevice?.let {
                                setLastConnectedDeviceAddress(it.address)
                                // Save to connection history
                                viewModelScope.launch(Dispatchers.IO) {
                                    dao.insertConnection(
                                        ConnectionHistoryEntity(
                                            deviceName = deviceName,
                                            deviceAddress = it.address
                                        )
                                    )
                                }
                            }
                        }
                        BluetoothProfile.STATE_CONNECTING -> {
                            Toast.makeText(application, "Connecting to $deviceName...", Toast.LENGTH_SHORT).show()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            if (lastState == BluetoothProfile.STATE_CONNECTED || lastState == BluetoothProfile.STATE_CONNECTING) {
                                Toast.makeText(application, "Disconnected from $deviceName", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                lastState = state
                if (currentDevice != null) {
                    lastDevice = currentDevice
                }
            }
        }

        refreshPairedDevices()
    }

    fun triggerAutoReconnect() {
        // Cancel any existing reconnect job
        autoReconnectJob?.cancel()

        autoReconnectJob = viewModelScope.launch {
            val lastAddress = lastConnectedDeviceAddress.value ?: return@launch
            if (!autoReconnectEnabled.value) return@launch

            // Debounce: Don't reconnect too frequently
            val now = System.currentTimeMillis()
            if (now - lastAutoReconnectTime < autoReconnectDebounceMs) {
                Log.d(TAG, "Auto-reconnect debounce active, skipping")
                return@launch
            }

            // Only try if not connected or connecting
            if (hidManager.isConnected() || hidManager.isCurrentlyConnecting()) {
                Log.d(TAG, "Already connected or connecting, skipping auto-reconnect")
                return@launch
            }

            // Must have bluetooth enabled and profile ready
            if (!isBluetoothEnabled() || !isProfileReady.value) {
                Log.d(TAG, "Bluetooth not ready, skipping auto-reconnect")
                return@launch
            }

            lastAutoReconnectTime = System.currentTimeMillis()

            val bonded = hidManager.getBondedDevices()
            val deviceToConnect = bonded.find { it.address == lastAddress }
            if (deviceToConnect != null) {
                Log.d(TAG, "Auto reconnecting to last connected device: ${deviceToConnect.getSafeName()} [${deviceToConnect.address}]")
                connectToDevice(deviceToConnect)
            } else {
                Log.d(TAG, "Last connected device not found in bonded devices")
            }
        }
    }

    fun refreshPairedDevices() {
        _pairedDevices.value = hidManager.getBondedDevices()
    }

    fun connectToDevice(device: BluetoothDevice) {
        vibrate(50)
        hidManager.connectHost(device)
    }

    fun disconnectDevice() {
        vibrate(50)
        hidManager.disconnectHost()
    }

    fun fetchBatteryLevel() {
        val batteryManager = app.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val batteryLevel = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        _batteryLevel.value = batteryLevel
    }

    fun clearConnectionHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearConnectionHistory()
        }
    }

    fun isBluetoothEnabled(): Boolean = hidManager.isBluetoothEnabled()

    @SuppressLint("MissingPermission")
    fun enableBluetooth() {
        vibrate(40)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(intent)
            } else {
                @Suppress("DEPRECATION")
                val adapter = BluetoothAdapter.getDefaultAdapter()
                @Suppress("DEPRECATION")
                adapter?.enable()
            }
        } catch (e: SecurityException) {
            try {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(app, "Please enable Bluetooth in system settings", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(app, "Please enable Bluetooth in system settings", Toast.LENGTH_LONG).show()
        }
    }

    // Dynamic database update wrapper
    fun updateSettings(newSettings: SettingsEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateSettings(newSettings)
        }
    }

    fun addCustomShortcut(name: String, modifiers: Int, keyCodes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertShortcut(ShortcutEntity(name = name, modifiers = modifiers, keyCodes = keyCodes))
        }
    }

    fun deleteShortcut(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteShortcut(id)
        }
    }

    // --- SENSOR TRIGGER ---
    fun startAirMouse(buttonsState: Byte = 0) {
        sensorManager.updateSettings(settingsState.value)
        sensorManager.start(buttonsState)
    }

    fun stopAirMouse() {
        sensorManager.stop()
    }

    fun calibrateAirMouse() {
        vibrate(100)
        sensorManager.calibrate()
    }

    fun setAirMouseButtons(buttonsState: Byte) {
        sensorManager.setButtonsState(buttonsState)
    }

    // --- MULTI-TOUCH TOUCHPAD ACTIONS ---
    // Track current mouse button state for drag operations
    private val _currentMouseButtons = MutableStateFlow<Byte>(0)
    val currentMouseButtons: StateFlow<Byte> = _currentMouseButtons.asStateFlow()

    // Sends touch moves relative
    fun sendTouchMove(dx: Float, dy: Float, buttons: Byte = 0, scroll: Byte = 0) {
        val sensitivity = settingsState.value.sensitivity
        val finalDx = (dx * sensitivity).coerceIn(-127f, 127f).toInt().toByte()
        val finalDy = (dy * sensitivity).coerceIn(-127f, 127f).toInt().toByte()

        // Use current mouse buttons if not specified
        val actualButtons = if (buttons == 0.toByte()) _currentMouseButtons.value else buttons

        if (finalDx != 0.toByte() || finalDy != 0.toByte() || scroll != 0.toByte()) {
            hidManager.sendMouseInput(actualButtons, finalDx, finalDy, scroll)
        }
    }

    fun sendMouseClick(button: Byte) {
        vibrate(30)
        // Click Down
        hidManager.sendMouseInput(button, 0, 0, 0)
        // Release immediately (25ms delay)
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(25)
            hidManager.sendMouseInput(0, 0, 0, 0)
        }
    }

    fun sendMouseDown(button: Byte) {
        _currentMouseButtons.value = button
        hidManager.sendMouseInput(button, 0, 0, 0)
    }

    fun sendMouseUp() {
        _currentMouseButtons.value = 0
        hidManager.sendMouseInput(0, 0, 0, 0)
    }

    // --- KEYBOARD ACTIONS ---
    fun sendKeyboardKey(modifiers: Byte, keyCode: Byte) {
        vibrate(20)
        hidManager.sendKeyPress(modifiers, keyCode)
    }

    fun triggerCustomShortcut(shortcut: ShortcutEntity) {
        vibrate(40)
        val codes = shortcut.keyCodes.split(",")
            .mapNotNull { it.trim().toIntOrNull()?.toByte() }
            .toByteArray()

        viewModelScope.launch(Dispatchers.IO) {
            hidManager.sendKeyboardInput(shortcut.modifiers.toByte(), codes)
            kotlinx.coroutines.delay(40)
            hidManager.sendKeyboardInput(0, byteArrayOf(0)) // Release
        }
    }

    // --- CONSUMER CONTROL / MEDIA KEYS ---
    fun sendMediaAction(actionBit: Byte) {
        vibrate(30)
        hidManager.sendConsumerInput(actionBit)
    }

    // --- VIBRATION HELPER ---
    fun vibrate(durationMs: Long) {
        if (!settingsState.value.vibrationFeedback) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoReconnectJob?.cancel()
    }

    companion object {
        private const val TAG = "AirMouseViewModel"
    }
}
