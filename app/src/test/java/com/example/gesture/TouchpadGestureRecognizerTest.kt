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

    @Test
    fun `two-finger horizontal scroll emits horizontal ticks`() {
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 1L)
        recognizer.processFrame(listOf(finger(1, 100f, 100f), finger(2, 200f, 100f)), 2L)

        // Both fingers move right 40px -> centroid moved +40px -> 2 scroll-right ticks
        val scroll = recognizer.processFrame(
            listOf(finger(1, 140f, 100f), finger(2, 240f, 100f)),
            3L
        )
        assertEquals(listOf(TouchpadAction.Scroll(2, 0)), scroll)
    }

    @Test
    fun `long press starts a drag that moves with the button held`() {
        // Finger down at t=0 with no movement
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 0L)

        // Still held at t=500 (past the 400ms threshold) -> DragStart
        val dragStart = recognizer.processFrame(listOf(finger(1, 100f, 100f)), 500L)
        assertEquals(listOf(TouchpadAction.DragStart(1)), dragStart)

        // Moving while dragging -> Move (the caller keeps the button held)
        val move = recognizer.processFrame(listOf(finger(1, 120f, 100f)), 501L)
        assertEquals(listOf(TouchpadAction.Move(20f, 0f)), move)

        // Release -> DragEnd, never a click
        val release = recognizer.processFrame(listOf(finger(1, 120f, 100f, down = false)), 502L)
        assertEquals(listOf(TouchpadAction.DragEnd), release)
    }

    @Test
    fun `long press without movement never clicks on release`() {
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 0L)
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 500L) // DragStart

        val release = recognizer.processFrame(listOf(finger(1, 100f, 100f, down = false)), 501L)
        assertEquals(listOf(TouchpadAction.DragEnd), release)
    }

    @Test
    fun `slow three-finger drag moves a window instead of swiping`() {
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 1L)
        recognizer.processFrame(listOf(finger(1, 100f, 100f), finger(2, 200f, 100f)), 2L)
        recognizer.processFrame(listOf(finger(1, 100f, 100f), finger(2, 200f, 100f), finger(3, 300f, 100f)), 3L)

        // Sustained movement crossing the threshold well after the flick window
        val dragStart = recognizer.processFrame(
            listOf(finger(1, 100f, 140f), finger(2, 200f, 140f), finger(3, 300f, 140f)),
            1000L
        )
        assertEquals(listOf(TouchpadAction.DragStart(1)), dragStart)

        // Continued movement drags the window
        val move = recognizer.processFrame(
            listOf(finger(1, 100f, 170f), finger(2, 200f, 170f), finger(3, 300f, 170f)),
            1100L
        )
        assertEquals(listOf(TouchpadAction.Move(0f, 30f)), move)

        // Release -> DragEnd, never a click or swipe
        val release = recognizer.processFrame(
            listOf(
                finger(1, 100f, 170f, down = false),
                finger(2, 200f, 170f, down = false),
                finger(3, 300f, 170f, down = false)
            ),
            1200L
        )
        assertEquals(listOf(TouchpadAction.DragEnd), release)
    }

    @Test
    fun `quick three-finger flick still fires task view`() {
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 1L)
        recognizer.processFrame(listOf(finger(1, 100f, 100f), finger(2, 200f, 100f)), 2L)
        recognizer.processFrame(listOf(finger(1, 100f, 100f), finger(2, 200f, 100f), finger(3, 300f, 100f)), 3L)

        // Quick flick (elapsed well under the flick window) -> task view swipe
        val swipe = recognizer.processFrame(
            listOf(finger(1, 100f, 140f), finger(2, 200f, 140f), finger(3, 300f, 140f)),
            4L
        )
        assertEquals(listOf(TouchpadAction.Swipe(SwipeDirection.DOWN)), swipe)
    }

    @Test
    fun `moving finger before long press is a normal drag not a button drag`() {
        recognizer.processFrame(listOf(finger(1, 100f, 100f)), 0L)

        // Real movement early -> regular cursor drag
        val move = recognizer.processFrame(listOf(finger(1, 120f, 100f)), 100L)
        assertEquals(listOf(TouchpadAction.Move(20f, 0f)), move)

        // Held after movement: must NOT turn into a button drag
        val held = recognizer.processFrame(listOf(finger(1, 120f, 100f)), 800L)
        assertEquals(emptyList(), held)

        val release = recognizer.processFrame(listOf(finger(1, 120f, 100f, down = false)), 801L)
        assertEquals(emptyList(), release)
    }
}
