package com.example.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureSerializationTest {

    @Test
    fun `round trip preserves points`() {
        val points = listOf(
            GesturePoint(1.5f, 2.5f, 12345L),
            GesturePoint(10f, -20f, 54321L)
        )
        val json = serializeGesturePoints(points)
        val restored = deserializeGesturePoints(json)
        assertEquals(points, restored)
    }

    @Test
    fun `empty input yields empty list`() {
        assertEquals(emptyList<GesturePoint>(), deserializeGesturePoints(""))
        assertEquals(emptyList<GesturePoint>(), deserializeGesturePoints("[]"))
    }

    @Test
    fun `legacy toString format still parses`() {
        val legacy = "[GesturePoint(x=1.0, y=2.0, timestamp=100), GesturePoint(x=3.0, y=4.0, timestamp=200)]"
        val restored = deserializeGesturePoints(legacy)
        assertEquals(2, restored.size)
        assertEquals(1.0f, restored[0].x, 0.001f)
        assertEquals(2.0f, restored[0].y, 0.001f)
        assertEquals(3.0f, restored[1].x, 0.001f)
        assertEquals(4.0f, restored[1].y, 0.001f)
    }

    @Test
    fun `garbage input yields empty list`() {
        assertEquals(emptyList<GesturePoint>(), deserializeGesturePoints("not a gesture at all"))
    }
}
