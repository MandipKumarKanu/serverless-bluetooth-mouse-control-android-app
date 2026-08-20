package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.bluetooth.HidDeviceManager
import com.example.data.SettingsEntity
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

class MotionSensorManager(
    context: Context,
    private val hidManager: HidDeviceManager
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var isRunning = false

    // Configurable Settings (synced from Room db)
    private var settings = SettingsEntity()

    // Bias calibration values (for drift elimination)
    private var biasX = 0f
    private var biasY = 0f
    private var isCalibrating = false
    private var calibrationSamplesCount = 0
    private val maxCalibrationSamples = 100
    private var accumBiasX = 0f
    private var accumBiasY = 0f

    // Adaptive smoothing filter (10/10 implementation)
    private val adaptiveFilter = AdaptiveSmoothingFilter()

    // Mouse buttons current state (retained to prevent releasing buttons during motion)
    private var activeButtons: Byte = 0

    companion object {
        private const val TAG = "MotionSensorManager"
    }

    fun updateSettings(newSettings: SettingsEntity) {
        this.settings = newSettings
    }

    fun start(buttonsState: Byte = 0) {
        if (isRunning) return
        activeButtons = buttonsState

        gyroscope?.let { gyro ->
            sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
            isRunning = true
            Log.d(TAG, "Motion sensor started successfully")
        } ?: Log.e(TAG, "Gyroscope sensor not available on this device!")
    }

    fun stop() {
        if (!isRunning) return
        sensorManager.unregisterListener(this)
        isRunning = false
        Log.d(TAG, "Motion sensor stopped")
    }

    fun setButtonsState(buttons: Byte) {
        activeButtons = buttons
    }

    fun calibrate() {
        if (isCalibrating) return
        isCalibrating = true
        calibrationSamplesCount = 0
        accumBiasX = 0f
        accumBiasY = 0f

        Log.d(TAG, "Starting motion sensor calibration")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        // Raw angular velocity around X, Y, Z axes (rad/s)
        // - X-axis (values[0]): Pitch (tilting nose up/down -> vertical move)
        // - Y-axis (values[1]): Roll (wrist twisting)
        // - Z-axis (values[2]): Yaw (pointing left/right)
        val rawX = event.values[0]
        val rawY = event.values[1]
        val rawZ = event.values[2]

        // Combine Z (Yaw) and Y (Roll) for natural, 360-degree fluid horizontal tracking
        var rawVert = -rawX
        var rawHoriz = -(rawZ * 0.75f + rawY * 0.50f)

        if (isCalibrating) {
            accumBiasX += rawVert
            accumBiasY += rawHoriz
            calibrationSamplesCount++

            if (calibrationSamplesCount >= maxCalibrationSamples) {
                biasX = accumBiasX / maxCalibrationSamples
                biasY = accumBiasY / maxCalibrationSamples
                isCalibrating = false
                Log.d(TAG, "Calibration complete: biasX=$biasX, biasY=$biasY")
            }
            return
        }

        // Apply Calibration Offset (drift elimination)
        rawVert -= biasX
        rawHoriz -= biasY

        // Map phone rotation to relative mouse pointer changes (in deg/s)
        var targetDx = rawHoriz * 180f / Math.PI.toFloat()
        var targetDy = rawVert * 180f / Math.PI.toFloat()

        // Apply Inversion
        if (settings.invertX) targetDx = -targetDx
        if (settings.invertY) targetDy = -targetDy

        // Apply Dead Zone Check
        val deadZ = settings.deadZone * 2.5f
        val finalDxRaw = if (abs(targetDx) < deadZ) 0f else targetDx
        val finalDyRaw = if (abs(targetDy) < deadZ) 0f else targetDy

        // Apply Adaptive Smoothing Filter
        val (smoothedDx, smoothedDy) = adaptiveFilter.filter(
            rawDx = finalDxRaw,
            rawDy = finalDyRaw,
            baseSmoothing = settings.smoothing
        )

        if (smoothedDx == 0f && smoothedDy == 0f) return

        // Apply Cursor Sensitivity
        var finalDx = smoothedDx * settings.sensitivity * 1.3f
        var finalDy = smoothedDy * settings.sensitivity * 1.3f

        // Apply Pointer Acceleration (smooth natural boost for flicks)
        val currentSpeed = sqrt(finalDx * finalDx + finalDy * finalDy)
        if (currentSpeed > 1.5f) {
            val accelMultiplier = 1.0f + ln(currentSpeed / 1.5f + 1.0f) * settings.acceleration * 0.2f
            finalDx *= accelMultiplier
            finalDy *= accelMultiplier
        }

        // Clamp values to Byte range (-127 to +127) for Bluetooth HID Mouse report
        val reportDx = finalDx.coerceIn(-127f, 127f).toInt().toByte()
        val reportDy = finalDy.coerceIn(-127f, 127f).toInt().toByte()

        // Send over Bluetooth HID if there is motion
        if (reportDx != 0.toByte() || reportDy != 0.toByte()) {
            hidManager.sendMouseInput(
                buttons = activeButtons,
                dx = reportDx,
                dy = reportDy,
                scroll = 0
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Unused for now
    }
}
