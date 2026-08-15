package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

data class ScannedDevice(
    val device: BluetoothDevice,
    val rssi: Int
)

// Host LED output report bits (Report ID 1, Usage Page 0x08 LEDs). The host
// sends these via onSetReport/onInterruptData to mirror its lock states.
const val LED_NUM_LOCK = 0x01
const val LED_CAPS_LOCK = 0x02
const val LED_SCROLL_LOCK = 0x04

/**
 * Parses the keyboard LED output report (Report ID 1) into the LED bitmask.
 * The report is a single byte: bits 0-4 are Num/Caps/Scroll/Compose/Kana.
 * Hosts vary between sending a 1-byte report and a full 8-byte keyboard
 * report, so take the first byte when present.
 */
fun parseLedState(data: ByteArray?): Int {
    return if (data == null || data.isEmpty()) 0 else (data[0].toInt() and 0xFF)
}

/**
 * Parses the gamepad force-feedback output report (Report ID 4) into a
 * rumble intensity 0-255. The report carries two motor bytes (strong, weak);
 * the strongest of the two drives the intensity.
 */
fun parseRumbleIntensity(data: ByteArray?): Int {
    if (data == null || data.isEmpty()) return 0
    return when {
        data.size >= 2 -> maxOf(data[0].toInt() and 0xFF, data[1].toInt() and 0xFF)
        else -> data[0].toInt() and 0xFF
    }
}

@SuppressLint("MissingPermission")
fun BluetoothDevice.getSafeName(): String {
    return try {
        this.name ?: "Unknown Device"
    } catch (e: SecurityException) {
        "Device"
    }
}

@SuppressLint("MissingPermission")
fun BluetoothDevice.isComputer(): Boolean {
    return try {
        val bc = this.bluetoothClass
        bc != null && bc.majorDeviceClass == 256
    } catch (e: SecurityException) {
        false
    }
}

@SuppressLint("MissingPermission")
fun BluetoothDevice.isHidCompatibleHost(): Boolean {
    return try {
        val name = this.name
        if (name.isNullOrBlank()) {
            return false
        }

        val bluetoothClass = this.bluetoothClass ?: return true // Safe fallback
        val majorClass = bluetoothClass.majorDeviceClass
        val deviceClass = bluetoothClass.deviceClass

        // Filter out known non-HID major classes
        // 1792 = WEARABLE, 2304 = HEALTH, 2048 = TOY, 1536 = IMAGING
        when (majorClass) {
            1792, 2304, 2048, 1536 -> return false
        }

        if (majorClass == 1024) { // AUDIO_VIDEO
            // Filter out headsets, speakers, headphones, portable/car/hi-fi audio
            val nonHidAudioClasses = setOf(
                1028, // WEARABLE_HEADSET
                1032, // HANDSFREE
                1040, // MICROPHONE
                1044, // LOUDSPEAKER
                1048, // HEADPHONES
                1052, // PORTABLE_AUDIO
                1056, // CAR_AUDIO
                1064  // HIFI_AUDIO
            )
            if (deviceClass in nonHidAudioClasses) {
                return false
            }

            // Filter out common audio device names in case of weird classes
            val nameLower = name.lowercase()
            if (nameLower.contains("speaker") || nameLower.contains("buds") || nameLower.contains("earphone") || 
                nameLower.contains("headphone") || nameLower.contains("headset") || nameLower.contains("watch") || 
                nameLower.contains("fitbit") || nameLower.contains("band")) {
                return false
            }
        }
        true
    } catch (e: SecurityException) {
        false
    }
}

@SuppressLint("MissingPermission")
class BluetoothHidManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var hidDeviceProfile: BluetoothHidDevice? = null
    private var isRegistered = false
    private val bleBatteryService = BleBatteryService(context)
    private var lastBatteryLevel = 0

    // Single executor for all scheduled tasks (prevents executor leak)
    private val scheduledExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    // Connection state guards
    private var isConnecting = false
    private var lastConnectAttemptTime = 0L
    private val connectCooldownMs = 2000L // Minimum time between connect attempts

    // Pending connection (for registration-complete flow)
    private var pendingConnectionDevice: BluetoothDevice? = null

    // State flows for UI mapping
    private val _connectionState = MutableStateFlow(BluetoothProfile.STATE_DISCONNECTED)
    val connectionState: StateFlow<Int> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    // Measured signal strength (dBm) of the connected host, captured from
    // discovery scans (classic Bluetooth doesn't expose a live RSSI of a
    // connected device). Null until the host is seen in a scan.
    private val _connectedRssi = MutableStateFlow<Int?>(null)
    val connectedRssi: StateFlow<Int?> = _connectedRssi.asStateFlow()

    private val _targetDevice = MutableStateFlow<BluetoothDevice?>(null)
    val targetDevice: StateFlow<BluetoothDevice?> = _targetDevice.asStateFlow()

    private var connectTimeoutJob: java.util.concurrent.ScheduledFuture<*>? = null

    private val _isProfileReady = MutableStateFlow(false)
    val isProfileReady: StateFlow<Boolean> = _isProfileReady.asStateFlow()

    private val _isAppRegistered = MutableStateFlow(false)
    val isAppRegistered: StateFlow<Boolean> = _isAppRegistered.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled ?: false)
    val isBluetoothEnabledFlow: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    // Host-side keyboard lock states (LED output report, Report ID 1).
    // Bitmask: LED_NUM_LOCK | LED_CAPS_LOCK | LED_SCROLL_LOCK
    private val _hostLeds = MutableStateFlow(0)
    val hostLeds: StateFlow<Int> = _hostLeds.asStateFlow()

    // Gamepad force feedback from the host (output report, Report ID 4).
    // 0 = motors off, 1-255 = intensity of the strongest motor.
    private val _gamepadRumble = MutableStateFlow(0)
    val gamepadRumble: StateFlow<Int> = _gamepadRumble.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _bondedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val bondedDevices: StateFlow<List<BluetoothDevice>> = _bondedDevices.asStateFlow()

    private var discoveryReceiver: BroadcastReceiver? = null

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
                    if (state == BluetoothAdapter.STATE_ON) {
                        initializeHidProfile()
                        _bondedDevices.value = getBondedDevices()
                    } else if (state == BluetoothAdapter.STATE_OFF) {
                        // Reset state when Bluetooth is turned off
                        _isProfileReady.value = false
                        _isAppRegistered.value = false
                        _connectionState.value = BluetoothProfile.STATE_DISCONNECTED
                        _connectedDevice.value = null
                        _connectedRssi.value = null
                        _targetDevice.value = null
                        connectTimeoutJob?.cancel(true)
                        isConnecting = false
                        isRegistered = false
                        _isScanning.value = false
                        _scannedDevices.value = emptyList()
                        _bondedDevices.value = emptyList()
                        _hostLeds.value = 0
                        _gamepadRumble.value = 0
                        // The BLE GATT server is invalidated by the stack while
                        // Bluetooth is off; reset it so the next connect rebuilds it.
                        bleBatteryService.onBluetoothTurnedOff()
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        if (state == BluetoothDevice.BOND_BONDED) {
                            // Remove from scanned list since it is now paired
                            _scannedDevices.value = _scannedDevices.value.filter { it.device.address != device.address }
                        }
                        // Always update bonded devices on bond state changes
                        _bondedDevices.value = getBondedDevices()
                    }
                }
            }
        }
    }

    init {
        initializeHidProfile()
        _bondedDevices.value = getBondedDevices()
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }
            androidx.core.content.ContextCompat.registerReceiver(
                appContext,
                bluetoothReceiver,
                filter,
                // Protected system broadcasts are delivered regardless of the
                // export flag; NOT_EXPORTED blocks other apps from spoofing
                // Bluetooth state changes.
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error registering bluetoothReceiver", e)
        }
    }

    companion object {
        private const val TAG = "BluetoothHidManager"

        @Volatile
        private var INSTANCE: BluetoothHidManager? = null

        fun getInstance(context: Context): BluetoothHidManager {
            return INSTANCE ?: synchronized(this) {
                val instance = BluetoothHidManager(context)
                INSTANCE = instance
                instance
            }
        }

        // Combined Keyboard, Mouse, and Consumer Control HID Descriptor
        // Windows-compatible with standard HID Usage Tables
        private val HID_DESCRIPTOR = byteArrayOf(
            // =====================================================================
            // KEYBOARD (Report ID 1) - Standard Boot Keyboard
            // =====================================================================
            0x05.toByte(), 0x01.toByte(),       // USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x06.toByte(),       // USAGE (Keyboard)
            0xa1.toByte(), 0x01.toByte(),       // COLLECTION (Application)
            0x85.toByte(), 0x01.toByte(),       //   REPORT_ID (1)
            0x05.toByte(), 0x07.toByte(),       //   USAGE_PAGE (Keyboard)
            0x19.toByte(), 0xe0.toByte(),       //   USAGE_MINIMUM (Keyboard LeftControl)
            0x29.toByte(), 0xe7.toByte(),       //   USAGE_MAXIMUM (Keyboard Right GUI)
            0x15.toByte(), 0x00.toByte(),       //   LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x01.toByte(),       //   LOGICAL_MAXIMUM (1)
            0x75.toByte(), 0x01.toByte(),       //   REPORT_SIZE (1)
            0x95.toByte(), 0x08.toByte(),       //   REPORT_COUNT (8)
            0x81.toByte(), 0x02.toByte(),       //   INPUT (Data,Var,Abs) - Modifiers

            0x95.toByte(), 0x01.toByte(),       //   REPORT_COUNT (1)
            0x75.toByte(), 0x08.toByte(),       //   REPORT_SIZE (8)
            0x81.toByte(), 0x03.toByte(),       //   INPUT (Cnst,Var,Abs) - Reserved

            0x95.toByte(), 0x05.toByte(),       //   REPORT_COUNT (5)
            0x75.toByte(), 0x01.toByte(),       //   REPORT_SIZE (1)
            0x05.toByte(), 0x08.toByte(),       //   USAGE_PAGE (LEDs)
            0x19.toByte(), 0x01.toByte(),       //   USAGE_MINIMUM (Num Lock)
            0x29.toByte(), 0x05.toByte(),       //   USAGE_MAXIMUM (Kana)
            0x91.toByte(), 0x02.toByte(),       //   OUTPUT (Data,Var,Abs) - LEDs

            0x95.toByte(), 0x01.toByte(),       //   REPORT_COUNT (1)
            0x75.toByte(), 0x03.toByte(),       //   REPORT_SIZE (3)
            0x91.toByte(), 0x03.toByte(),       //   OUTPUT (Cnst,Var,Abs) - LED Padding

            0x95.toByte(), 0x06.toByte(),       //   REPORT_COUNT (6)
            0x75.toByte(), 0x08.toByte(),       //   REPORT_SIZE (8)
            0x15.toByte(), 0x00.toByte(),       //   LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x65.toByte(),       //   LOGICAL_MAXIMUM (101)
            0x05.toByte(), 0x07.toByte(),       //   USAGE_PAGE (Keyboard)
            0x19.toByte(), 0x00.toByte(),       //   USAGE_MINIMUM (Reserved)
            0x29.toByte(), 0x65.toByte(),       //   USAGE_MAXIMUM (Keyboard Application)
            0x81.toByte(), 0x00.toByte(),       //   INPUT (Data,Ary,Abs) - Key codes
            0xc0.toByte(),                      // END_COLLECTION

            // =====================================================================
            // MOUSE (Report ID 2) - Standard Boot Mouse
            // =====================================================================
            0x05.toByte(), 0x01.toByte(),       // USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x02.toByte(),       // USAGE (Mouse)
            0xa1.toByte(), 0x01.toByte(),       // COLLECTION (Application)
            0x09.toByte(), 0x01.toByte(),       //   USAGE (Pointer)
            0xa1.toByte(), 0x00.toByte(),       //   COLLECTION (Physical)
            0x85.toByte(), 0x02.toByte(),       //     REPORT_ID (2)

            // Buttons
            0x05.toByte(), 0x09.toByte(),       //     USAGE_PAGE (Button)
            0x19.toByte(), 0x01.toByte(),       //     USAGE_MINIMUM (Button 1 - Left)
            0x29.toByte(), 0x05.toByte(),       //     USAGE_MAXIMUM (Button 5)
            0x15.toByte(), 0x00.toByte(),       //     LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x01.toByte(),       //     LOGICAL_MAXIMUM (1)
            0x75.toByte(), 0x01.toByte(),       //     REPORT_SIZE (1)
            0x95.toByte(), 0x05.toByte(),       //     REPORT_COUNT (5)
            0x81.toByte(), 0x02.toByte(),       //     INPUT (Data,Var,Abs)

            // Padding
            0x95.toByte(), 0x01.toByte(),       //     REPORT_COUNT (1)
            0x75.toByte(), 0x03.toByte(),       //     REPORT_SIZE (3)
            0x81.toByte(), 0x03.toByte(),       //     INPUT (Cnst,Var,Abs)

            // Movement (X, Y)
            0x05.toByte(), 0x01.toByte(),       //     USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x30.toByte(),       //     USAGE (X)
            0x09.toByte(), 0x31.toByte(),       //     USAGE (Y)
            0x15.toByte(), 0x81.toByte(),       //     LOGICAL_MINIMUM (-127)
            0x25.toByte(), 0x7f.toByte(),       //     LOGICAL_MAXIMUM (127)
            0x75.toByte(), 0x08.toByte(),       //     REPORT_SIZE (8)
            0x95.toByte(), 0x02.toByte(),       //     REPORT_COUNT (2)
            0x81.toByte(), 0x06.toByte(),       //     INPUT (Data,Var,Rel)

            // Wheel scroll (vertical) + horizontal wheel. Two Wheel usages in one
            // report is the standard way Windows exposes horizontal scrolling
            // (first usage = vertical, second = horizontal).
            0x09.toByte(), 0x38.toByte(),       //     USAGE (Wheel)
            0x09.toByte(), 0x38.toByte(),       //     USAGE (Wheel) - horizontal
            0x15.toByte(), 0x81.toByte(),       //     LOGICAL_MINIMUM (-127)
            0x25.toByte(), 0x7f.toByte(),       //     LOGICAL_MAXIMUM (127)
            0x75.toByte(), 0x08.toByte(),       //     REPORT_SIZE (8)
            0x95.toByte(), 0x02.toByte(),       //     REPORT_COUNT (2)
            0x81.toByte(), 0x06.toByte(),       //     INPUT (Data,Var,Rel)

            0xc0.toByte(),                      //   END_COLLECTION
            0xc0.toByte(),                      // END_COLLECTION

            // =====================================================================
            // GAMEPAD (Report ID 4) - Real HID game controller (joystick axes,
            // hat switch, 12 buttons). Recognized by DirectInput games and
            // emulators; keyboard mode remains available for XInput-only games.
            // =====================================================================
            0x05.toByte(), 0x01.toByte(),       // USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x05.toByte(),       // USAGE (Game Pad)
            0xa1.toByte(), 0x01.toByte(),       // COLLECTION (Application)
            0x85.toByte(), 0x04.toByte(),       //   REPORT_ID (4)

            // Left stick X, Y + triggers Z, Rz (8-bit signed each)
            0x09.toByte(), 0x30.toByte(),       //   USAGE (X)
            0x09.toByte(), 0x31.toByte(),       //   USAGE (Y)
            0x09.toByte(), 0x32.toByte(),       //   USAGE (Z)
            0x09.toByte(), 0x35.toByte(),       //   USAGE (Rz)
            0x15.toByte(), 0x81.toByte(),       //   LOGICAL_MINIMUM (-127)
            0x25.toByte(), 0x7f.toByte(),       //   LOGICAL_MAXIMUM (127)
            0x75.toByte(), 0x08.toByte(),       //   REPORT_SIZE (8)
            0x95.toByte(), 0x04.toByte(),       //   REPORT_COUNT (4)
            0x81.toByte(), 0x02.toByte(),       //   INPUT (Data,Var,Abs)

            // Hat switch: 0-7 directions, 8 = released (null state)
            0x09.toByte(), 0x39.toByte(),       //   USAGE (Hat Switch)
            0x15.toByte(), 0x00.toByte(),       //   LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x07.toByte(),       //   LOGICAL_MAXIMUM (7)
            0x35.toByte(), 0x00.toByte(),       //   PHYSICAL_MINIMUM (0)
            0x46.toByte(), 0x3b.toByte(), 0x01.toByte(), //   PHYSICAL_MAXIMUM (315)
            0x65.toByte(), 0x14.toByte(),       //   UNIT (Degrees)
            0x75.toByte(), 0x08.toByte(),       //   REPORT_SIZE (8)
            0x95.toByte(), 0x01.toByte(),       //   REPORT_COUNT (1)
            0x81.toByte(), 0x42.toByte(),       //   INPUT (Data,Var,Abs,Null)

            // Buttons 1-12
            0x05.toByte(), 0x09.toByte(),       //   USAGE_PAGE (Button)
            0x19.toByte(), 0x01.toByte(),       //   USAGE_MINIMUM (Button 1)
            0x29.toByte(), 0x0c.toByte(),       //   USAGE_MAXIMUM (Button 12)
            0x15.toByte(), 0x00.toByte(),       //   LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x01.toByte(),       //   LOGICAL_MAXIMUM (1)
            0x75.toByte(), 0x01.toByte(),       //   REPORT_SIZE (1)
            0x95.toByte(), 0x0c.toByte(),       //   REPORT_COUNT (12)
            0x81.toByte(), 0x02.toByte(),       //   INPUT (Data,Var,Abs)

            // 4 bits padding to byte-align the 12 buttons
            0x75.toByte(), 0x01.toByte(),       //   REPORT_SIZE (1)
            0x95.toByte(), 0x04.toByte(),       //   REPORT_COUNT (4)
            0x81.toByte(), 0x03.toByte(),       //   INPUT (Cnst,Var,Abs)

            // Force feedback (rumble) output report: 2 bytes (strong motor,
            // weak motor), 0-255 each. DirectInput games and emulators that
            // write to a generic gamepad's output report trigger it; the phone
            // mirrors the intensity as vibration.
            0x06.toByte(), 0x00.toByte(), 0xff.toByte(), //   USAGE_PAGE (Vendor-Defined)
            0x09.toByte(), 0x01.toByte(),       //   USAGE (Vendor-Defined)
            0x15.toByte(), 0x00.toByte(),       //   LOGICAL_MINIMUM (0)
            0x26.toByte(), 0xff.toByte(), 0x00.toByte(), //   LOGICAL_MAXIMUM (255)
            0x75.toByte(), 0x08.toByte(),       //   REPORT_SIZE (8)
            0x95.toByte(), 0x02.toByte(),       //   REPORT_COUNT (2)
            0x91.toByte(), 0x02.toByte(),       //   OUTPUT (Data,Var,Abs)

            0xc0.toByte(),                      // END_COLLECTION

            // =====================================================================
            // CONSUMER CONTROL (Report ID 3) - Media keys using HID Usage Tables
            // =====================================================================
            0x05.toByte(), 0x0c.toByte(),       // USAGE_PAGE (Consumer Devices)
            0x09.toByte(), 0x01.toByte(),       // USAGE (Consumer Control)
            0xa1.toByte(), 0x01.toByte(),       // COLLECTION (Application)
            0x85.toByte(), 0x03.toByte(),       //   REPORT_ID (3)

            // Consumer Control uses 16-bit usage IDs
            0x15.toByte(), 0x00.toByte(),       //   LOGICAL_MINIMUM (0)
            0x26.toByte(), 0xff.toByte(), 0x00.toByte(), //   LOGICAL_MAXIMUM (255)
            0x09.toByte(), 0xe9.toByte(),       //   USAGE (Volume Up)
            0x09.toByte(), 0xea.toByte(),       //   USAGE (Volume Down)
            0x09.toByte(), 0xe2.toByte(),       //   USAGE (Mute)
            0x09.toByte(), 0xcd.toByte(),       //   USAGE (Play/Pause)
            0x09.toByte(), 0xb5.toByte(),       //   USAGE (Scan Next Track)
            0x09.toByte(), 0xb6.toByte(),       //   USAGE (Scan Previous Track)
            0x09.toByte(), 0x30.toByte(),       //   USAGE (Power)
            0x09.toByte(), 0x40.toByte(),       //   USAGE (Menu)

            0x75.toByte(), 0x01.toByte(),       //   REPORT_SIZE (1)
            0x95.toByte(), 0x08.toByte(),       //   REPORT_COUNT (8)
            0x81.toByte(), 0x02.toByte(),       //   INPUT (Data,Var,Abs)
            0xc0.toByte()                       // END_COLLECTION
        )
    }

    fun initializeHidProfile(onReady: () -> Unit = {}) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.e(TAG, "Bluetooth HID Device Profile requires Android 9 (API 28) or higher!")
            return
        }

        val currentProfile = hidDeviceProfile
        if (currentProfile != null) {
            if (!isRegistered) {
                registerApp()
            }
            onReady()
            return
        }

        bluetoothAdapter?.getProfileProxy(appContext, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    Log.d(TAG, "HID Device Profile Connected")
                    hidDeviceProfile = proxy as BluetoothHidDevice
                    _isProfileReady.value = true
                    registerApp()
                    onReady()
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    Log.d(TAG, "HID Device Profile Disconnected")
                    hidDeviceProfile = null
                    _isProfileReady.value = false
                    _isAppRegistered.value = false
                    isRegistered = false
                    // Attempt to reconnect profile after a delay
                    scheduledExecutor.schedule({
                        Log.d(TAG, "Attempting to reconnect HID profile...")
                        initializeHidProfile()
                    }, 1, TimeUnit.SECONDS)
                }
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    // Complete BluetoothHidDevice.Callback implementation
    private val mCallback = @SuppressLint("NewApi") object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            Log.d(TAG, "onAppStatusChanged: registered=$registered, device=${pluggedDevice?.getSafeName()} [${pluggedDevice?.address}]")
            _isAppRegistered.value = registered
            isRegistered = registered

            if (registered) {
                Log.d(TAG, "=== HID APP REGISTERED SUCCESSFULLY ===")
                Log.d(TAG, "Device should now appear as HID in Bluetooth settings")

                // If there's a pending connection, connect now
                pendingConnectionDevice?.let { device ->
                    Log.d(TAG, "Connecting pending device after registration: ${device.getSafeName()}")
                    pendingConnectionDevice = null
                    performConnection(device)
                }
            } else {
                Log.w(TAG, "HID app registration failed or was unregistered")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            val stateStr = when (state) {
                BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
                BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
                BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
                else -> "UNKNOWN($state)"
            }
            Log.d(TAG, "onConnectionStateChanged: device=${device?.getSafeName()} [${device?.address}], state=$stateStr")

            _connectionState.value = state

            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectTimeoutJob?.cancel(true)
                    _targetDevice.value = null
                    _connectedDevice.value = device
                    isConnecting = false
                    Log.d(TAG, "=== CONNECTION ESTABLISHED ===")
                    Log.d(TAG, "Device: ${device?.getSafeName()}")
                    Log.d(TAG, "Address: ${device?.address}")
                    // Start BLE Battery Service safely so host can see phone battery
                    try {
                        bleBatteryService.start()
                        bleBatteryService.updateBatteryLevel(lastBatteryLevel)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting bleBatteryService", e)
                    }
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    isConnecting = true
                    Log.d(TAG, "Connecting to: ${device?.getSafeName()}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectTimeoutJob?.cancel(true)
                    _targetDevice.value = null
                    _connectedDevice.value = null
                    _connectedRssi.value = null
                    isConnecting = false
                    Log.d(TAG, "Disconnected from: ${device?.getSafeName()}")
                    // Stop BLE Battery Service
                    bleBatteryService.stop()
                    // Host lock/rumble state no longer applies
                    _hostLeds.value = 0
                    _gamepadRumble.value = 0
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    Log.d(TAG, "Disconnecting from: ${device?.getSafeName()}")
                }
            }
            try {
                com.example.widget.AirMouseWidgetReceiver.updateAllWidgets(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widget: ${e.message}")
            }
        }

        // Handle GetReport - Windows may request current state
        override fun onGetReport(device: BluetoothDevice?, type: Byte, reportId: Byte, bufferSize: Int) {
            super.onGetReport(device, type, reportId, bufferSize)
            Log.d(TAG, "onGetReport: device=${device?.getSafeName()}, type=$type, reportId=$reportId, bufferSize=$bufferSize")

            // Respond with empty report to satisfy Windows
            val profile = hidDeviceProfile ?: return
            try {
                val data = when (reportId) {
                    1.toByte() -> ByteArray(8) // Keyboard report
                    2.toByte() -> ByteArray(5) // Mouse report (+ horizontal wheel)
                    3.toByte() -> ByteArray(1) // Consumer control
                    4.toByte() -> ByteArray(7) // Gamepad report
                    else -> ByteArray(bufferSize.coerceAtLeast(1))
                }
                val success = profile.replyReport(device, type, reportId, data)
                Log.d(TAG, "onGetReport: Responded via replyReport (success=$success) for reportId=$reportId")
            } catch (e: Exception) {
                Log.e(TAG, "onGetReport: Error sending response", e)
            }
        }

        // Handle SetReport - host sends LED state or gamepad force feedback
        override fun onSetReport(device: BluetoothDevice?, type: Byte, reportId: Byte, data: ByteArray?) {
            super.onSetReport(device, type, reportId, data)
            Log.d(TAG, "onSetReport: device=${device?.getSafeName()}, type=$type, reportId=$reportId, data=${data?.contentToString()}")
            handleHostOutputReport(reportId, data)
            // ACK the report with SUCCESS handshake
            val profile = hidDeviceProfile ?: return
            try {
                profile.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
                Log.d(TAG, "onSetReport: Handshake success ACK sent for reportId=$reportId")
            } catch (e: Exception) {
                Log.e(TAG, "onSetReport: Error sending ACK", e)
            }
        }

        // Handle SetProtocol - Windows negotiates boot protocol
        override fun onSetProtocol(device: BluetoothDevice?, protocol: Byte) {
            super.onSetProtocol(device, protocol)
            Log.d(TAG, "onSetProtocol: device=${device?.getSafeName()}, protocol=$protocol (0=Boot, 1=Report)")
            // Protocol 0 = Boot, Protocol 1 = Report
            // We acknowledge but continue using Report protocol
            // Windows may request Boot protocol during enumeration
        }

        // Handle InterruptData - host may deliver output reports here instead
        // of (or in addition to) onSetReport on some stacks
        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
            super.onInterruptData(device, reportId, data)
            Log.d(TAG, "onInterruptData: device=${device?.getSafeName()}, reportId=$reportId, data=${data?.contentToString()}")
            handleHostOutputReport(reportId, data)
        }

        // Handle VirtualCableUnplug - Windows may unplug virtually
        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            super.onVirtualCableUnplug(device)
            Log.d(TAG, "onVirtualCableUnplug: device=${device?.getSafeName()}")
            // Tear down everything a real disconnect would: the pending target,
            // the connect timeout, and the BLE battery server (kept running on
            // the old path, so the phone kept advertising battery after Windows
            // unplugged the virtual cable).
            connectTimeoutJob?.cancel(true)
            _targetDevice.value = null
            _connectedDevice.value = null
            _connectedRssi.value = null
            _connectionState.value = BluetoothProfile.STATE_DISCONNECTED
            isConnecting = false
            _hostLeds.value = 0
            _gamepadRumble.value = 0
            try {
                bleBatteryService.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping bleBatteryService after virtual unplug", e)
            }
        }
    }

    /**
     * Parses host->device output reports: Report ID 1 carries keyboard LED
     * state, Report ID 4 carries gamepad force feedback. Called from both
     * onSetReport and onInterruptData because stacks differ on which channel
     * delivers output reports.
     */
    private fun handleHostOutputReport(reportId: Byte, data: ByteArray?) {
        when (reportId.toInt()) {
            1 -> {
                val leds = parseLedState(data)
                if (leds != _hostLeds.value) {
                    _hostLeds.value = leds
                    Log.d(TAG, "Host LEDs updated: numLock=${leds and LED_NUM_LOCK != 0}, " +
                        "capsLock=${leds and LED_CAPS_LOCK != 0}, scrollLock=${leds and LED_SCROLL_LOCK != 0}")
                }
            }
            4 -> {
                val intensity = parseRumbleIntensity(data)
                if (intensity != _gamepadRumble.value) {
                    _gamepadRumble.value = intensity
                    Log.d(TAG, "Gamepad rumble updated: intensity=$intensity")
                }
            }
        }
    }

    @SuppressLint("NewApi")
    fun registerApp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.e(TAG, "registerApp: SDK too low (${Build.VERSION.SDK_INT})")
            return
        }
        val profile = hidDeviceProfile
        if (profile == null) {
            Log.e(TAG, "registerApp: HID profile is null, reinitializing...")
            initializeHidProfile()
            return
        }
        if (isRegistered) {
            Log.d(TAG, "registerApp: Already registered, skipping")
            return
        }

        Log.d(TAG, "registerApp: Creating SDP settings...")
        Log.d(TAG, "  Name: AirMouse")
        Log.d(TAG, "  Description: Wireless HID Controller")
        Log.d(TAG, "  Provider: Generic HID Device")
        Log.d(TAG, "  Subclass: SUBCLASS1_COMBO (0x03)")
        Log.d(TAG, "  Descriptor size: ${HID_DESCRIPTOR.size} bytes")

        // Windows-compatible SDP settings using official Android SDK constant
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "AirMouse",                              // Name
            "Wireless HID Controller",               // Description
            "Generic HID Device",                   // Provider
            BluetoothHidDevice.SUBCLASS1_COMBO,      // Subclass: Combo Keyboard/Pointer
            HID_DESCRIPTOR
        )

        try {
            Log.d(TAG, "registerApp: Calling profile.registerApp()...")
            val registered = profile.registerApp(
                sdpSettings,
                null,
                null,
                scheduledExecutor,
                mCallback
            )
            Log.d(TAG, "registerApp: registerApp() returned: $registered")
        } catch (e: Exception) {
            Log.e(TAG, "registerApp: Exception during registration", e)
        }
    }

    @SuppressLint("NewApi")
    fun unregisterApp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val profile = hidDeviceProfile ?: return
        if (!isRegistered) return

        try {
            profile.unregisterApp()
            isRegistered = false
            _isAppRegistered.value = false
            Log.d(TAG, "unregisterApp successful")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering HID app", e)
        }
    }

    fun getBondedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Missing BLUETOOTH_CONNECT permission to get bonded devices", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting bonded devices", e)
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        // Stop any ongoing scanning first
        stopScanning()

        _scannedDevices.value = emptyList()
        _isScanning.value = true

        discoveryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        if (device != null && device.isHidCompatibleHost()) {
                            // Keep the connected host's signal strength fresh
                            // whenever it appears in a discovery scan.
                            if (device.address == _connectedDevice.value?.address && rssi != Short.MIN_VALUE.toInt()) {
                                _connectedRssi.value = rssi
                            }
                            // Only add if not already in paired list
                            val paired = getBondedDevices()
                            val alreadyPaired = paired.any { it.address == device.address }
                            if (!alreadyPaired) {
                                val currentList = _scannedDevices.value
                                val existingIndex = currentList.indexOfFirst { it.device.address == device.address }
                                if (existingIndex >= 0) {
                                    // Update RSSI of existing scanned device
                                    _scannedDevices.value = currentList.mapIndexed { idx, item ->
                                        if (idx == existingIndex) item.copy(rssi = rssi) else item
                                    }
                                } else {
                                    _scannedDevices.value = currentList + ScannedDevice(device, rssi)
                                }
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        _isScanning.value = true
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        _isScanning.value = false
                        unregisterDiscoveryReceiver()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        try {
            androidx.core.content.ContextCompat.registerReceiver(
                appContext,
                discoveryReceiver,
                filter,
                // Discovery broadcasts are protected system broadcasts; see above.
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            val success = adapter.startDiscovery()
            if (!success) {
                Log.e(TAG, "Failed to start discovery")
                _isScanning.value = false
                unregisterDiscoveryReceiver()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting discovery", e)
            _isScanning.value = false
            unregisterDiscoveryReceiver()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        val adapter = bluetoothAdapter ?: return
        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling discovery", e)
        }
        _isScanning.value = false
        unregisterDiscoveryReceiver()
    }

    private fun unregisterDiscoveryReceiver() {
        discoveryReceiver?.let { receiver ->
            try {
                appContext.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering discoveryReceiver", e)
            }
            discoveryReceiver = null
        }
    }

    @SuppressLint("MissingPermission")
    fun bondDevice(device: BluetoothDevice): Boolean {
        return try {
            // Cancel discovery before initiating bond for better performance and reliability
            stopScanning()
            device.createBond()
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating bonding for device ${device.address}", e)
            false
        }
    }

    @SuppressLint("NewApi")
    fun connectHost(device: BluetoothDevice): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false

        val currentState = _connectionState.value
        val currentTarget = _targetDevice.value

        // Guard: Don't connect if already connected to this device
        if (currentState == BluetoothProfile.STATE_CONNECTED && _connectedDevice.value?.address == device.address) {
            Log.d(TAG, "Already connected to ${device.getSafeName()}")
            return true
        }

        // Handle ongoing connection attempt:
        // - If connecting to the same device -> Cancel attempt
        // - If connecting to a different device -> Cancel current attempt and connect to new target
        if (currentState == BluetoothProfile.STATE_CONNECTING || isConnecting) {
            if (currentTarget?.address == device.address) {
                Log.d(TAG, "Already connecting to ${device.getSafeName()}, cancelling attempt")
                cancelConnection()
                return false
            } else {
                Log.d(TAG, "Cancelling connection to ${currentTarget?.getSafeName()} to connect to ${device.getSafeName()}")
                cancelConnection()
            }
        }

        // Guard: Cooldown between connect attempts
        val now = System.currentTimeMillis()
        if (now - lastConnectAttemptTime < connectCooldownMs) {
            Log.d(TAG, "Connect cooldown active, skipping")
            return false
        }

        // Guard: Ensure app is registered before connecting (Windows requirement)
        if (!isRegistered) {
            Log.w(TAG, "App not registered, storing as pending connection")
            pendingConnectionDevice = device
            registerApp()
            return false
        }

        return performConnection(device)
    }

    @SuppressLint("NewApi")
    private fun performConnection(device: BluetoothDevice): Boolean {
        lastConnectAttemptTime = System.currentTimeMillis()
        isConnecting = true
        _targetDevice.value = device
        _connectionState.value = BluetoothProfile.STATE_CONNECTING
        Log.d(TAG, "Connecting to host: ${device.getSafeName()} [${device.address}]")

        // Schedule 10-second automatic connection timeout
        connectTimeoutJob?.cancel(true)
        connectTimeoutJob = scheduledExecutor.schedule({
            if (_connectionState.value == BluetoothProfile.STATE_CONNECTING) {
                Log.w(TAG, "Connection attempt to ${device.getSafeName()} timed out after 10s")
                cancelConnection()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        Toast.makeText(appContext, "Connection timed out. ${device.getSafeName()} is unreachable.", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {}
                }
            }
        }, 10, TimeUnit.SECONDS)

        initializeHidProfile {
            try {
                Log.d(TAG, "Attempting connection...")
                val connected = hidDeviceProfile?.connect(device)
                Log.d(TAG, "connectHost result: $connected")
                if (connected != true) {
                    isConnecting = false
                    _connectionState.value = BluetoothProfile.STATE_DISCONNECTED
                    _targetDevice.value = null
                    connectTimeoutJob?.cancel(true)
                    Log.w(TAG, "Connection attempt returned false")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException connecting to host", e)
                isConnecting = false
                _connectionState.value = BluetoothProfile.STATE_DISCONNECTED
                _targetDevice.value = null
                connectTimeoutJob?.cancel(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to host", e)
                isConnecting = false
                _connectionState.value = BluetoothProfile.STATE_DISCONNECTED
                _targetDevice.value = null
                connectTimeoutJob?.cancel(true)
            }
        }
        return true
    }

    @SuppressLint("NewApi")
    fun cancelConnection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        connectTimeoutJob?.cancel(true)
        val deviceToDisconnect = _targetDevice.value ?: _connectedDevice.value
        Log.d(TAG, "Cancelling connection/disconnecting for device: ${deviceToDisconnect?.getSafeName()}")
        if (deviceToDisconnect != null) {
            try {
                hidDeviceProfile?.disconnect(deviceToDisconnect)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException disconnecting host", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting host", e)
            }
        }
        _connectionState.value = BluetoothProfile.STATE_DISCONNECTED
        _connectedDevice.value = null
        _connectedRssi.value = null
        _targetDevice.value = null
        isConnecting = false
    }

    @SuppressLint("NewApi")
    fun disconnectHost() {
        cancelConnection()
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }

    fun updateBleBatteryLevel(level: Int) {
        lastBatteryLevel = level
        bleBatteryService.updateBatteryLevel(level)
    }

    fun isConnected(): Boolean {
        return _connectionState.value == BluetoothProfile.STATE_CONNECTED && _connectedDevice.value != null
    }

    fun isCurrentlyConnecting(): Boolean {
        return _connectionState.value == BluetoothProfile.STATE_CONNECTING || isConnecting
    }

    // --- MOUSE TRANSMISSION ---
    // Report ID 2: [buttons (1 byte), dx (1 byte), dy (1 byte), scroll (1 byte), hScroll (1 byte)]
    // dx/dy: -127 to +127 relative movement; scroll: vertical wheel, hScroll: horizontal wheel
    @SuppressLint("NewApi")
    fun sendMouseInput(buttons: Byte, dx: Byte, dy: Byte, scroll: Byte, hScroll: Byte = 0): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val profile = hidDeviceProfile ?: return false
        val device = _connectedDevice.value ?: return false

        // Validate and clamp values
        val validDx = dx.coerceIn(-127, 127)
        val validDy = dy.coerceIn(-127, 127)
        val validScroll = scroll.coerceIn(-127, 127)
        val validHScroll = hScroll.coerceIn(-127, 127)
        val validButtons = (buttons.toInt() and 0x1F).toByte() // Only lower 5 bits

        val data = byteArrayOf(validButtons, validDx.toByte(), validDy.toByte(), validScroll, validHScroll)
        Log.v(TAG, "sendMouseInput: buttons=$validButtons, dx=$validDx, dy=$validDy, scroll=$validScroll, hScroll=$validHScroll")

        val result = profile.sendReport(device, 2, data)
        if (!result) {
            Log.w(TAG, "sendMouseInput: sendReport failed - device may be disconnected")
        }
        return result
    }

    // --- GAMEPAD TRANSMISSION ---
    // Report ID 4: [x (1), y (1), z (1), rz (1), hat (1), buttons lo (1), buttons hi (1)]
    // hat: 0=up, 2=right, 4=down, 6=left (and diagonals 1/3/5/7), 8 = released
    // buttons: bitmask, bit 0 = button 1 (A), bit 1 = button 2 (B), ...
    @SuppressLint("NewApi")
    fun sendGamepadInput(buttons: Int, hat: Byte, x: Byte, y: Byte, z: Byte = 0, rz: Byte = 0): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val profile = hidDeviceProfile ?: return false
        val device = _connectedDevice.value ?: return false

        val data = byteArrayOf(
            x, y, z, rz,
            hat,
            (buttons and 0xFF).toByte(),
            ((buttons shr 8) and 0xFF).toByte()
        )
        Log.v(TAG, "sendGamepadInput: buttons=0x${Integer.toHexString(buttons)}, hat=$hat")
        return profile.sendReport(device, 4, data)
    }

    // --- KEYBOARD TRANSMISSION ---
    // Report ID 1: [modifiers (1 byte), reserved (1 byte), keyCodes (6 bytes)]
    @SuppressLint("NewApi")
    fun sendKeyboardInput(modifiers: Byte, keyCodes: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val profile = hidDeviceProfile ?: return false
        val device = _connectedDevice.value ?: return false

        val fullReport = ByteArray(8)
        fullReport[0] = modifiers
        fullReport[1] = 0 // Reserved byte

        // Populate key codes (up to 6)
        for (i in 0 until minOf(6, keyCodes.size)) {
            fullReport[2 + i] = keyCodes[i]
        }

        Log.v(TAG, "sendKeyboardInput: modifiers=$modifiers, keyCodes=${keyCodes.contentToString()}")
        val result = profile.sendReport(device, 1, fullReport)
        if (!result) {
            Log.w(TAG, "sendKeyboardInput: sendReport failed")
        }
        return result
    }

    // Sends a key press followed immediately by key release
    fun sendKeyPress(modifiers: Byte, keyCode: Byte) {
        sendKeyboardInput(modifiers, byteArrayOf(keyCode))
        sendKeyboardInput(0, byteArrayOf(0))
    }

    // --- CONSUMER CONTROL TRANSMISSION ---
    // Report ID 3: [keys (1 byte)]
    // Map: Bit 0: Vol Up, Bit 1: Vol Down, Bit 2: Mute, Bit 3: Play/Pause, Bit 4: Next, Bit 5: Prev, Bit 6: Power, Bit 7: Menu
    @SuppressLint("NewApi")
    fun sendConsumerInput(keys: Byte): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val profile = hidDeviceProfile ?: return false
        val device = _connectedDevice.value ?: return false

        val data = byteArrayOf(keys)
        Log.v(TAG, "sendConsumerInput: keys=$keys")
        val success = profile.sendReport(device, 3, data)

        // Release immediate key-press after volume or control command to mimic keyboard tap release
        if (keys != 0.toByte()) {
            scheduledExecutor.schedule({
                try {
                    profile.sendReport(device, 3, byteArrayOf(0))
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing consumer input", e)
                }
            }, 50, TimeUnit.MILLISECONDS)
        }
        return success
    }

}
