package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.bluetooth.BluetoothHidManager
import com.example.bluetooth.getSafeName
import com.example.sensor.MotionSensorManager
import com.example.data.AppDatabase
import com.example.data.SettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AirMouseService : Service() {

    private val hidManager by lazy { BluetoothHidManager.getInstance(this) }
    private var sensorManager: MotionSensorManager? = null

    // Service state
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager = MotionSensorManager(this)
        Log.d(TAG, "AirMouseService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
                startForegroundService(deviceName)
            }
            ACTION_STOP -> {
                stopForegroundService()
            }
            ACTION_UPDATE -> {
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
                updateNotification(deviceName)
            }
            ACTION_START_AIR_MOUSE -> {
                isAirMouseActive = true
                startAirMouseSensors()
                updateNotification(_connectedDeviceName.value)
            }
            ACTION_STOP_AIR_MOUSE -> {
                isAirMouseActive = false
                stopAirMouseSensors()
                updateNotification(_connectedDeviceName.value)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isAirMouseActive = false
        stopAirMouseSensors()
        _isRunning.value = false
        Log.d(TAG, "AirMouseService destroyed")
    }

    private fun startAirMouseSensors() {
        if (sensorManager == null) {
            sensorManager = MotionSensorManager(this)
        }
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val db = AppDatabase.getDatabase(this@AirMouseService, scope)
            val settings = db.airMouseDao().getSettingsDirect() ?: SettingsEntity()
            sensorManager?.updateSettings(settings)
            sensorManager?.start(0)
            Log.d(TAG, "Sensor manager started inside Service")
        }
    }

    private fun stopAirMouseSensors() {
        sensorManager?.stop()
        Log.d(TAG, "Sensor manager stopped inside Service")
    }

    private fun startForegroundService(deviceName: String?) {
        _isRunning.value = true
        _connectedDeviceName.value = deviceName

        val notification = createNotification(deviceName)
        startForeground(NOTIFICATION_ID, notification)

        Log.d(TAG, "Foreground service started, connected to: $deviceName")
    }

    private fun stopForegroundService() {
        isAirMouseActive = false
        stopAirMouseSensors()
        _isRunning.value = false
        _connectedDeviceName.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Foreground service stopped")
    }

    private fun updateNotification(deviceName: String?) {
        _connectedDeviceName.value = deviceName
        val notification = createNotification(deviceName)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AirMouse Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains Bluetooth connection when app is minimized"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(deviceName: String?): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isAirMouseActive) {
            if (deviceName != null) "Air Mouse Active • $deviceName" else "Air Mouse Active"
        } else {
            if (deviceName != null) "Connected to $deviceName" else "AirMouse Ready"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AirMouse")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "AirMouseService"
        private const val CHANNEL_ID = "airmouse_service_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.START"
        const val ACTION_STOP = "com.example.service.STOP"
        const val ACTION_UPDATE = "com.example.service.UPDATE"
        const val ACTION_START_AIR_MOUSE = "com.example.service.START_AIR_MOUSE"
        const val ACTION_STOP_AIR_MOUSE = "com.example.service.STOP_AIR_MOUSE"
        const val EXTRA_DEVICE_NAME = "device_name"

        var isAirMouseActive = false

        fun startService(context: Context, deviceName: String? = null) {
            val intent = Intent(context, AirMouseService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DEVICE_NAME, deviceName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AirMouseService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }

        fun updateService(context: Context, deviceName: String?) {
            val intent = Intent(context, AirMouseService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_DEVICE_NAME, deviceName)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
