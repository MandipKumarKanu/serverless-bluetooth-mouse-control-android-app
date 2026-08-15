package com.example.bluetooth

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.bluetooth.BluetoothManager

/**
 * Tests for the pure device-classification helpers and the connection-state
 * guards in BluetoothHidManager. The Bluetooth stack itself is not available
 * under Robolectric, so HID transmission calls are verified to fail safely
 * while disconnected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BluetoothHidManagerTest {

    /**
     * BluetoothClass's (int) constructor is package-private in the Android SDK,
     * so unit tests can't call it directly; create instances reflectively.
     */
    private fun bluetoothClass(classValue: Int): BluetoothClass {
        val constructor = BluetoothClass::class.java.getDeclaredConstructor(Int::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(classValue)
    }

    private fun device(name: String?, bluetoothClass: BluetoothClass? = null): BluetoothDevice {
        // BluetoothDevice has no public constructor; create a shadowed instance
        // via the BluetoothManager adapter (ShadowBluetoothDevice.newInstance and
        // BluetoothAdapter.getDefaultAdapter are deprecated).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val d = adapter.getRemoteDevice("AA:BB:CC:DD:EE:FF")
        if (name != null) shadowOf(d).setName(name)
        if (bluetoothClass != null) shadowOf(d).setBluetoothClass(bluetoothClass)
        return d
    }

    // --- getSafeName ---

    @Test
    fun getSafeName_returnsDeviceName() {
        assertEquals("My PC", device("My PC").getSafeName())
    }

    @Test
    fun getSafeName_fallsBackForUnnamedDevice() {
        assertEquals("Unknown Device", device(null).getSafeName())
    }

    // --- isComputer ---

    @Test
    fun isComputer_trueForComputerMajorClass() {
        // 0x0100 = BluetoothClass.Device.Major.COMPUTER
        assertTrue(device("Desktop", bluetoothClass(0x0100)).isComputer())
    }

    @Test
    fun isComputer_falseForPhone() {
        // 0x0200 = BluetoothClass.Device.Major.PHONE
        assertFalse(device("Phone", bluetoothClass(0x0200)).isComputer())
    }

    // --- isHidCompatibleHost ---

    @Test
    fun hidHost_rejectsDeviceWithoutName() {
        assertFalse(device(null).isHidCompatibleHost())
    }

    @Test
    fun hidHost_acceptsComputer() {
        assertTrue(device("My Desktop", bluetoothClass(0x0100)).isHidCompatibleHost())
    }

    @Test
    fun hidHost_rejectsNonHostMajorClasses() {
        assertFalse(device("Watch", bluetoothClass(0x0700)).isHidCompatibleHost()) // 1792 WEARABLE
        assertFalse(device("Fitness Band", bluetoothClass(0x0900)).isHidCompatibleHost()) // 2304 HEALTH
        assertFalse(device("Toy", bluetoothClass(0x0800)).isHidCompatibleHost()) // 2048 TOY
        assertFalse(device("Camera", bluetoothClass(0x0600)).isHidCompatibleHost()) // 1536 IMAGING
    }

    @Test
    fun hidHost_filtersAudioDeviceClasses() {
        // 0x041C = PORTABLE_AUDIO (major AUDIO_VIDEO); excluded by device class
        assertFalse(device("Bluetooth Speaker", bluetoothClass(0x041C)).isHidCompatibleHost())
    }

    @Test
    fun hidHost_filtersAudioDevicesByNameFallback() {
        // Audio-video major class with an unknown minor class: the name filter
        // is the backstop for headsets/headphones/etc.
        assertFalse(device("Sony Headphones", bluetoothClass(0x0400)).isHidCompatibleHost())
        assertTrue(device("Living Room TV", bluetoothClass(0x0400)).isHidCompatibleHost())
    }

    // --- host output report parsing (LEDs / gamepad rumble) ---

    @Test
    fun parseLedState_emptyReport_returnsZero() {
        assertEquals(0, parseLedState(null))
        assertEquals(0, parseLedState(ByteArray(0)))
    }

    @Test
    fun parseLedState_extractsLockBitsFromFirstByte() {
        // Num Lock | Caps Lock
        assertEquals(0x03, parseLedState(byteArrayOf(0x03)))
        // Caps Lock only
        assertEquals(LED_CAPS_LOCK, parseLedState(byteArrayOf(LED_CAPS_LOCK.toByte())))
        // Scroll Lock only
        assertEquals(LED_SCROLL_LOCK, parseLedState(byteArrayOf(LED_SCROLL_LOCK.toByte())))
        // Full 8-byte keyboard-style report: only the first byte matters
        assertEquals(0x03, parseLedState(byteArrayOf(0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)))
    }

    @Test
    fun parseLedState_treatsFirstByteAsUnsigned() {
        // A host sending 0xFF for all LEDs must not become a negative value
        assertEquals(0xFF, parseLedState(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun parseRumbleIntensity_emptyReport_returnsZero() {
        assertEquals(0, parseRumbleIntensity(null))
        assertEquals(0, parseRumbleIntensity(ByteArray(0)))
    }

    @Test
    fun parseRumbleIntensity_usesStrongestMotor() {
        // weak=20, strong=200 -> 200
        assertEquals(200, parseRumbleIntensity(byteArrayOf(200.toByte(), 20.toByte())))
        // strong=30, weak=240 -> 240
        assertEquals(240, parseRumbleIntensity(byteArrayOf(30.toByte(), 240.toByte())))
    }

    @Test
    fun parseRumbleIntensity_singleByteReport() {
        // Some stacks deliver a single intensity byte
        assertEquals(128, parseRumbleIntensity(byteArrayOf(128.toByte())))
    }

    @Test
    fun parseRumbleIntensity_zeroMeansMotorsOff() {
        assertEquals(0, parseRumbleIntensity(byteArrayOf(0, 0)))
    }

    // --- connection guards / HID transmission while disconnected ---

    @Test
    fun connectHost_returnsFalseWhenAppNotRegistered() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = BluetoothHidManager.getInstance(context)

        // The HID profile never becomes ready under Robolectric, so the app is
        // never registered: connectHost must stash a pending connection and
        // report failure instead of crashing.
        assertFalse(manager.connectHost(device("My PC", bluetoothClass(0x0100))))
        assertFalse(manager.isConnected())
        assertFalse(manager.isCurrentlyConnecting())
    }

    @Test
    fun sendReports_failSafelyWhenDisconnected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = BluetoothHidManager.getInstance(context)

        assertFalse(manager.sendMouseInput(1, 10, 10, 0))
        assertFalse(manager.sendKeyboardInput(0x01, byteArrayOf(0x06)))
        assertFalse(manager.sendConsumerInput(0x08))
    }

    @Test
    fun initialConnectionState_isDisconnected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = BluetoothHidManager.getInstance(context)

        assertEquals(BluetoothProfile.STATE_DISCONNECTED, manager.connectionState.value)
        assertFalse(manager.isConnected())
        assertFalse(manager.isCurrentlyConnecting())
    }
}
