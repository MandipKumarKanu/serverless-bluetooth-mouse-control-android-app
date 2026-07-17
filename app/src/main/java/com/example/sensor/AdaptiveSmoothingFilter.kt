package com.example.sensor

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sign
import kotlin.math.sqrt

class AdaptiveSmoothingFilter {
    // Previous values for smoothing and prediction
    private var prevDx = 0f
    private var prevDy = 0f
    private var prevPrevDx = 0f
    private var prevPrevDy = 0f
    private var prevSmoothing = 0.3f

    // User's tremor frequency (calibrated)
    private var userTremorFreq = 4f // Default 4Hz (typical hand tremor)

    // Calibration state
    private var calibrationSamples = mutableListOf<Pair<Float, Float>>()
    private var isCalibrating = false
    private val maxCalibrationSamples = 100

    /**
     * Apply adaptive filtering to raw gyroscope data.
     * Returns smoothed (dx, dy) values.
     */
    fun filter(rawDx: Float, rawDy: Float, baseSmoothing: Float = 0.3f): Pair<Float, Float> {
        val speedX = abs(rawDx)
        val speedY = abs(rawDy)
        val speed = sqrt(rawDx * rawDx + rawDy * rawDy)

        // 1. Adaptive smoothing based on speed relative to tremor frequency
        val targetSmoothing = when {
            speed < userTremorFreq * 0.5f -> 0.85f  // Very slow: strong filter (kills tremor)
            speed < userTremorFreq -> 0.6f           // Near tremor: moderate filter
            speed < userTremorFreq * 3f -> 0.3f     // Medium: light filter
            speed < userTremorFreq * 5f -> 0.15f    // Fast: minimal filter
            else -> 0.05f                            // Very fast: almost none
        }

        // 2. Smooth transition (hysteresis) to prevent jarring mode switches
        val smoothFactor = prevSmoothing * 0.8f + targetSmoothing * 0.2f
        prevSmoothing = smoothFactor

        // 3. Apply low-pass filter with adaptive factor
        val sf = (1.0f - smoothFactor).coerceIn(0.05f, 1.0f)
        val filteredDx = prevDx * sf + rawDx * (1.0f - sf)
        val filteredDy = prevDy * sf + rawDy * (1.0f - sf)

        // 4. Predictive component (velocity-based prediction)
        val velocityX = prevDx - prevPrevDx
        val velocityY = prevDy - prevPrevDy
        val predictedDx = prevDx + velocityX * 0.3f
        val predictedDy = prevDy + velocityY * 0.3f

        // 5. Blend prediction with filtered based on speed
        // Faster movement = more prediction (reduces lag)
        val predictionWeight = (speed / 100f).coerceIn(0f, 0.4f)
        val finalDx = filteredDx * (1 - predictionWeight) + predictedDx * predictionWeight
        val finalDy = filteredDy * (1 - predictionWeight) + predictedDy * predictionWeight

        // Update history for next iteration
        prevPrevDx = prevDx
        prevPrevDy = prevDy
        prevDx = finalDx
        prevDy = finalDy

        return Pair(finalDx, finalDy)
    }

    /**
     * Start calibration to detect user's tremor frequency.
     */
    fun startCalibration() {
        isCalibrating = true
        calibrationSamples.clear()
        Log.d(TAG, "Starting adaptive filter calibration")
    }

    /**
     * Add a sample during calibration.
     */
    fun addCalibrationSample(rawDx: Float, rawDy: Float) {
        if (!isCalibrating) return

        calibrationSamples.add(Pair(rawDx, rawDy))

        if (calibrationSamples.size >= maxCalibrationSamples) {
            finishCalibration()
        }
    }

    /**
     * Finish calibration and compute tremor frequency.
     */
    private fun finishCalibration() {
        if (calibrationSamples.isEmpty()) return

        // Analyze tremor frequency using zero-crossing rate
        var zeroCrossings = 0
        var prevSign = 0f

        for ((dx, _) in calibrationSamples) {
            val currentSign = dx
            if (prevSign != 0f && currentSign * prevSign < 0) {
                zeroCrossings++
            }
            prevSign = currentSign
        }

        // Estimate frequency (samples / time)
        // Assuming ~50Hz sensor rate (SENSOR_DELAY_GAME)
        val duration = calibrationSamples.size / 50f // seconds
        val frequency = zeroCrossings / duration / 2f // Hz (divide by 2 for full cycles)

        // Clamp to reasonable range (1-10 Hz)
        userTremorFreq = frequency.coerceIn(1f, 10f)

        isCalibrating = false
        calibrationSamples.clear()

        Log.d(TAG, "Calibration complete: tremor frequency = ${userTremorFreq}Hz")
    }

    /**
     * Check if calibration is in progress.
     */
    fun isCalibrating(): Boolean = isCalibrating

    /**
     * Get current tremor frequency.
     */
    fun getTremorFrequency(): Float = userTremorFreq

    /**
     * Reset filter state.
     */
    fun reset() {
        prevDx = 0f
        prevDy = 0f
        prevPrevDx = 0f
        prevPrevDy = 0f
        prevSmoothing = 0.3f
    }

    companion object {
        private const val TAG = "AdaptiveSmoothingFilter"
    }
}
