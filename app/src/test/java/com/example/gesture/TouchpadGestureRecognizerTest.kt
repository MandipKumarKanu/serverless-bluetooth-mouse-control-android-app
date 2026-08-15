package com.example.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchpadGestureRecognizerTest {

    private val recognizer = TouchpadGestureRecognizer()

    private fun finger(id: Long, x: Float, y: Float, down: Boolean = true) =
        PointerSample(id = id, x = x, y = y, down = down)

    @Test
    fun `single tap fires a left click on release`() {
        assertEquals(emptyList(), recognizer.processFrame(listOf(finger(1, 100f, 100f)), 1000L))

        val release = recognizer.processFrame(listOf(finger(1, 100f, 100f, down = false)), 1000L)
        assertEquals(listOf(TouchpadAction.Tap(1)), release)
    }

    @Test
    fun `drag moves the cursor and never fires a click on release`() {
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 1L)

        val move = recognizer.processFrame(listOf(finger(1, 115f, 100f)), 2L)
        assertEquals(listOf(TouchpadAction.Move(15f, 0f)), move)

        val release = recognizer.processFrame(listOf(finger(1, 115f, 100f, down = false)), 3L)
        assertEquals(emptyList(), release)
    }

    @Test
    fun `two-finger tap fires a right click`() {
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 1L)
        recognizer.processFrame(listOf(finger(1, 100f, 100f), finger(2, 200f, 100f)), 2L)
        // Slight jitter below the tap threshold must not cancel the tap
        recognizer.processFrame(listOf(finger(1, 103f, 102f), finger(2, 200f, 100f)), 3L)

        val release = recognizer.processFrame(
            listOf(finger(1, 103f, 102f, down = false), finger(2, 200f, 100f, down = false)),
            4L
        )
        assertEquals(listOf(TouchpadAction.Tap(2)), release)
    }

    @Test
    fun `two-finger scroll emits ticks and does not click on release`() {
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 1L)
        recognizer.processFrame(listOf(finger(1, 100f, 100f), finger(2, 200f, 100f)), 2L)

        // Both fingers move down 40px -> centroid moved 40px -> 2 scroll-down ticks
        val scroll = recognizer.processFrame(
            listOf(finger(1, 100f, 140f), finger(2, 200f, 140f)),
            3L
        )
        assertEquals(listOf(TouchpadAction.Scroll(0, -2)), scroll)

        val release = recognizer.processFrame(
            listOf(finger(1, 100f, 140f, down = false), finger(2, 200f, 140f, down = false)),
            4L
        )
        assertEquals(emptyList(), release)
    }

    @Test
    fun `pinch emits zoom ticks`() {
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 1L)
        recognizer.processFrame(listOf(finger(1, 100f, 100f), finger(2, 200f, 100f)), 2L)
        // Baseline frame for the pinch distance
        recognizer.processFrame(listOf(finger(1, 80f, 100f), finger(2, 220f, 100f)), 3L)

        // Distance grows 140 -> 180, delta >= threshold -> zoom in
        val zoom = recognizer.processFrame(listOf(finger(1, 60f, 100f), finger(2, 240f, 100f)), 4L)
        assertTrue(zoom.contains(TouchpadAction.Zoom(1)))
    }

    @Test
    fun `three-finger swipe fires task view and does not click`() {
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 1L)
        recognizer.processFrame(listOf(finger(1, 100f, 100f), finger(2, 200f, 100f)), 2L)
        recognizer.processFrame(listOf(finger(1, 100f, 100f), finger(2, 200f, 100f), finger(3, 300f, 100f)), 3L)

        val swipe = recognizer.processFrame(
            listOf(finger(1, 100f, 140f), finger(2, 200f, 140f), finger(3, 300f, 140f)),
            4L
        )
        assertEquals(listOf(TouchpadAction.Swipe(SwipeDirection.DOWN)), swipe)

        val release = recognizer.processFrame(
            listOf(
                finger(1, 100f, 140f, down = false),
                finger(2, 200f, 140f, down = false),
                finger(3, 300f, 140f, down = false)
            ),
            5L
        )
        assertEquals(emptyList(), release)
    }

    @Test
    fun `quick double tap fires one tap then a double tap`() {
        // First tap at t=1000
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 1000L)
        assertEquals(
            listOf(TouchpadAction.Tap(1)),
            recognizer.processFrame(listOf(finger(1, 100f, 100f, down = false)), 1000L)
        )

        // Second tap within the window at t=1100
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 1100L)
        assertEquals(
            listOf(TouchpadAction.DoubleTap(1)),
            recognizer.processFrame(listOf(finger(1, 100f, 100f, down = false)), 1100L)
        )
    }

    @Test
    fun `slow taps are separate single clicks`() {
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 0L)
        recognizer.processFrame(listOf(finger(1, 100f, 100f, down = false)), 0L)

        // Second tap well after the double-tap window
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 5000L)
        assertEquals(
            listOf(TouchpadAction.Tap(1)),
            recognizer.processFrame(listOf(finger(1, 100f, 100f, down = false)), 5000L)
        )
    }

    @Test
    fun `lifting from three fingers to two never fires scroll or click`() {
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 1L)
        recognizer.processFrame(listOf(finger(1, 100f, 100f), finger(2, 200f, 100f)), 2L)
        recognizer.processFrame(listOf(finger(1, 100f, 100f), finger(2, 200f, 100f), finger(3, 300f, 100f)), 3L)

        // Lift finger 3 -> transition frame, no actions
        val transition = recognizer.processFrame(
            listOf(finger(1, 100f, 100f), finger(2, 200f, 100f), finger(3, 300f, 100f, down = false)),
            4L
        )
        assertEquals(emptyList(), transition)

        // Moving the remaining two fingers must NOT scroll (mode is locked to 3)
        val moved = recognizer.processFrame(listOf(finger(1, 100f, 140f), finger(2, 200f, 140f)), 5L)
        assertEquals(emptyList(), moved)

        // Release must not produce a click
        val release = recognizer.processFrame(
            listOf(finger(1, 100f, 140f, down = false), finger(2, 200f, 140f, down = false)),
            6L
        )
        assertEquals(emptyList(), release)
    }

    @Test
    fun `empty frame releases all fingers`() {
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 1L)
        assertEquals(
            listOf(TouchpadAction.Tap(1)),
            recognizer.processFrame(emptyList(), 2L)
        )
    }
}
