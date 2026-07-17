package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

class AirMouseWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality upon enabling the first widget
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality upon last widget being disabled
    }

    companion object {
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

            // Get battery level
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            // Update status text
            val statusText = if (isBluetoothOn) {
                "Bluetooth ON • $batteryLevel%"
            } else {
                "Bluetooth OFF • $batteryLevel%"
            }
            views.setTextViewText(R.id.widget_status, statusText)

            // Update status color based on Bluetooth state
            val statusColor = if (isBluetoothOn) {
                context.getColor(android.R.color.holo_green_dark)
            } else {
                context.getColor(android.R.color.holo_red_dark)
            }
            views.setTextColor(R.id.widget_status, statusColor)

            // Update button text
            views.setTextViewText(R.id.widget_button, if (isBluetoothOn) "Open" else "Enable BT")

            // Set up the click intent to open the app
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_icon, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_status, pendingIntent)

            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
