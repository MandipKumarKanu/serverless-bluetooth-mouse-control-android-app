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
import org.robolectric.shadows.ShadowBluetoothDevice

/**
 * Tests for the pure device-classification helpers and the connection-state
 * guards in BluetoothHidManager. The Bluetooth stack itself is not available
 * under Robolectric, so HID transmission calls are verified to fail safely
 * while disconnected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BluetoothHidManagerTest {

    private fun device(name: String?, bluetoothClass: BluetoothClass? = null): BluetoothDevice {
        val d = ShadowBluetoothDevice.newInstance("AA:BB:CC:DD:EE:FF")
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
        assertTrue(device("Desktop", BluetoothClass(0x0100)).isComputer())
    }

    @Test
    fun isComputer_falseForPhone() {
        // 0x0200 = BluetoothClass.Device.Major.PHONE
        assertFalse(device("Phone", BluetoothClass(0x0200)).isComputer())
    }

    // --- isHidCompatibleHost ---

    @Test
    fun hidHost_rejectsDeviceWithoutName() {
        assertFalse(device(null).isHidCompatibleHost())
    }

    @Test
    fun hidHost_acceptsComputer() {
        assertTrue(device("My Desktop", BluetoothClass(0x0100)).isHidCompatibleHost())
    }

    @Test
    fun hidHost_rejectsNonHostMajorClasses() {
        assertFalse(device("Watch", BluetoothClass(0x0700)).isHidCompatibleHost()) // 1792 WEARABLE
        assertFalse(device("Fitness Band", BluetoothClass(0x0900)).isHidCompatibleHost()) // 2304 HEALTH
        assertFalse(device("Toy", BluetoothClass(0x0800)).isHidCompatibleHost()) // 2048 TOY
        assertFalse(device("Camera", BluetoothClass(0x0600)).isHidCompatibleHost()) // 1536 IMAGING
    }

    @Test
    fun hidHost_filtersAudioDeviceClasses() {
        // 0x041C = PORTABLE_AUDIO (major AUDIO_VIDEO); excluded by device class
        assertFalse(device("Bluetooth Speaker", BluetoothClass(0x041C)).isHidCompatibleHost())
    }

    @Test
    fun hidHost_filtersAudioDevicesByNameFallback() {
        // Audio-video major class with an unknown minor class: the name filter
        // is the backstop for headsets/headphones/etc.
        assertFalse(device("Sony Headphones", BluetoothClass(0x0400)).isHidCompatibleHost())
        assertTrue(device("Living Room TV", BluetoothClass(0x0400)).isHidCompatibleHost())
    }

    // --- connection guards / HID transmission while disconnected ---

    @Test
    fun connectHost_returnsFalseWhenAppNotRegistered() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = BluetoothHidManager.getInstance(context)

        // The HID profile never becomes ready under Robolectric, so the app is
        // never registered: connectHost must stash a pending connection and
        // report failure instead of crashing.
        assertFalse(manager.connectHost(device("My PC", BluetoothClass(0x0100))))
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
