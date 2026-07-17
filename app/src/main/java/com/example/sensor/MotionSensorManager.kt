package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.bluetooth.BluetoothHidManager
import com.example.data.SettingsEntity
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

class MotionSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val hidManager = BluetoothHidManager.getInstance(context)

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

        // Also start adaptive filter calibration
        adaptiveFilter.startCalibration()

        Log.d(TAG, "Starting motion sensor calibration")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        // Raw angular velocity around X, Y, Z axes (rad/s)
        var rawX = event.values[0] // Rotation around X-axis (Pitch - vertical move)
        var rawY = event.values[1] // Rotation around Y-axis (Yaw - horizontal move)

        if (isCalibrating) {
            accumBiasX += rawX
            accumBiasY += rawY
            calibrationSamplesCount++

            // Add sample to adaptive filter calibration
            adaptiveFilter.addCalibrationSample(rawX, rawY)

            if (calibrationSamplesCount >= maxCalibrationSamples) {
                biasX = accumBiasX / maxCalibrationSamples
                biasY = accumBiasY / maxCalibrationSamples
                isCalibrating = false
                Log.d(TAG, "Calibration complete: biasX=$biasX, biasY=$biasY, tremorFreq=${adaptiveFilter.getTremorFrequency()}Hz")
            }
            return
        }

        // Apply Calibration Offset (drift elimination)
        rawX -= biasX
        rawY -= biasY

        // Map phone rotation to relative mouse pointer changes
        // - Rotating around Y-axis (rawY) controls X position (left/right)
        // - Rotating around X-axis (rawX) controls Y position (up/down)
        var targetDx = -rawY * 180f / Math.PI.toFloat() // Convert to degrees/s roughly
        var targetDy = -rawX * 180f / Math.PI.toFloat()

        // Apply Inversion
        if (settings.invertX) targetDx = -targetDx
        if (settings.invertY) targetDy = -targetDy

        // Apply Dead Zone Check
        val speedX = abs(targetDx)
        val speedY = abs(targetDy)

        val deadZ = settings.deadZone * 10f // amplify dead zone factor

        val finalDxRaw = if (speedX < deadZ) 0f else targetDx
        val finalDyRaw = if (speedY < deadZ) 0f else targetDy

        // Apply Adaptive Smoothing (10/10 implementation)
        val (smoothedDx, smoothedDy) = adaptiveFilter.filter(
            rawDx = finalDxRaw,
            rawDy = finalDyRaw,
            baseSmoothing = settings.smoothing
        )

        if (smoothedDx == 0f && smoothedDy == 0f) return

        // Apply Cursor Sensitivity
        var finalDx = smoothedDx * settings.sensitivity * 0.8f
        var finalDy = smoothedDy * settings.sensitivity * 0.8f

        // Apply Pointer Acceleration (logarithmic curve for natural feel)
        val currentSpeed = abs(finalDx) + abs(finalDy)
        if (currentSpeed > 1.0f) {
            // Logarithmic acceleration: more natural than linear
            val accelMultiplier = 1.0f + ln(currentSpeed / 10f + 1f) * settings.acceleration * 0.3f
            finalDx *= accelMultiplier
            finalDy *= accelMultiplier
        }

        // Clamp values to Byte range (-127 to +127) for Bluetooth HID Mouse report specifications
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
