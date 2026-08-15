package com.example.gesture

import kotlin.math.abs
import kotlin.math.hypot

/**
 * A single pointer sample delivered by the UI layer each frame.
 *
 * @param id   stable pointer id (finger)
 * @param x    current x position in pixels
 * @param y    current y position in pixels
 * @param down true while the finger is pressed down
 */
data class PointerSample(
    val id: Long,
    val x: Float,
    val y: Float,
    val down: Boolean
)

/** Direction of a three-finger swipe. */
enum class SwipeDirection { UP, DOWN }

/**
 * High-level touchpad gesture intents emitted by [TouchpadGestureRecognizer].
 */
sealed class TouchpadAction {
    /** Relative pointer movement (raw pixels; the caller applies sensitivity). */
    data class Move(val dx: Float, val dy: Float) : TouchpadAction()

    /**
     * Mouse wheel ticks from a two-finger scroll.
     * Negative yTicks = scroll down, positive = scroll up.
     * Negative xTicks = scroll left, positive = scroll right.
     */
    data class Scroll(val xTicks: Int, val yTicks: Int) : TouchpadAction()

    /** Ctrl+scroll zoom ticks from a two-finger pinch. Positive = zoom in. */
    data class Zoom(val ticks: Int) : TouchpadAction()

    /**
     * Single tap. button: 1 = left, 2 = right.
     * Fired only when the pointer(s) barely moved.
     */
    data class Tap(val button: Int) : TouchpadAction()

    /**
     * Second tap of a double tap, fired instead of a second [Tap].
     * The caller adds one more click on top of the first tap's click.
     */
    data class DoubleTap(val button: Int) : TouchpadAction()

    /**
     * A drag (button held down) begins: a long-press on one finger, or a
     * sustained three-finger movement (window move). The caller presses the
     * mouse button; subsequent [Move]s drag, and [DragEnd] releases it.
     */
    data class DragStart(val button: Int) : TouchpadAction()

    /** The drag is over — the caller releases the held mouse button. */
    data object DragEnd : TouchpadAction()

    /** Three-finger vertical swipe (task view). */
    data class Swipe(val direction: SwipeDirection) : TouchpadAction()
}

/**
 * State-machine touchpad gesture recognizer.
 *
 * Turns a raw stream of pointer frames into high-level touchpad actions while
 * solving the classic problems of naive implementations:
 *
 *  - **Drag is not a click.** A pointer movement (drag) never fires a click on
 *    release; a tap fires only when the pointer(s) barely moved.
 *  - **Finger transitions are safe.** Adding/removing fingers mid-gesture never
 *    fires spurious actions — the gesture mode is locked to the maximum number
 *    of fingers seen during the sequence, so lifting from 3 -> 2 fingers can't
 *    accidentally trigger a pinch or scroll, and a 3-finger swipe can't be
 *    mistaken for a click on release.
 *  - **Continuous gestures.** Pinch-to-zoom and two-finger scroll emit
 *    repeated ticks as long as the movement continues.
 *  - **Long-press drag-hold.** Holding one finger still past a threshold
 *    starts a drag (button held), so drag-and-drop needs no separate toggle.
 *  - **Three-finger flick vs. drag.** A quick three-finger swipe is task view;
 *    a slow, sustained three-finger movement drags (moves a window).
 *
 * This class is pure Kotlin and unit-testable; it has no Android dependencies.
 */
class TouchpadGestureRecognizer {

    private class Finger(
        var lastX: Float = 0f,
        var lastY: Float = 0f,
        var totalDx: Float = 0f,
        var totalDy: Float = 0f
    )

    private val fingers = LinkedHashMap<Long, Finger>()
    private var maxPointers = 0
    private var gestureMoved = false
    private var gestureHandled = false
    private var lastSingleTapTime = 0L

    // Timestamps used to distinguish long-press drags and slow 3-finger drags
    // from taps/flicks.
    private var gestureStartTime = 0L
    private var modeStartTime = 0L

    // Drag state
    private var dragActive = false          // 1-finger long-press drag
    private var threeFingerDragActive = false

    // Multi-finger baselines
    private var pinchBaseline = -1f
    private var lastCentroidX = 0f
    private var lastCentroidY = 0f
    private var scrollAccumX = 0f
    private var scrollAccumY = 0f
    private var swipeAccumY = 0f

    /**
     * Process one frame of pointer samples and return the actions produced.
     *
     * @param nowMs current time in millis, used for double-tap detection
     */
    fun processFrame(samples: List<PointerSample>, nowMs: Long): List<TouchpadAction> {
        val actions = mutableListOf<TouchpadAction>()

        // Treat an empty frame as "all fingers lifted".
        if (samples.isEmpty()) {
            if (fingers.isNotEmpty()) {
                actions += handleRelease(nowMs)
                resetGesture()
            }
            return actions
        }

        var countChanged = false
        var deltaX = 0f
        var deltaY = 0f
        var downCount = 0
        val updated = LinkedHashMap<Long, Finger>()

        for (sample in samples) {
            if (!sample.down) {
                countChanged = true // this finger is lifting
                continue
            }
            downCount++
            val previous = fingers[sample.id]
            if (previous == null) {
                // New finger down
                updated[sample.id] = Finger(lastX = sample.x, lastY = sample.y)
                countChanged = true
            } else {
                val dx = sample.x - previous.lastX
                val dy = sample.y - previous.lastY
                deltaX += dx
                deltaY += dy
                updated[sample.id] = Finger(
                    lastX = sample.x,
                    lastY = sample.y,
                    totalDx = previous.totalDx + dx,
                    totalDy = previous.totalDy + dy
                )
            }
        }

        // A finger that was tracked last frame but is missing now lifted.
        if (updated.size != fingers.size) countChanged = true

        // Start of a brand-new gesture (no fingers tracked last frame).
        if (fingers.isEmpty() && downCount > 0) {
            gestureStartTime = nowMs
        }

        fingers.clear()
        fingers.putAll(updated)

        if (fingers.isEmpty()) {
            // All fingers lifted this frame.
            actions += handleRelease(nowMs)
            resetGesture()
            return actions
        }

        maxPointers = maxOf(maxPointers, fingers.size)

        // Movement beyond the tap threshold means this is a drag, not a tap.
        if (fingers.values.any { abs(it.totalDx) >= MOVE_TAP_THRESHOLD || abs(it.totalDy) >= MOVE_TAP_THRESHOLD }) {
            gestureMoved = true
        }

        val mode = maxPointers

        // During finger transitions (a finger added/removed, or fewer fingers
        // down than the locked mode), re-baseline and emit nothing.
        if (countChanged || fingers.size < mode) {
            resetBaselines(fingers)
            modeStartTime = nowMs
            return actions
        }

        when (mode) {
            1 -> {
                // Long-press drag-hold: a finger held still past the threshold
                // becomes a drag, so drag-and-drop needs no separate toggle.
                if (!dragActive && !gestureMoved && nowMs - gestureStartTime >= LONG_PRESS_DRAG_MS) {
                    dragActive = true
                    gestureHandled = true
                    actions += TouchpadAction.DragStart(1)
                }
                // Single-finger pointer movement
                if (abs(deltaX) > MOVE_EMIT_THRESHOLD || abs(deltaY) > MOVE_EMIT_THRESHOLD) {
                    actions += TouchpadAction.Move(deltaX, deltaY)
                }
            }
            2 -> {
                val f = fingers.values.toList()
                val p1x = f[0].lastX
                val p1y = f[0].lastY
                val p2x = f[1].lastX
                val p2y = f[1].lastY

                // Two-finger scroll: track centroid travel in both axes
                val centroidX = (p1x + p2x) / 2f
                val centroidY = (p1y + p2y) / 2f
                scrollAccumX += centroidX - lastCentroidX
                scrollAccumY += centroidY - lastCentroidY
                lastCentroidX = centroidX
                lastCentroidY = centroidY

                var xTicks = 0
                while (scrollAccumX >= SCROLL_TICK_PX) {
                    xTicks++ // fingers moved right -> scroll right
                    scrollAccumX -= SCROLL_TICK_PX
                }
                while (scrollAccumX <= -SCROLL_TICK_PX) {
                    xTicks--
                    scrollAccumX += SCROLL_TICK_PX
                }

                var yTicks = 0
                while (scrollAccumY >= SCROLL_TICK_PX) {
                    yTicks-- // fingers moved down -> scroll down
                    scrollAccumY -= SCROLL_TICK_PX
                }
                while (scrollAccumY <= -SCROLL_TICK_PX) {
                    yTicks++
                    scrollAccumY += SCROLL_TICK_PX
                }
                if (xTicks != 0 || yTicks != 0) {
                    actions += TouchpadAction.Scroll(xTicks, yTicks)
                    gestureHandled = true
                }

                // Pinch-to-zoom: track the distance between the two fingers
                val distance = hypot((p2x - p1x).toDouble(), (p2y - p1y).toDouble()).toFloat()
                if (pinchBaseline < 0f) {
                    pinchBaseline = distance
                } else {
                    val delta = distance - pinchBaseline
                    if (abs(delta) >= ZOOM_TICK_PX) {
                        val ticks = if (delta > 0) 1 else -1
                        actions += TouchpadAction.Zoom(ticks)
                        pinchBaseline = distance
                        gestureHandled = true
                    }
                }
            }
            3 -> {
                // Three-finger gestures: track centroid travel in both axes.
                val centroidX = fingers.values.map { it.lastX }.average().toFloat()
                val centroidY = fingers.values.map { it.lastY }.average().toFloat()
                val deltaCx = centroidX - lastCentroidX
                val deltaCy = centroidY - lastCentroidY
                lastCentroidX = centroidX
                lastCentroidY = centroidY
                swipeAccumY += deltaCy

                // Decide flick (task view) vs sustained drag (window move) on
                // first threshold crossing: a quick swipe is task view, a slow
                // sustained movement drags.
                if (!gestureHandled && abs(swipeAccumY) >= SWIPE_THRESHOLD_PX) {
                    val elapsed = nowMs - modeStartTime
                    if (elapsed >= THREE_FINGER_FLICK_MS) {
                        threeFingerDragActive = true
                        actions += TouchpadAction.DragStart(1)
                    } else {
                        actions += TouchpadAction.Swipe(if (swipeAccumY > 0) SwipeDirection.DOWN else SwipeDirection.UP)
                    }
                    gestureHandled = true
                } else if (threeFingerDragActive &&
                    (abs(deltaCx) > MOVE_EMIT_THRESHOLD || abs(deltaCy) > MOVE_EMIT_THRESHOLD)
                ) {
                    // While dragging, forward the movement to move the window.
                    // (Not on the DragStart frame itself.)
                    actions += TouchpadAction.Move(deltaCx, deltaCy)
                }
            }
        }

        return actions
    }

    private fun handleRelease(nowMs: Long): List<TouchpadAction> {
        val actions = mutableListOf<TouchpadAction>()
        // Active drags end with a button release, never a click.
        if (dragActive || threeFingerDragActive) {
            actions += TouchpadAction.DragEnd
            return actions
        }
        // A handled gesture (scroll/zoom/swipe) or a drag never produces a click.
        if (gestureHandled || gestureMoved) return actions

        when (maxPointers) {
            1 -> {
                if (lastSingleTapTime > 0 && nowMs - lastSingleTapTime <= DOUBLE_TAP_WINDOW_MS) {
                    actions += TouchpadAction.DoubleTap(1)
                    lastSingleTapTime = 0L
                } else {
                    actions += TouchpadAction.Tap(1)
                    lastSingleTapTime = nowMs
                }
            }
            2 -> actions += TouchpadAction.Tap(2) // two-finger tap -> right click
            // Three-finger sequences never produce a click.
        }
        return actions
    }

    private fun resetBaselines(fingers: Map<Long, Finger>) {
        pinchBaseline = -1f
        scrollAccumX = 0f
        scrollAccumY = 0f
        swipeAccumY = 0f
        if (fingers.isNotEmpty()) {
            lastCentroidX = fingers.values.map { it.lastX }.average().toFloat()
            lastCentroidY = fingers.values.map { it.lastY }.average().toFloat()
        }
    }

    private fun resetGesture() {
        fingers.clear()
        maxPointers = 0
        gestureMoved = false
        gestureHandled = false
        dragActive = false
        threeFingerDragActive = false
        gestureStartTime = 0L
        modeStartTime = 0L
        pinchBaseline = -1f
        scrollAccumX = 0f
        scrollAccumY = 0f
        swipeAccumY = 0f
        lastCentroidX = 0f
        lastCentroidY = 0f
        // lastSingleTapTime intentionally kept across gestures for double-tap detection.
    }

    companion object {
        // Min per-frame delta (px) before a Move is emitted.
        private const val MOVE_EMIT_THRESHOLD = 1f
        // Total accumulated movement (px) that turns a tap into a drag.
        private const val MOVE_TAP_THRESHOLD = 15f
        // Max gap between two taps (ms) to count as a double tap.
        private const val DOUBLE_TAP_WINDOW_MS = 250L
        // Centroid travel (px) per wheel tick.
        private const val SCROLL_TICK_PX = 20f
        // Finger-distance change (px) per zoom tick.
        private const val ZOOM_TICK_PX = 25f
        // Centroid travel (px) to trigger a three-finger swipe.
        private const val SWIPE_THRESHOLD_PX = 30f
        // How long a single finger must be held still to become a drag (ms).
        private const val LONG_PRESS_DRAG_MS = 400L
        // Max three-finger gesture duration (ms) still treated as a quick flick.
        private const val THREE_FINGER_FLICK_MS = 300L
    }
}
