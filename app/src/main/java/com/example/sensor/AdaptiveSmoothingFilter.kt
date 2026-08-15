package com.example.sensor

import kotlin.math.sqrt

/**
 * Speed-adaptive low-pass filter for gyroscope-derived mouse movement.
 * Applies heavy smoothing to slow (tremor-prone) movement and near-pass-through
 * alpha to fast sweeps so the cursor stays responsive.
 */
class AdaptiveSmoothingFilter {
    // Previous filtered values (EMA state)
    private var prevDx = 0f
    private var prevDy = 0f

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
}
