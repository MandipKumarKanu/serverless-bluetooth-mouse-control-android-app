package com.example.tile

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity

class AirMouseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        // Open the app when tile is clicked
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
    }

    @SuppressLint("MissingPermission")
    private fun updateTileState() {
        val qsTile = qsTile ?: return

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        val isBluetoothOn = bluetoothManager?.adapter?.isEnabled == true

        qsTile.state = if (isBluetoothOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.label = if (isBluetoothOn) "AirMouse" else "AirMouse (BT Off)"
        qsTile.subtitle = if (isBluetoothOn) "Tap to open" else "Enable Bluetooth first"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            qsTile.stateDescription = if (isBluetoothOn) "Bluetooth enabled" else "Bluetooth disabled"
        }

        qsTile.updateTile()
    }
}
