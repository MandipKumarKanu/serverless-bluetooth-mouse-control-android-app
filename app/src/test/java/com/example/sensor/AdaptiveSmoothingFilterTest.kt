package com.example.sensor

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AdaptiveSmoothingFilterTest {

    @Test
    fun `filter converges to a constant input`() {
        val filter = AdaptiveSmoothingFilter()
        var result = 0f to 0f
        repeat(200) {
            result = filter.filter(10f, 0f, 0.3f)
        }
        assertTrue(abs(result.first - 10f) < 0.1f)
        assertTrue(abs(result.second) < 0.1f)
    }

    @Test
    fun `zero input decays back to zero`() {
        val filter = AdaptiveSmoothingFilter()
        filter.filter(10f, 0f, 0.3f)
        var result = 10f to 0f
        repeat(200) {
            result = filter.filter(0f, 0f, 0.3f)
        }
        assertTrue(abs(result.first) < 0.1f)
    }

    @Test
    fun `fast input passes through more than slow input`() {
        val filter = AdaptiveSmoothingFilter()
        // Reach steady state at slow speed, then jump to a fast value
        repeat(50) { filter.filter(0.2f, 0f, 0.3f) }
        val fast = filter.filter(50f, 0f, 0.3f)
        assertTrue("expected >25f but was ${fast.first}", fast.first > 25f)
    }

    @Test
    fun `higher base smoothing damps movement more`() {
        val light = AdaptiveSmoothingFilter()
        val heavy = AdaptiveSmoothingFilter()
        val lightOut = light.filter(5f, 0f, 0.1f)
        val heavyOut = heavy.filter(5f, 0f, 0.9f)
        assertTrue(lightOut.first > heavyOut.first)
    }
}
