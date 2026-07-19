package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import com.example.MainActivity
import com.example.R
import com.example.bluetooth.BluetoothHidManager
import com.example.service.AirMouseService

class AirMouseWidgetReceiver : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        Log.d("AirMouseWidgetReceiver", "onReceive: action = $action")
        
        when (action) {
            BluetoothAdapter.ACTION_STATE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            "android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED" -> {
                updateAllWidgets(context)
            }
            ACTION_TOGGLE_BT -> {
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val bluetoothAdapter = bluetoothManager?.adapter
                if (bluetoothAdapter != null) {
                    if (!bluetoothAdapter.isEnabled) {
                        try {
                            @Suppress("DEPRECATION")
                            val success = bluetoothAdapter.enable()
                            if (!success) {
                                // If programmatic enable is restricted (Android 13+), open the app to request it
                                val appIntent = Intent(context, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(appIntent)
                                Toast.makeText(context, "Opening AirMouse to enable Bluetooth", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Enabling Bluetooth...", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: SecurityException) {
                            // Permission or restriction issue, launch the app as fallback
                            val appIntent = Intent(context, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(appIntent)
                            Toast.makeText(context, "Please enable Bluetooth in the app", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                updateAllWidgets(context)
            }
            ACTION_START_MOUSE -> {
                // Activate Air Mouse
                AirMouseService.isAirMouseActive = true
                
                // Send start intent to service
                val serviceIntent = Intent(context, AirMouseService::class.java).apply {
                    this.action = AirMouseService.ACTION_START_AIR_MOUSE
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Toast.makeText(context, "Air Mouse activated", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("AirMouseWidgetReceiver", "Failed to start service: ${e.message}")
                }
                updateAllWidgets(context)
            }
            ACTION_STOP_MOUSE -> {
                // Deactivate Air Mouse
                AirMouseService.isAirMouseActive = false
                
                // Send stop intent to service
                val serviceIntent = Intent(context, AirMouseService::class.java).apply {
                    this.action = AirMouseService.ACTION_STOP_AIR_MOUSE
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Toast.makeText(context, "Air Mouse deactivated", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("AirMouseWidgetReceiver", "Failed to stop service: ${e.message}")
                }
                updateAllWidgets(context)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val ACTION_TOGGLE_BT = "com.example.widget.ACTION_TOGGLE_BT"
        const val ACTION_START_MOUSE = "com.example.widget.ACTION_START_MOUSE"
        const val ACTION_STOP_MOUSE = "com.example.widget.ACTION_STOP_MOUSE"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, AirMouseWidgetReceiver::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            // Get Bluetooth status
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter
            val isBluetoothOn = bluetoothAdapter?.isEnabled == true

            // Get connection status
            val hidManager = BluetoothHidManager.getInstance(context)
            val isConnected = isBluetoothOn && hidManager.isConnected()
            val connectedDeviceName = if (isConnected) {
                hidManager.connectedDevice.value?.name ?: "Connected Device"
            } else {
                null
            }

            // Determine state and configure widget UI
            if (!isBluetoothOn) {
                // State 1: Bluetooth OFF
                views.setTextViewText(R.id.widget_status, "Bluetooth is OFF")
                views.setTextColor(R.id.widget_status, 0xFFEF4444.toInt()) // Red (Tailwind slate-500 equivalent/error red)
                
                views.setTextViewText(R.id.widget_button, "Turn ON")
                
                // Button PendingIntent to turn on BT
                val btIntent = Intent(context, AirMouseWidgetReceiver::class.java).apply {
                    action = ACTION_TOGGLE_BT
                }
                val btPendingIntent = PendingIntent.getBroadcast(
                    context,
                    1,
                    btIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_button, btPendingIntent)
                
            } else if (!isConnected) {
                // State 2: Bluetooth ON, but not connected
                views.setTextViewText(R.id.widget_status, "Not Connected")
                views.setTextColor(R.id.widget_status, 0xFF94A3B8.toInt()) // Gray
                
                views.setTextViewText(R.id.widget_button, "Connect Device")
                
                // Button PendingIntent to open application
                val appIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val appPendingIntent = PendingIntent.getActivity(
                    context,
                    2,
                    appIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_button, appPendingIntent)
                
            } else {
                // State 3: Bluetooth ON and connected to host
                val isAirMouseActive = AirMouseService.isAirMouseActive
                
                if (isAirMouseActive) {
                    views.setTextViewText(R.id.widget_status, "Active • $connectedDeviceName")
                    views.setTextColor(R.id.widget_status, 0xFF06B6D4.toInt()) // Cyan
                    
                    views.setTextViewText(R.id.widget_button, "Deactivate")
                    
                    // Button PendingIntent to stop Air Mouse
                    val stopIntent = Intent(context, AirMouseWidgetReceiver::class.java).apply {
                        action = ACTION_STOP_MOUSE
                    }
                    val stopPendingIntent = PendingIntent.getBroadcast(
                        context,
                        4,
                        stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_button, stopPendingIntent)
                } else {
                    views.setTextViewText(R.id.widget_status, "Connected • $connectedDeviceName")
                    views.setTextColor(R.id.widget_status, 0xFF10B981.toInt()) // Green
                    
                    views.setTextViewText(R.id.widget_button, "Start Air Mouse")
                    
                    // Button PendingIntent to start Air Mouse
                    val startIntent = Intent(context, AirMouseWidgetReceiver::class.java).apply {
                        action = ACTION_START_MOUSE
                    }
                    val startPendingIntent = PendingIntent.getBroadcast(
                        context,
                        3,
                        startIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_button, startPendingIntent)
                }
            }

            // General PendingIntent to open the app when tapping the icon/title/status
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_icon, mainPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_title, mainPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_status, mainPendingIntent)

            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
