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
import com.example.bluetooth.HidDeviceManager
import com.example.bluetooth.HidKeyMapper
import com.example.bluetooth.LED_CAPS_LOCK
import com.example.bluetooth.LED_NUM_LOCK
import com.example.bluetooth.LED_SCROLL_LOCK
import com.example.bluetooth.ScannedDevice
import com.example.bluetooth.getSafeName
import com.example.data.AppDatabase
import com.example.service.AirMouseService
import com.example.data.ConnectionHistoryEntity
import com.example.data.ConnectionHistoryRepository
import com.example.data.DeviceSettingsEntity
import com.example.data.GestureEntity
import com.example.data.SettingsEntity
import com.example.data.SettingsRepository
import com.example.data.ShortcutEntity
import com.example.sensor.BatteryMonitor
import com.example.sensor.MotionSensorManager
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AirMouseViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val db = AppDatabase.getDatabase(application, viewModelScope)

    val hidManager: HidDeviceManager = BluetoothHidManager.getInstance(application)
    private val sensorManager = MotionSensorManager(application)

    // Sub-ViewModels for settings and gesture CRUD. Screens can access these
    // via [settingsViewModel] and [gestureViewModel] instead of calling the
    // corresponding methods on AirMouseViewModel directly.
    val settingsViewModel = SettingsViewModel(application)
    val gestureViewModel = GestureViewModel(application)

    // SettingsRepository is still needed directly for auto-reconnect
    // SharedPreferences and device-profile seeding on first connect.
    // ConnectionHistoryRepo handles connection history lifecycle operations.
    private val settingsRepo = SettingsRepository(db.airMouseDao(), application, viewModelScope)
    private val connectionHistoryRepo = ConnectionHistoryRepository(db.airMouseDao())

    // Effective settings: global settings with the connected device's pointer
    // overrides applied. Delegates to [SettingsViewModel] and reactively
    // switches when the connected device changes.
    val settingsState: StateFlow<SettingsEntity> =
        hidManager.connectedDevice
            .map { it?.address }
            .distinctUntilChanged()
            .flatMapLatest { address ->
                settingsViewModel.settingsStateForDevice(address)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = SettingsEntity()
            )

    /** Delegates to [GestureViewModel.shortcuts]. */
    val shortcutsState: StateFlow<List<ShortcutEntity>> = gestureViewModel.shortcuts

    val connectionHistory: StateFlow<List<ConnectionHistoryEntity>> = connectionHistoryRepo.recentConnections
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Delegates to [GestureViewModel.gestures]. */
    val gesturesState: StateFlow<List<GestureEntity>> = gestureViewModel.gestures

    // Bluetooth States from native service
    val bluetoothState: StateFlow<Int> = hidManager.connectionState
    val connectedDevice: StateFlow<BluetoothDevice?> = hidManager.connectedDevice
    val targetDevice: StateFlow<BluetoothDevice?> = hidManager.targetDevice
    val isProfileReady: StateFlow<Boolean> = hidManager.isProfileReady
    val isBluetoothPowerOn: StateFlow<Boolean> = hidManager.isBluetoothEnabledFlow

    // Last connection error message (cleared when a new connection attempt starts)
    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    // Host-side keyboard lock indicators, mirrored from the host's HID LED
    // output reports (e.g. pressing Caps Lock on the PC lights the indicator
    // here). Only true while connected to a host that sends them.
    val capsLockState: StateFlow<Boolean> = hidManager.hostLeds
        .map { it and LED_CAPS_LOCK != 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val numLockState: StateFlow<Boolean> = hidManager.hostLeds
        .map { it and LED_NUM_LOCK != 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val scrollLockState: StateFlow<Boolean> = hidManager.hostLeds
        .map { it and LED_SCROLL_LOCK != 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Gamepad rumble intensity (0-255) from the host's force-feedback output
    // reports; 0 when the motors are off or no host is connected.
    val gamepadRumbleState: StateFlow<Int> = hidManager.gamepadRumble

    // Measured signal strength (dBm) of the connected host, captured from
    // discovery scans; null when unknown or disconnected.
    val connectedRssi: StateFlow<Int?> = hidManager.connectedRssi

    // All saved per-device pointer profiles (address -> profile), used by the
    // Device Specific Settings screen to edit any host's saved profile.
    /** Delegates to [SettingsViewModel.deviceProfiles]. */
    val deviceProfiles: StateFlow<List<DeviceSettingsEntity>> = settingsViewModel.deviceProfiles

    // Dynamic paired devices list
    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    // Nearby devices discovered via classic Bluetooth scanning
    val scannedDevices: StateFlow<List<ScannedDevice>> = hidManager.scannedDevices
    val isScanning: StateFlow<Boolean> = hidManager.isScanning

    // Battery level (live, not one-shot). The monitor feeds the BLE battery
    // service so the host sees the phone battery; the foreground service runs
    // its own monitor for the notification, so no UI state is exposed here.
    private val batteryMonitor = BatteryMonitor(application)

    private val _autoReconnectEnabled = MutableStateFlow(settingsRepo.autoReconnectEnabled)
    val autoReconnectEnabled: StateFlow<Boolean> = _autoReconnectEnabled.asStateFlow()

    private val _lastConnectedDeviceAddress = MutableStateFlow(settingsRepo.lastConnectedDeviceAddress)
    val lastConnectedDeviceAddress: StateFlow<String?> = _lastConnectedDeviceAddress.asStateFlow()

    // Auto-reconnect job to prevent multiple concurrent reconnect attempts
    private var autoReconnectJob: Job? = null

    // Debounce state for auto-reconnect
    private var lastAutoReconnectTime = 0L
    private val autoReconnectDebounceMs = 3000L

    fun setAutoReconnectEnabled(enabled: Boolean) {
        settingsRepo.setAutoReconnectEnabled(enabled)
        _autoReconnectEnabled.value = enabled
    }

    fun setLastConnectedDeviceAddress(address: String?) {
        settingsRepo.setLastConnectedDeviceAddress(address)
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
        // One-time cleanup of duplicate connection-history rows from before
        // the per-device upsert (keeps only the latest row per host).
        viewModelScope.launch(Dispatchers.IO) {
            connectionHistoryRepo.dedupeConnectionHistory()
        }

        // Start live battery monitoring
        batteryMonitor.start()

        // Push battery level to BLE GATT Battery Service (for host Bluetooth settings)
        viewModelScope.launch {
            batteryMonitor.batteryLevel.collect { level ->
                hidManager.updateBleBatteryLevel(level)
            }
        }

        // Mirror host gamepad force feedback (rumble) as phone vibration while
        // a game keeps sending rumble reports.
        viewModelScope.launch {
            hidManager.gamepadRumble.collect { intensity ->
                handleGamepadRumble(intensity)
            }
        }

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
                val currentDevice = connectedDevice.value ?: targetDevice.value ?: lastDevice
                val deviceName = currentDevice?.getSafeName() ?: "Device"

                if (lastState != null && lastState != state) {
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            currentDevice?.let { lastDevice = it }
                            Toast.makeText(application, "Connected to $deviceName", Toast.LENGTH_SHORT).show()
                            currentDevice?.let {
                                setLastConnectedDeviceAddress(it.address)
                                // Save to connection history (one row per
                                // device — reconnecting updates the timestamp)
                                viewModelScope.launch(Dispatchers.IO) {
                                    connectionHistoryRepo.upsertConnection(
                                        ConnectionHistoryEntity(
                                            deviceName = deviceName,
                                            deviceAddress = it.address
                                        )
                                    )
                                    // First connection to this device: seed its
                                    // pointer profile from the current global
                                    // settings so per-device settings start
                                    // seamlessly instead of at defaults.
                                    if (settingsRepo.getDeviceSettingsDirect(it.address) == null) {
                                        val global = settingsRepo.getSettingsDirect() ?: SettingsEntity()
                                        settingsRepo.updateDeviceSettings(
                                            DeviceSettingsEntity(
                                                deviceAddress = it.address,
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
                            // Start foreground service to maintain connection safely
                            try {
                                AirMouseService.startService(app, deviceName)
                            } catch (e: Exception) {
                                Log.e("AirMouseViewModel", "Failed to start AirMouseService", e)
                            }
                        }
                        BluetoothProfile.STATE_CONNECTING -> {
                            _connectionError.value = null
                            currentDevice?.let { lastDevice = it }
                            Toast.makeText(application, "Connecting to $deviceName...", Toast.LENGTH_SHORT).show()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            if (lastState == BluetoothProfile.STATE_CONNECTING) {
                                Toast.makeText(application, "Failed to connect to $deviceName", Toast.LENGTH_LONG).show()
                            } else if (lastState == BluetoothProfile.STATE_CONNECTED) {
                                Toast.makeText(application, "Disconnected from $deviceName", Toast.LENGTH_SHORT).show()
                                // Stop foreground service safely
                                try {
                                    AirMouseService.stopService(app)
                                } catch (e: Exception) {
                                    Log.e("AirMouseViewModel", "Failed to stop AirMouseService", e)
                                }
                            }
                        }
                    }
                }
                lastState = state
            }
        }

        // Observe bondedDevices flow from hidManager to keep pairedDevices in sync
        viewModelScope.launch {
            hidManager.bondedDevices.collect {
                refreshPairedDevices()
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
        val devices = hidManager.getBondedDevices()
        val currentDevice = connectedDevice.value
        val lastAddress = lastConnectedDeviceAddress.value

        // Sort: connected first, then last used, then others
        val sorted = devices.sortedWith(compareByDescending<BluetoothDevice> {
            it.address == currentDevice?.address
        }.thenByDescending {
            it.address == lastAddress
        })

        _pairedDevices.value = sorted
    }

    // --- DEVICE DISCOVERY ---
    fun startScanning() {
        hidManager.startScanning()
    }

    fun stopScanning() {
        hidManager.stopScanning()
    }

    fun bondScannedDevice(device: BluetoothDevice) {
        hidManager.bondDevice(device)
    }

    // Reconnects to a bonded device by its MAC address (used by connection history)
    fun connectToDeviceByAddress(address: String?) {
        if (address.isNullOrBlank()) return
        val device = hidManager.getBondedDevices().find { it.address == address } ?: return
        connectToDevice(device)
    }

    fun connectToDevice(device: BluetoothDevice) {
        vibrate(50)
        val currentTarget = targetDevice.value
        val currentState = bluetoothState.value

        if (currentState == BluetoothProfile.STATE_CONNECTING) {
            if (currentTarget?.address == device.address) {
                // Tapping same target device while connecting -> Cancel
                cancelConnection()
                Toast.makeText(app, "Connection cancelled", Toast.LENGTH_SHORT).show()
                return
            } else {
                // Tapping a different device while connecting -> One-tap switch
                cancelConnection()
                Toast.makeText(app, "Switching connection to ${device.getSafeName()}...", Toast.LENGTH_SHORT).show()
            }
        }

        hidManager.connectHost(device)
    }

    fun cancelConnection() {
        vibrate(50)
        hidManager.cancelConnection()
        try {
            AirMouseService.stopService(app)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop AirMouseService on cancel", e)
        }
    }

    fun disconnectDevice() {
        vibrate(50)
        cancelConnection()
    }


    fun clearConnectionHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            connectionHistoryRepo.clear()
        }
    }

    fun clearConnectionError() {
        _connectionError.value = null
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

    // Dynamic database update wrapper. While a device is connected the pointer
    // fields are routed to that device's profile (per-device settings), while
    // app-level fields (theme, vibration, keep-awake, ...) always go to the
    // global row. The global pointer fields are left untouched while connected
    // so switching devices never leaks one device's settings into another.
    /** Delegates to [SettingsViewModel.updateSettings]. */
    fun updateSettings(newSettings: SettingsEntity) {
        settingsViewModel.updateSettings(newSettings, hidManager.connectedDevice.value?.address)
    }

    /** Delegates to [SettingsViewModel.updateDeviceSettingsForDevice]. */
    fun updateDeviceSettingsForDevice(deviceSettings: DeviceSettingsEntity) {
        settingsViewModel.updateDeviceSettingsForDevice(deviceSettings)
    }

    /** Delegates to [SettingsViewModel.resetDeviceSettingsForDevice]. */
    fun resetDeviceSettingsForDevice(address: String) {
        settingsViewModel.resetDeviceSettingsForDevice(address)
    }

    /** Forget the connected device's pointer profile and fall back to global settings. */
    fun resetDeviceSettings() {
        val address = hidManager.connectedDevice.value?.address ?: return
        resetDeviceSettingsForDevice(address)
    }

    /** Delegates to [GestureViewModel.addShortcut]. */
    fun addCustomShortcut(name: String, modifiers: Int, keyCodes: String) {
        gestureViewModel.addShortcut(name, modifiers, keyCodes)
    }

    /** Delegates to [GestureViewModel.deleteShortcut]. */
    fun deleteShortcut(id: Int) {
        gestureViewModel.deleteShortcut(id)
    }

    // --- SENSOR TRIGGER ---
    fun startAirMouse(buttonsState: Byte = 0) {
        sensorManager.updateSettings(settingsState.value)
        sensorManager.start(buttonsState)
        AirMouseService.isAirMouseActive = true
        com.example.widget.AirMouseWidgetReceiver.updateAllWidgets(app)
    }

    fun stopAirMouse() {
        sensorManager.stop()
        AirMouseService.isAirMouseActive = false
        // Send stop intent to service to stop service's background sensors
        val serviceIntent = Intent(app, AirMouseService::class.java).apply {
            action = AirMouseService.ACTION_STOP_AIR_MOUSE
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(serviceIntent)
            } else {
                app.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop service sensors from ViewModel: ${e.message}")
        }
        com.example.widget.AirMouseWidgetReceiver.updateAllWidgets(app)
    }

    // Called when app goes to background - pause sensors but keep Bluetooth alive
    fun onAppBackground() {
        // Stop the local listener FIRST, then hand off to the service. The old
        // order (start service, then stop local) left a window where both
        // MotionSensorManagers had a live gyro listener, doubling every report.
        sensorManager.stop()
        if (AirMouseService.isAirMouseActive) {
            // Transfer sensors to service so they keep running in foreground service
            val serviceIntent = Intent(app, AirMouseService::class.java).apply {
                action = AirMouseService.ACTION_START_AIR_MOUSE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(serviceIntent)
                } else {
                    app.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to transfer sensors to service on background: ${e.message}")
            }
        }
        Log.d(TAG, "App backgrounded - sensors paused in ViewModel, maintained in service if active")
    }

    // Called when app comes to foreground - resume sensors if they were active
    fun onAppForeground() {
        Log.d(TAG, "App foregrounded - ready to resume sensors")
        if (AirMouseService.isAirMouseActive) {
            // Stop service sensors since we are in the foreground now and ViewModel will handle it
            val serviceIntent = Intent(app, AirMouseService::class.java).apply {
                action = AirMouseService.ACTION_STOP_AIR_MOUSE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(serviceIntent)
                } else {
                    app.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service sensors on foreground: ${e.message}")
            }
            // Ensure ViewModel sensors are started
            sensorManager.updateSettings(settingsState.value)
            sensorManager.start(0)
        }
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

    val currentRoute = MutableStateFlow<String?>(null)

    fun sendCtrlScroll(scrollTicks: Byte) {
        // Hold Left Ctrl (Modifier 0x01)
        hidManager.sendKeyboardInput(0x01, byteArrayOf())
        // Send Mouse Scroll tick
        hidManager.sendMouseInput(0, 0, 0, scrollTicks)
        // Release Left Ctrl
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(40)
            hidManager.sendKeyboardInput(0, byteArrayOf())
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

    // Sends mouse wheel ticks from the touchpad (two-finger scroll).
    // Negative ticks scroll down, positive scroll up. Applies the user's
    // scroll-speed setting, consistent with the dedicated scroll bar.
    fun sendScrollTicks(ticks: Int) {
        if (ticks == 0) return
        val speed = settingsState.value.scrollSpeed
        val sign = if (ticks > 0) 1 else -1
        repeat(kotlin.math.abs(ticks)) {
            val scaled = sign * speed
            val finalTick = if (scaled > 0) {
                maxOf(1, scaled.toInt())
            } else {
                minOf(-1, scaled.toInt())
            }.toByte()
            hidManager.sendMouseInput(0, 0, 0, finalTick)
        }
    }

    // Sends horizontal mouse wheel ticks (two-finger horizontal scroll).
    // Positive ticks scroll right, negative scroll left. Applies the user's
    // scroll-speed setting like the vertical path.
    fun sendHScrollTicks(ticks: Int) {
        if (ticks == 0) return
        val speed = settingsState.value.scrollSpeed
        val sign = if (ticks > 0) 1 else -1
        repeat(kotlin.math.abs(ticks)) {
            val scaled = sign * speed
            val finalTick = if (scaled > 0) {
                maxOf(1, scaled.toInt())
            } else {
                minOf(-1, scaled.toInt())
            }.toByte()
            hidManager.sendMouseInput(0, 0, 0, 0, finalTick)
        }
    }

    // --- REAL GAMEPAD (HID Report ID 4) ---
    // Tracks the current button mask + hat switch so multi-button holds and
    // diagonal hat directions are sent as complete, consistent reports.
    private val _gamepadButtons = MutableStateFlow(0)
    private val _gamepadHat = MutableStateFlow(8.toByte())

    fun gamepadButton(buttonBit: Int, down: Boolean) {
        val next = if (down) {
            _gamepadButtons.value or buttonBit
        } else {
            _gamepadButtons.value and buttonBit.inv()
        }
        _gamepadButtons.value = next
        // Haptic feedback on press, mirroring the keyboard-mode path
        if (down) vibrate(20)
        sendGamepadReport(next, _gamepadHat.value)
    }

    fun gamepadHat(hat: Byte) {
        val changed = hat != _gamepadHat.value
        _gamepadHat.value = hat
        // Buzz when a direction is pressed (not when returning to center)
        if (changed && hat != 8.toByte()) vibrate(15)
        sendGamepadReport(_gamepadButtons.value, hat)
    }

    private fun sendGamepadReport(buttons: Int, hat: Byte) {
        hidManager.sendGamepadInput(buttons, hat, 0, 0)
    }

    fun sendMouseDown(button: Byte) {
        _currentMouseButtons.value = button
        hidManager.sendMouseInput(button, 0, 0, 0)
    }

    fun sendMouseUp() {
        _currentMouseButtons.value = 0
        hidManager.sendMouseInput(0, 0, 0, 0)
    }

    fun sendText(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            text.forEach { char ->
                val mapped = HidKeyMapper.map(char) ?: return@forEach
                hidManager.sendKeyPress(mapped.first, mapped.second)
                kotlinx.coroutines.delay(15)
            }
        }
    }

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

    // --- GESTURE ACTIONS ---
    /** Delegates to [GestureViewModel.saveGesture]. */
    fun saveGesture(gesture: GestureEntity) {
        gestureViewModel.saveGesture(gesture)
    }

    /** Delegates to [GestureViewModel.deleteGesture]. */
    fun deleteGesture(id: Int) {
        gestureViewModel.deleteGesture(id)
    }

    fun executeGestureAction(action: String) {
        vibrate(30)
        when (action) {
            // Keyboard actions
            "copy" -> sendKeyboardKey(0x01, 0x06.toByte()) // Ctrl+C
            "paste" -> sendKeyboardKey(0x01, 0x19.toByte()) // Ctrl+V
            "undo" -> sendKeyboardKey(0x01, 0x1D.toByte()) // Ctrl+Z
            "redo" -> sendKeyboardKey(0x01, 0x1C.toByte()) // Ctrl+Y
            "select_all" -> sendKeyboardKey(0x01, 0x04.toByte()) // Ctrl+A
            "save" -> sendKeyboardKey(0x01, 0x16.toByte()) // Ctrl+S
            "close" -> sendKeyboardKey(0x01, 0x1A.toByte()) // Ctrl+W
            "tab" -> sendKeyboardKey(0, 0x2B.toByte())
            "enter" -> sendKeyboardKey(0, 0x28.toByte())
            "esc" -> sendKeyboardKey(0, 0x29.toByte())
            "delete" -> sendKeyboardKey(0, 0x4C.toByte()) // Delete (Forward) Key
            "backspace" -> sendKeyboardKey(0, 0x2A.toByte())

            // Media actions
            "play_pause" -> sendMediaAction(0x08)
            "next_track" -> sendMediaAction(0x10)
            "prev_track" -> sendMediaAction(0x20)
            "vol_up" -> sendMediaAction(0x01)
            "vol_down" -> sendMediaAction(0x02)
            "mute" -> sendMediaAction(0x04)

            // Mouse actions
            "left_click" -> sendMouseClick(1)
            "right_click" -> sendMouseClick(2)
            "middle_click" -> sendMouseClick(4)
            // Route through sendScrollTicks so the user's scroll-speed setting
            // applies, matching the touchpad scroll path.
            "scroll_up" -> sendScrollTicks(1)
            "scroll_down" -> sendScrollTicks(-1)

            // Presentation actions
            "next_slide" -> sendKeyboardKey(0, 0x4E.toByte()) // Page Down
            "prev_slide" -> sendKeyboardKey(0, 0x4B.toByte()) // Page Up
            "fullscreen" -> sendKeyboardKey(0, 0x3E.toByte()) // F5
            "black_screen" -> sendKeyboardKey(0, 0x05.toByte()) // B key
        }
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

    // True while the phone is vibrating in response to host rumble reports;
    // guards against re-triggering on every report the game sends.
    private var rumbleActive = false

    /**
     * Starts a repeating vibration while the host keeps sending nonzero rumble
     * reports and stops it when the motors go quiet. Respects the vibration
     * feedback setting like every other haptic in the app.
     */
    private fun handleGamepadRumble(intensity: Int) {
        if (intensity <= 0) {
            if (rumbleActive) {
                rumbleActive = false
                try {
                    vibrator.cancel()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to cancel rumble vibration", e)
                }
            }
            return
        }
        if (rumbleActive || !settingsState.value.vibrationFeedback) return
        rumbleActive = true
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Alternate off/on pulses until the host stops rumbling
                val amplitude = intensity.coerceIn(1, 255)
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 120),
                        intArrayOf(0, amplitude),
                        0
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(120)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start rumble vibration", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoReconnectJob?.cancel()
        batteryMonitor.stop()
        try {
            vibrator.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel vibration on clear", e)
        }
        rumbleActive = false
    }

    /** Test hook: mirror the framework's onCleared() cleanup from unit tests. */
    @androidx.annotation.VisibleForTesting
    fun clearForTest() {
        viewModelScope.cancel()
        onCleared()
    }

    companion object {
        private const val TAG = "AirMouseViewModel"
    }
}
