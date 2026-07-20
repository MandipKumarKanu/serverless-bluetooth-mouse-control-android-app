package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

/**
 * BLE GATT Battery Service — exposes phone battery level to connected hosts
 * via standard Bluetooth Battery Service (UUID 0x180F).
 *
 * This is the same mechanism Bluetooth earphones use to show battery
 * in the host's Bluetooth settings.
 */
class BleBatteryService(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var isRunning = false
    private var currentBatteryLevel = 0

    private val connectedDevices = mutableSetOf<BluetoothDevice>()

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            if (device == null) return
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevices.add(device)
                    Log.d(TAG, "BLE GATT client connected: ${device.getSafeName()}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevices.remove(device)
                    Log.d(TAG, "BLE GATT client disconnected: ${device.getSafeName()}")
                    if (connectedDevices.isEmpty()) {
                        Log.d(TAG, "No BLE clients remaining, stopping GATT server")
                        stop()
                    }
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: android.bluetooth.BluetoothGattCharacteristic?
        ) {
            if (characteristic?.uuid == BATTERY_LEVEL_UUID) {
                val value = byteArrayOf(currentBatteryLevel.toByte())
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    value
                )
                Log.d(TAG, "Battery read request from ${device?.getSafeName()}: $currentBatteryLevel%")
            } else {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_FAILURE,
                    0,
                    null
                )
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            descriptor: android.bluetooth.BluetoothGattDescriptor?
        ) {
            // Client Characteristic Configuration descriptor — return enabled
            if (descriptor?.uuid == CCC_DESCRIPTOR_UUID) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                )
            } else {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_FAILURE,
                    0,
                    null
                )
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: android.bluetooth.BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "BLE advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertising failed with error code: $errorCode")
            isRunning = false
        }
    }

    fun start() {
        if (isRunning) return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        try {
            // Create GATT server
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            if (gattServer == null) {
                Log.e(TAG, "Failed to open GATT server")
                return
            }

            // Build Battery Service with Battery Level characteristic
            val batteryService = BluetoothGattService(BATTERY_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

            val batteryLevelChar = android.bluetooth.BluetoothGattCharacteristic(
                BATTERY_LEVEL_UUID,
                android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ or
                        android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ
            )

            val cccDescriptor = android.bluetooth.BluetoothGattDescriptor(
                CCC_DESCRIPTOR_UUID,
                android.bluetooth.BluetoothGattDescriptor.PERMISSION_READ or
                        android.bluetooth.BluetoothGattDescriptor.PERMISSION_WRITE
            )
            batteryLevelChar.addDescriptor(cccDescriptor)

            batteryService.addCharacteristic(batteryLevelChar)
            gattServer?.addService(batteryService)

            // Start BLE advertising so hosts can discover the battery service
            advertiser = adapter.bluetoothLeAdvertiser
            if (advertiser != null) {
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setConnectable(true)
                    .setTimeout(0) // No timeout
                    .build()

                val data = AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .setIncludeTxPowerLevel(false)
                    .addServiceUuid(ParcelUuid(BATTERY_SERVICE_UUID))
                    .build()

                advertiser?.startAdvertising(settings, data, advertiseCallback)
            } else {
                Log.w(TAG, "BLE advertiser not available")
            }

            isRunning = true
            Log.d(TAG, "BLE Battery Service started")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting BLE Battery Service", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE Battery Service", e)
        }
    }

    fun stop() {
        if (!isRunning) return
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Exception) {
        }
        try {
            gattServer?.close()
        } catch (_: Exception) {
        }
        gattServer = null
        advertiser = null
        connectedDevices.clear()
        isRunning = false
        Log.d(TAG, "BLE Battery Service stopped")
    }

    @SuppressLint("MissingPermission")
    fun updateBatteryLevel(level: Int) {
        currentBatteryLevel = level.coerceIn(0, 100)

        // Notify all subscribed clients
        val characteristic = gattServer
            ?.getService(BATTERY_SERVICE_UUID)
            ?.getCharacteristic(BATTERY_LEVEL_UUID) ?: return

        characteristic.value = byteArrayOf(currentBatteryLevel.toByte())

        for (device in connectedDevices) {
            try {
                gattServer?.notifyCharacteristicChanged(device, characteristic, false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify battery level to ${device.getSafeName()}", e)
            }
        }
    }

    fun isRunning(): Boolean = isRunning

    companion object {
        private const val TAG = "BleBatteryService"

        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
