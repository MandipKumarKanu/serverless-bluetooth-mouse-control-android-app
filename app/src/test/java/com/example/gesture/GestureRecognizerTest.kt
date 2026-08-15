package com.example.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GestureRecognizerTest {

    private val recognizer = GestureRecognizer()

    @Test
    fun `recognize returns null for too few points`() {
        val points = listOf(GesturePoint(0f, 0f), GesturePoint(10f, 10f))
        assertNull(recognizer.recognize(points, emptyMap()))
    }

    @Test
    fun `recognize returns null when there are no templates`() {
        val points = (0..100).map { GesturePoint(it * 2f, 50f) }
        assertNull(recognizer.recognize(points, emptyMap()))
    }

    @Test
    fun `recognize matches a horizontal line template`() {
        val template = (0..100).map { GesturePoint(it * 2f, 50f) }
        val templates = mapOf("line" to template)

        // Same line drawn with slight variation (offset + small jitter)
        val drawn = (0..100).map { GesturePoint(it * 2f + 5f, 48f + (it % 5)) }
        val result = recognizer.recognize(drawn, templates)

        assertNotNull(result)
        assertEquals("line", result?.first)
        assert(result?.second ?: 0.0 > 0.65)
    }

    @Test
    fun `recognize prefers the best matching template`() {
        val line = (0..100).map { GesturePoint(it * 2f, 50f) }
        val circle = (0..100).map {
            val angle = it / 100.0 * 2.0 * Math.PI
            GesturePoint((50 + 40 * Math.cos(angle)).toFloat(), (50 + 40 * Math.sin(angle)).toFloat())
        }
        val templates = mapOf("line" to line, "circle" to circle)

        val result = recognizer.recognize(line, templates)
        assertNotNull(result)
        assertEquals("line", result?.first)
    }
}
