package com.example.bluetooth

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the Bluetooth HID Device profile.
 *
 * The concrete implementation ([BluetoothHidManager]) manages SDP registration,
 * connection lifecycle, and HID report transmission. This interface lets the
 * ViewModel, screens, and tests depend only on the contract, making the
 * concrete singleton mockable and swappable.
 */
interface HidDeviceManager {

    // ── Connection state ──────────────────────────────────────────────

    /** Current Bluetooth HID connection state (STATE_DISCONNECTED, etc.). */
    val connectionState: StateFlow<Int>

    /** The currently connected host device, or null. */
    val connectedDevice: StateFlow<BluetoothDevice?>

    /** The device a connection attempt is targeting, or null. */
    val targetDevice: StateFlow<BluetoothDevice?>

    /** True when the HID Device profile proxy is connected to the stack. */
    val isProfileReady: StateFlow<Boolean>

    /** True when the Bluetooth adapter is enabled. */
    val isBluetoothEnabledFlow: StateFlow<Boolean>

    /** Host-side keyboard lock LED bitmask (bit 0 = Num, 1 = Caps, 2 = Scroll). */
    val hostLeds: StateFlow<Int>

    /** Gamepad force-feedback rumble intensity 0–255 from the host. */
    val gamepadRumble: StateFlow<Int>

    /** Measured RSSI (dBm) of the connected host, or null. */
    val connectedRssi: StateFlow<Int?>

    // ── Scanning / pairing ────────────────────────────────────────────

    /** Discovered nearby devices from a classic Bluetooth scan. */
    val scannedDevices: StateFlow<List<ScannedDevice>>

    /** True while a discovery scan is in progress. */
    val isScanning: StateFlow<Boolean>

    /** Bonded (paired) devices visible to the adapter. */
    val bondedDevices: StateFlow<List<BluetoothDevice>>

    // ── Queries ───────────────────────────────────────────────────────

    fun getBondedDevices(): List<BluetoothDevice>
    fun isConnected(): Boolean
    fun isCurrentlyConnecting(): Boolean
    fun isBluetoothEnabled(): Boolean

    // ── Connection lifecycle ──────────────────────────────────────────

    fun registerApp()
    fun connectHost(device: BluetoothDevice): Boolean
    fun cancelConnection()
    fun bondDevice(device: BluetoothDevice): Boolean
    fun startScanning()
    fun stopScanning()

    // ── HID report transmission ───────────────────────────────────────

    fun sendMouseInput(buttons: Byte, dx: Byte, dy: Byte, scroll: Byte, hScroll: Byte = 0): Boolean
    fun sendKeyboardInput(modifiers: Byte, keyCodes: ByteArray): Boolean
    fun sendKeyPress(modifiers: Byte, keyCode: Byte)
    fun sendConsumerInput(keys: Byte): Boolean
    fun sendGamepadInput(buttons: Int, hat: Byte, x: Byte, y: Byte, z: Byte = 0, rz: Byte = 0): Boolean

    // ── BLE battery ───────────────────────────────────────────────────

    fun updateBleBatteryLevel(level: Int)
}
