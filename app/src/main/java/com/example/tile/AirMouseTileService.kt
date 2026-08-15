package com.example.tile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.bluetooth.BluetoothHidManager
import com.example.service.AirMouseService

class AirMouseTileService : TileService() {

    private val hidManager by lazy { BluetoothHidManager.getInstance(this) }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        // Not connected -> open the app so the user can pick a host
        if (!hidManager.isConnected()) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
            return
        }

        // Connected -> toggle the air mouse streaming
        AirMouseService.isAirMouseActive = !AirMouseService.isAirMouseActive
        val action = if (AirMouseService.isAirMouseActive) {
            AirMouseService.ACTION_START_AIR_MOUSE
        } else {
            AirMouseService.ACTION_STOP_AIR_MOUSE
        }
        val intent = Intent(this, AirMouseService::class.java).apply { this.action = action }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Exception) {
        }
        updateTileState()
    }

    private fun updateTileState() {
        val qsTile = qsTile ?: return

        val isConnected = hidManager.isConnected()
        val isActive = AirMouseService.isAirMouseActive

        qsTile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.label = if (isActive) "Stop Mouse" else "AirMouse"
        qsTile.subtitle = when {
            isConnected && isActive -> "Air mouse streaming"
            isConnected -> "Tap to start mouse"
            else -> "Not connected - tap to open"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            qsTile.stateDescription = if (isActive) "Air mouse active" else "Air mouse off"
        }

        qsTile.updateTile()
    }
}
