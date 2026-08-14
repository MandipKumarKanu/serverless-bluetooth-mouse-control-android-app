package com.example.sensor

import android.util.Log
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
     * Returns smoothed (dx, dy) values with low-pass filtering on slow movements
     * and high responsiveness on fast sweeps.
     */
    fun filter(rawDx: Float, rawDy: Float, baseSmoothing: Float = 0.3f): Pair<Float, Float> {
        val speed = sqrt(rawDx * rawDx + rawDy * rawDy)

        // Alpha determines the weight of the NEW raw sensor sample:
        // - Lower alpha (e.g. 0.15): Strong low-pass filter for slow movements (removes hand jitter/shake completely)
        // - Higher alpha (e.g. 0.90): Direct raw passthrough for fast sweeps (zero latency)
        val targetAlpha = when {
            speed < 0.5f -> 0.15f   // Very slow precision targeting: heavy smoothing
            speed < 2.0f -> 0.28f   // Normal movement: balanced smoothness
            speed < 6.0f -> 0.55f   // Moderate movement: responsive
            speed < 12.0f -> 0.80f  // Fast movement: near zero lag
            else -> 0.95f           // Rapid sweep: instant raw output
        }

        // Apply user preference baseSmoothing modifier (higher smoothing = lower alpha)
        val alphaModifier = (1.0f - (baseSmoothing - 0.3f) * 0.5f).coerceIn(0.5f, 1.5f)
        val finalAlpha = (targetAlpha * alphaModifier).coerceIn(0.08f, 0.98f)

        // Exponential Moving Average filter formula
        val filteredDx = prevDx + finalAlpha * (rawDx - prevDx)
        val filteredDy = prevDy + finalAlpha * (rawDy - prevDy)

        // Update history
        prevDx = filteredDx
        prevDy = filteredDy

        return Pair(filteredDx, filteredDy)
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
