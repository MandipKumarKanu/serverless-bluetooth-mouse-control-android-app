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
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

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
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    // Reset state when Bluetooth is turned off
                    _isProfileReady.value = false
                    _isAppRegistered.value = false
                    _connectionState.value = BluetoothProfile.STATE_DISCONNECTED
                    _connectedDevice.value = null
                    isConnecting = false
                    isRegistered = false
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

            // Wheel scroll
            0x09.toByte(), 0x38.toByte(),       //     USAGE (Wheel)
            0x15.toByte(), 0x81.toByte(),       //     LOGICAL_MINIMUM (-127)
            0x25.toByte(), 0x7f.toByte(),       //     LOGICAL_MAXIMUM (127)
            0x75.toByte(), 0x08.toByte(),       //     REPORT_SIZE (8)
            0x95.toByte(), 0x01.toByte(),       //     REPORT_COUNT (1)
            0x81.toByte(), 0x06.toByte(),       //     INPUT (Data,Var,Rel)

            0xc0.toByte(),                      //   END_COLLECTION
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
                    _connectedDevice.value = device
                    isConnecting = false
                    Log.d(TAG, "=== CONNECTION ESTABLISHED ===")
                    Log.d(TAG, "Device: ${device?.getSafeName()}")
                    Log.d(TAG, "Address: ${device?.address}")
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    isConnecting = true
                    Log.d(TAG, "Connecting to: ${device?.getSafeName()}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectedDevice.value = null
                    isConnecting = false
                    Log.d(TAG, "Disconnected from: ${device?.getSafeName()}")
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    Log.d(TAG, "Disconnecting from: ${device?.getSafeName()}")
                }
            }
        }

        // Handle GetReport - Windows may request current state
        override fun onGetReport(device: BluetoothDevice?, type: Byte, reportId: Byte, bufferSize: Int) {
            super.onGetReport(device, type, reportId, bufferSize)
            Log.d(TAG, "onGetReport: device=${device?.getSafeName()}, type=$type, reportId=$reportId, bufferSize=$bufferSize")

            // Respond with empty report to satisfy Windows
            val profile = hidDeviceProfile ?: return
            try {
                when (reportId) {
                    1.toByte() -> {
                        // Keyboard report - 8 bytes
                        profile.sendReport(device, 1, ByteArray(8))
                    }
                    2.toByte() -> {
                        // Mouse report - 4 bytes
                        profile.sendReport(device, 2, ByteArray(4))
                    }
                    3.toByte() -> {
                        // Consumer control report - 1 byte
                        profile.sendReport(device, 3, ByteArray(1))
                    }
                }
                Log.d(TAG, "onGetReport: Responded with empty report for reportId=$reportId")
            } catch (e: Exception) {
                Log.e(TAG, "onGetReport: Error sending response", e)
            }
        }

        // Handle SetReport - Windows may send LED state
        override fun onSetReport(device: BluetoothDevice?, type: Byte, reportId: Byte, data: ByteArray?) {
            super.onSetReport(device, type, reportId, data)
            Log.d(TAG, "onSetReport: device=${device?.getSafeName()}, type=$type, reportId=$reportId, data=${data?.contentToString()}")
            // ACK the report - Android API only takes device and reportId
            val profile = hidDeviceProfile ?: return
            try {
                profile.reportError(device, reportId)
                Log.d(TAG, "onSetReport: ACK sent for reportId=$reportId")
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

        // Handle InterruptData - Host may send data to device
        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
            super.onInterruptData(device, reportId, data)
            Log.d(TAG, "onInterruptData: device=${device?.getSafeName()}, reportId=$reportId, data=${data?.contentToString()}")
            // Handle LED state changes if needed (e.g., Num Lock, Caps Lock)
        }

        // Handle VirtualCableUnplug - Windows may unplug virtually
        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            super.onVirtualCableUnplug(device)
            Log.d(TAG, "onVirtualCableUnplug: device=${device?.getSafeName()}")
            _connectedDevice.value = null
            _connectionState.value = BluetoothProfile.STATE_DISCONNECTED
            isConnecting = false
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

    @SuppressLint("NewApi")
    fun connectHost(device: BluetoothDevice): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false

        val currentState = _connectionState.value

        // Guard: Don't connect if already connected to this device
        if (currentState == BluetoothProfile.STATE_CONNECTED && _connectedDevice.value?.address == device.address) {
            Log.d(TAG, "Already connected to ${device.getSafeName()}")
            return true
        }

        // Guard: Don't connect if already connecting
        if (currentState == BluetoothProfile.STATE_CONNECTING || isConnecting) {
            Log.d(TAG, "Already connecting, skipping")
            return false
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
        Log.d(TAG, "Connecting to host: ${device.getSafeName()} [${device.address}]")

        initializeHidProfile {
            try {
                Log.d(TAG, "Attempting connection...")
                val connected = hidDeviceProfile?.connect(device)
                Log.d(TAG, "connectHost result: $connected")
                if (connected != true) {
                    isConnecting = false
                    Log.w(TAG, "Connection attempt returned false")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException connecting to host", e)
                isConnecting = false
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to host", e)
                isConnecting = false
            }
        }
        return true
    }

    @SuppressLint("NewApi")
    fun disconnectHost() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val device = _connectedDevice.value ?: return
        Log.d(TAG, "Disconnecting from host: ${device.getSafeName()}")
        try {
            hidDeviceProfile?.disconnect(device)
            isConnecting = false
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException disconnecting from host", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from host", e)
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }

    fun isConnected(): Boolean {
        return _connectionState.value == BluetoothProfile.STATE_CONNECTED && _connectedDevice.value != null
    }

    fun isCurrentlyConnecting(): Boolean {
        return _connectionState.value == BluetoothProfile.STATE_CONNECTING || isConnecting
    }

    // --- MOUSE TRANSMISSION ---
    // Report ID 2: [buttons (1 byte), dx (1 byte), dy (1 byte), scroll (1 byte)]
    // dx/dy: -127 to +127 relative movement
    @SuppressLint("NewApi")
    fun sendMouseInput(buttons: Byte, dx: Byte, dy: Byte, scroll: Byte): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val profile = hidDeviceProfile ?: return false
        val device = _connectedDevice.value ?: return false

        // Validate and clamp values
        val validDx = dx.coerceIn(-127, 127)
        val validDy = dy.coerceIn(-127, 127)
        val validScroll = scroll.coerceIn(-127, 127)
        val validButtons = (buttons.toInt() and 0x1F).toByte() // Only lower 5 bits

        val data = byteArrayOf(validButtons, validDx.toByte(), validDy.toByte(), validScroll.toByte())
        Log.v(TAG, "sendMouseInput: buttons=$validButtons, dx=$validDx, dy=$validDy, scroll=$validScroll")

        val result = profile.sendReport(device, 2, data)
        if (!result) {
            Log.w(TAG, "sendMouseInput: sendReport failed - device may be disconnected")
        }
        return result
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

    fun destroy() {
        scheduledExecutor.shutdownNow()
    }
}
