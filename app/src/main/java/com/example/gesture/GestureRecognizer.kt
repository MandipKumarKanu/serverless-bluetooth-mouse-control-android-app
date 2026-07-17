package com.example.gesture

import kotlin.math.sqrt

/**
 * Gesture Recognizer based on $1 Unistroke Recognizer algorithm
 * Recognizes drawn gestures by comparing them to stored templates
 */
class GestureRecognizer {

    companion object {
        private const val NUM_POINTS = 64 // Number of points to resample to
        private const val SQUARE_SIZE = 250.0 // Size of the bounding square
        private const val HALF SQUARE_SIZE = SQUARE_SIZE / 2.0
        private const val THRESHOLD = 0.65 // Minimum score to consider a match (0-1)
        private const val ORIGIN = Point(0.0, 0.0)
    }

    /**
     * Recognize a gesture from a list of points
     * Returns the best matching template name and score
     */
    fun recognize(points: List<GesturePoint>, templates: Map<String, List<GesturePoint>>): Pair<String, Double>? {
        if (points.size < 10) return null // Too few points

        // Convert to Point list and process
        val processedPoints = points.map { Point(it.x.toDouble(), it.y.toDouble()) }
        val resampled = resample(processedPoints, NUM_POINTS)
        val rotated = rotateToZero(resampled)
        val scaled = scaleToSquare(rotated, SQUARE_SIZE)
        val translated = translateTo(scaled, ORIGIN)

        var bestMatch: Pair<String, Double>? = null

        for ((name, templatePoints) in templates) {
            val templateProcessed = templatePoints.map { Point(it.x.toDouble(), it.y.toDouble()) }
            val templateResampled = resample(templateProcessed, NUM_POINTS)
            val templateRotated = rotateToZero(templateResampled)
            val templateScaled = scaleToSquare(templateRotated, SQUARE_SIZE)
            val templateTranslated = translateTo(templateScaled, ORIGIN)

            val score = match(translated, templateTranslated)

            if (score > THRESHOLD) {
                if (bestMatch == null || score > bestMatch.second) {
                    bestMatch = Pair(name, score)
                }
            }
        }

        return bestMatch
    }

    /**
     * Resample a path to N evenly spaced points
     */
    private fun resample(points: List<Point>, n: Int): List<Point> {
        if (points.isEmpty()) return emptyList()

        val interval = pathLength(points) / (n - 1)
        var D = 0.0
        val newPoints = mutableListOf(points[0])

        var i = 1
        while (i < points.size) {
            val d = distance(points[i - 1], points[i])
            if (D + d >= interval) {
                val qx = points[i - 1].x + ((interval - D) / d) * (points[i].x - points[i - 1].x)
                val qy = points[i - 1].y + ((interval - D) / d) * (points[i].y - points[i - 1].y)
                val q = Point(qx, qy)
                newPoints.add(q)
                points.add(i, q) // Insert resampled point
                D = 0.0
            } else {
                D += d
            }
            i++
        }

        // Add last point if needed
        if (newPoints.size < n) {
            newPoints.add(points.last())
        }

        return newPoints.take(n)
    }

    /**
     * Rotate the path so the indicative angle is zero
     */
    private fun rotateToZero(points: List<Point>): List<Point> {
        val c = centroid(points)
        val angle = Math.atan2(c.y - points[0].y, c.x - points[0].x)
        return rotateBy(points, -angle)
    }

    /**
     * Rotate points by a given angle
     */
    private fun rotateBy(points: List<Point>, angle: Double): List<Point> {
        val cos = Math.cos(angle)
        val sin = Math.sin(angle)

        return points.map { p ->
            val qx = (p.x * cos) - (p.y * sin) + ORIGIN.x
            val qy = (p.x * sin) + (p.y * cos) + ORIGIN.y
            Point(qx, qy)
        }
    }

    /**
     * Scale points to fit within a square of given size
     */
    private fun scaleToSquare(points: List<Point>, size: Double): List<Point> {
        val B = boundingBox(points)
        val newPoints = mutableListOf<Point>()

        for (p in points) {
            val qx = if (B.width > 0) p.x * (size / B.width) else p.x
            val qy = if (B.height > 0) p.y * (size / B.height) else p.y
            newPoints.add(Point(qx, qy))
        }

        return newPoints
    }

    /**
     * Translate points to a target origin
     */
    private fun translateTo(points: List<Point>, target: Point): List<Point> {
        val c = centroid(points)
        return points.map { p ->
            Point(p.x - c.x + target.x, p.y - c.y + target.y)
        }
    }

    /**
     * Calculate path length
     */
    private fun pathLength(points: List<Point>): Double {
        var length = 0.0
        for (i in 1 until points.size) {
            length += distance(points[i - 1], points[i])
        }
        return length
    }

    /**
     * Calculate distance between two points
     */
    private fun distance(p1: Point, p2: Point): Double {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Calculate centroid of points
     */
    private fun centroid(points: List<Point>): Point {
        var x = 0.0
        var y = 0.0
        for (p in points) {
            x += p.x
            y += p.y
        }
        return Point(x / points.size, y / points.size)
    }

    /**
     * Get bounding box of points
     */
    private fun boundingBox(points: List<Point>): BoundingBox {
        var minX = Double.MAX_VALUE
        var maxX = Double.MIN_VALUE
        var minY = Double.MAX_VALUE
        var maxY = Double.MIN_VALUE

        for (p in points) {
            minX = minOf(minX, p.x)
            maxX = maxOf(maxX, p.x)
            minY = minOf(minY, p.y)
            maxY = maxOf(maxY, p.y)
        }

        return BoundingBox(minX, minY, maxX - minX, maxY - minY)
    }

    /**
     * Match two paths and return similarity score (0-1)
     */
    private fun match(points1: List<Point>, points2: List<Point>): Double {
        val n = minOf(points1.size, points2.size)
        var sum = 0.0

        for (i in 0 until n) {
            sum += distance(points1[i], points2[i])
        }

        return 1.0 - (sum / (n * SQUARE_SIZE))
    }
}

data class Point(val x: Double, val y: Double)

data class BoundingBox(val x: Double, val y: Double, val width: Double, val height: Double)
