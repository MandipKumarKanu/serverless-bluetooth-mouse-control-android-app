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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
fun BluetoothDevice.getSafeName(): String {
    return try {
        this.name ?: "Unknown Device"
    } catch (e: SecurityException) {
        "Device"
    }
}

@SuppressLint("MissingPermission")
class BluetoothHidManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var hidDeviceProfile: BluetoothHidDevice? = null
    private var isRegistered = false

    // State flows for UI mapping
    private val _connectionState = MutableStateFlow(BluetoothProfile.STATE_DISCONNECTED)
    val connectionState: StateFlow<Int> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    private val _isProfileReady = MutableStateFlow(false)
    val isProfileReady: StateFlow<Boolean> = _isProfileReady.asStateFlow()

    private val _isAppRegistered = MutableStateFlow(false)
    val isAppRegistered: StateFlow<Boolean> = _isAppRegistered.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled ?: false)
    val isBluetoothEnabledFlow: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
                if (state == BluetoothAdapter.STATE_ON) {
                    initializeHidProfile()
                }
            }
        }
    }

    init {
        initializeHidProfile()
        try {
            appContext.registerReceiver(
                bluetoothReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
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
        private val HID_DESCRIPTOR = byteArrayOf(
            // Keyboard (Report ID 1)
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
            
            // Mouse (Report ID 2)
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
            
            // Wheel scroll
            0x09.toByte(), 0x38.toByte(),       //     USAGE (Wheel)
            0x15.toByte(), 0x81.toByte(),       //     LOGICAL_MINIMUM (-127)
            0x25.toByte(), 0x7f.toByte(),       //     LOGICAL_MAXIMUM (127)
            0x75.toByte(), 0x08.toByte(),       //     REPORT_SIZE (8)
            0x95.toByte(), 0x01.toByte(),       //     REPORT_COUNT (1)
            0x81.toByte(), 0x06.toByte(),       //     INPUT (Data,Var,Rel)
            
            0xc0.toByte(),                      //   END_COLLECTION
            0xc0.toByte(),                      // END_COLLECTION
            
            // Consumer Control (Report ID 3 - Volume/Media Keys)
            0x05.toByte(), 0x0c.toByte(),       // USAGE_PAGE (Consumer Devices)
            0x09.toByte(), 0x01.toByte(),       // USAGE (Consumer Control)
            0xa1.toByte(), 0x01.toByte(),       // COLLECTION (Application)
            0x85.toByte(), 0x03.toByte(),       //   REPORT_ID (3)
            0x15.toByte(), 0x00.toByte(),       //   LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x01.toByte(),       //   LOGICAL_MAXIMUM (1)
            0x75.toByte(), 0x01.toByte(),       //   REPORT_SIZE (1)
            
            // Key Definitions: Volume Up, Down, Mute, Play/Pause, Next Track, Prev Track, Power, Home
            0x09.toByte(), 0xe9.toByte(),       //   USAGE (Volume Up)
            0x09.toByte(), 0xea.toByte(),       //   USAGE (Volume Down)
            0x09.toByte(), 0xe2.toByte(),       //   USAGE (Mute)
            0x09.toByte(), 0xcd.toByte(),       //   USAGE (Play/Pause)
            0x09.toByte(), 0xb5.toByte(),       //   USAGE (Scan Next Track)
            0x09.toByte(), 0xb6.toByte(),       //   USAGE (Scan Previous Track)
            0x09.toByte(), 0x30.toByte(),       //   USAGE (Power)
            0x09.toByte(), 0x40.toByte(),       //   USAGE (Menu) - fallback home/menu
            
            0x95.toByte(), 0x08.toByte(),       //   REPORT_COUNT (8)
            0x81.toByte(), 0x02.toByte(),       //   INPUT (Data,Var,Abs)
            0xc0.toByte()                       // END_COLLECTION
        )
    }

    fun initializeHidProfile(onReady: () -> Unit = {}) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
                    }
                }
            }, BluetoothProfile.HID_DEVICE)
        } else {
            Log.e(TAG, "Bluetooth HID Device Profile requires Android 9 (API 28) or higher!")
        }
    }

    private val mCallback = @SuppressLint("NewApi") object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            Log.d(TAG, "onAppStatusChanged: registered=$registered")
            _isAppRegistered.value = registered
            isRegistered = registered
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            Log.d(TAG, "onConnectionStateChanged: device=${device?.name}, state=$state")
            _connectionState.value = state
            _connectedDevice.value = if (state == BluetoothProfile.STATE_CONNECTED) device else null
        }
    }

    @SuppressLint("NewApi")
    fun registerApp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val profile = hidDeviceProfile
        if (profile == null) {
            initializeHidProfile()
            return
        }
        if (isRegistered) return

        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "AirMouse",
            "Serverless Air Mouse Remote",
            "Google AI Studio",
            0xC0.toByte(), // Subclass: Peripheral (Combo Keyboard/Pointer)
            HID_DESCRIPTOR
        )

        try {
            val registered = profile.registerApp(
                sdpSettings,
                null,
                null,
                Executors.newSingleThreadExecutor(),
                mCallback
            )
            Log.d(TAG, "registerApp attempt: success=$registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering HID app", e)
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

    @SuppressLint("NewApi")
    fun connectHost(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        Log.d(TAG, "Connecting to host: ${device.getSafeName()} [${device.address}]")
        initializeHidProfile {
            try {
                val connected = hidDeviceProfile?.connect(device)
                Log.d(TAG, "connectHost result: $connected")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException connecting to host", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to host", e)
            }
        }
    }

    @SuppressLint("NewApi")
    fun disconnectHost() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val device = _connectedDevice.value ?: return
        Log.d(TAG, "Disconnecting from host: ${device.getSafeName()}")
        try {
            hidDeviceProfile?.disconnect(device)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException disconnecting from host", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from host", e)
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }

    // --- MOUSE TRANSMISSION ---
    // Report ID 2: [buttons (1 byte), dx (1 byte), dy (1 byte), scroll (1 byte)]
    // dx/dy: -127 to +127 relative movement
    @SuppressLint("NewApi")
    fun sendMouseInput(buttons: Byte, dx: Byte, dy: Byte, scroll: Byte): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val profile = hidDeviceProfile ?: return false
        val device = _connectedDevice.value ?: return false

        val data = byteArrayOf(buttons, dx, dy, scroll)
        return profile.sendReport(device, 2, data)
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
        
        return profile.sendReport(device, 1, fullReport)
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
        val success = profile.sendReport(device, 3, data)
        
        // Release immediate key-press after volume or control command to mimic keyboard tap release
        if (keys != 0.toByte()) {
            Executors.newSingleThreadScheduledExecutor().schedule({
                profile.sendReport(device, 3, byteArrayOf(0))
            }, 50, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
        return success
    }
}
