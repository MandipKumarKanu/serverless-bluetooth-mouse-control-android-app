package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.bluetooth.BluetoothHidManager
import com.example.bluetooth.getSafeName
import com.example.sensor.BatteryMonitor
import com.example.sensor.MotionSensorManager
import com.example.data.AppDatabase
import com.example.data.SettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AirMouseService : Service() {

    private val hidManager by lazy { BluetoothHidManager.getInstance(this) }
    private var sensorManager: MotionSensorManager? = null
    private lateinit var batteryMonitor: BatteryMonitor
    private var batteryUpdateJob: Job? = null

    // Service state
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    // Broadcast receiver for notification media actions
    private val mediaActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_MEDIA_PLAY_PAUSE -> {
                    hidManager.sendConsumerInput(0x08)
                    Log.d(TAG, "Media: Play/Pause")
                }
                ACTION_MEDIA_NEXT -> {
                    hidManager.sendConsumerInput(0x10)
                    Log.d(TAG, "Media: Next Track")
                }
                ACTION_MEDIA_PREV -> {
                    hidManager.sendConsumerInput(0x20)
                    Log.d(TAG, "Media: Previous Track")
                }
                ACTION_MEDIA_VOL_DOWN -> {
                    hidManager.sendConsumerInput(0x02)
                    Log.d(TAG, "Media: Volume Down")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager = MotionSensorManager(this)
        batteryMonitor = BatteryMonitor(this)
        batteryMonitor.start()

        // Register media action receiver
        val filter = IntentFilter().apply {
            addAction(ACTION_MEDIA_PLAY_PAUSE)
            addAction(ACTION_MEDIA_NEXT)
            addAction(ACTION_MEDIA_PREV)
            addAction(ACTION_MEDIA_VOL_DOWN)
        }
        registerReceiver(mediaActionReceiver, filter)

        // Update notification when battery level changes
        batteryUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            batteryMonitor.batteryLevel.collect {
                updateNotification(_connectedDeviceName.value)
            }
        }

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
        batteryUpdateJob?.cancel()
        batteryMonitor.stop()
        try {
            unregisterReceiver(mediaActionReceiver)
        } catch (_: Exception) {
        }
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

        val batteryLevel = batteryMonitor.batteryLevel.value
        val isCharging = batteryMonitor.isCharging.value
        val batteryIcon = if (isCharging) "\u26A1" else "\uD83D\uDD0B"

        val statusText = if (isAirMouseActive) {
            if (deviceName != null) "Air Mouse Active \u2022 $deviceName" else "Air Mouse Active"
        } else {
            if (deviceName != null) "Connected to $deviceName" else "AirMouse Ready"
        }

        val subText = "$batteryIcon Battery: $batteryLevel%${if (isCharging) " (Charging)" else ""}"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AirMouse")
            .setContentText(statusText)
            .setSubText(subText)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Add media + air mouse action buttons when connected
        if (hidManager.isConnected()) {
            // Play / Pause
            val playPauseIntent = Intent(ACTION_MEDIA_PLAY_PAUSE)
            val playPausePending = PendingIntent.getBroadcast(
                this, 10, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_app_logo, "Play", playPausePending)

            // Next Track
            val nextIntent = Intent(ACTION_MEDIA_NEXT)
            val nextPending = PendingIntent.getBroadcast(
                this, 11, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_app_logo, "Next", nextPending)

            // Volume Down
            val volDownIntent = Intent(ACTION_MEDIA_VOL_DOWN)
            val volDownPending = PendingIntent.getBroadcast(
                this, 12, volDownIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_app_logo, "Vol-", volDownPending)

            // Air Mouse Toggle
            val airMouseAction = if (isAirMouseActive) ACTION_STOP_AIR_MOUSE else ACTION_START_AIR_MOUSE
            val airMouseLabel = if (isAirMouseActive) "Stop Mouse" else "Start Mouse"
            val airMouseIntent = Intent(this, AirMouseService::class.java).apply {
                action = airMouseAction
            }
            val airMousePending = PendingIntent.getService(
                this, 13, airMouseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_app_logo, airMouseLabel, airMousePending)
        }

        return builder.build()
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

        // Notification media action broadcasts
        const val ACTION_MEDIA_PLAY_PAUSE = "com.example.service.MEDIA_PLAY_PAUSE"
        const val ACTION_MEDIA_NEXT = "com.example.service.MEDIA_NEXT"
        const val ACTION_MEDIA_PREV = "com.example.service.MEDIA_PREV"
        const val ACTION_MEDIA_VOL_DOWN = "com.example.service.MEDIA_VOL_DOWN"

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
