package com.example.imageprocessing

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Finds a page by its straight borders instead of by blobs. Long edge segments are grouped into near-horizontal
 * and near-vertical lines, every top/bottom/left/right combination is turned into a quadrilateral, and the one
 * whose four sides are best backed by real image edges wins. This copes with a white page lying on white
 * papers, where colour or brightness regions merge but the page border still leaves a thin edge.
 */
object LineQuadDetector {

    class Result(val corners: Array<Point>, val score: Double) // top-left, top-right, bottom-right, bottom-left

    private class Line(val x0: Double, val y0: Double, val vx: Double, val vy: Double, val support: Double)

    fun detect(gray: Mat): Result? {
        val w = gray.cols()
        val h = gray.rows()
        if (w < 64 || h < 64) return null

        val edges = Mat()
        val lines = Mat()
        val inverted = Mat()
        val distMat = Mat()
        try {
            val median = Core.mean(gray).`val`[0]
            Imgproc.Canny(gray, edges, max(10.0, 0.4 * median), max(40.0, 0.9 * median))
            Imgproc.HoughLinesP(
                edges, lines, 1.0, Math.PI / 180, 50,
                0.12 * min(w, h), 0.02 * max(w, h)
            )
            if (lines.rows() == 0) return null

            val segments = ArrayList<DoubleArray>(lines.rows())
            for (i in 0 until lines.rows()) segments.add(lines.get(i, 0))

            val horizontals = cluster(segments, w, h, horizontal = true)
            val verticals = cluster(segments, w, h, horizontal = false)
            if (horizontals.size < 2 || verticals.size < 2) return null

            Core.bitwise_not(edges, inverted)
            Imgproc.distanceTransform(inverted, distMat, Imgproc.DIST_L2, 3)
            val dist = FloatArray(w * h)
            distMat.get(0, 0, dist)

            var best: Array<Point>? = null
            var bestScore = 0.0
            var bestMin = 0.0
            for (a in 0 until horizontals.size) for (b in a + 1 until horizontals.size) {
                val (top, bottom) = if (horizontals[a].y0 < horizontals[b].y0) horizontals[a] to horizontals[b] else horizontals[b] to horizontals[a]
                for (c in 0 until verticals.size) for (d in c + 1 until verticals.size) {
                    val (left, right) = if (verticals[c].x0 < verticals[d].x0) verticals[c] to verticals[d] else verticals[d] to verticals[c]
                    val tl = intersect(top, left) ?: continue
                    val tr = intersect(top, right) ?: continue
                    val br = intersect(bottom, right) ?: continue
                    val bl = intersect(bottom, left) ?: continue
                    val q = arrayOf(tl, tr, br, bl)
                    if (q.any { it.x < -0.08 * w || it.x > 1.08 * w || it.y < -0.08 * h || it.y > 1.08 * h }) continue
                    val area = polygonArea(q)
                    if (area < 0.25 * w * h || area > 1.05 * w * h) continue
                    if (!isConvex(q)) continue
                    val sides = DoubleArray(4) { hypot(q[it].x - q[(it + 1) % 4].x, q[it].y - q[(it + 1) % 4].y) }
                    if (sides.min() < 0.2 * min(w, h)) continue
                    val aspect = (sides[0] + sides[2]) / (sides[1] + sides[3])
                    if (aspect < 0.35 || aspect > 2.8) continue

                    var sum = 0.0
                    var minCov = 1.0
                    for (i in 0 until 4) {
                        val cov = sideCoverage(q[i], q[(i + 1) % 4], dist, w, h)
                        sum += cov
                        minCov = min(minCov, cov)
                    }
                    var score = (sum / 4) * 0.6 + minCov * 0.4
                    score *= 0.6 + 0.4 * min(1.0, area / (0.7 * w * h)) // prefer the larger page
                    if (score > bestScore) {
                        bestScore = score
                        bestMin = minCov
                        best = q
                    }
                }
            }
            return if (best != null && bestScore >= MIN_SCORE && bestMin >= MIN_SIDE_COVERAGE) Result(best, bestScore) else null
        } finally {
            edges.release(); lines.release(); inverted.release(); distMat.release()
        }
    }

    private fun cluster(segments: List<DoubleArray>, w: Int, h: Int, horizontal: Boolean): List<Line> {
        class Item(val angle: Double, val length: Double, val s: DoubleArray)

        val items = ArrayList<Item>()
        for (s in segments) {
            val length = hypot(s[2] - s[0], s[3] - s[1])
            var angle = Math.toDegrees(atan2(s[3] - s[1], s[2] - s[0]))
            angle = ((angle % 180) + 180) % 180
            if (horizontal) {
                if (angle > 35 && angle < 145) continue
                if (angle > 90) angle -= 180
            } else if (angle < 55 || angle > 125) continue
            items.add(Item(angle, length, s))
        }
        items.sortByDescending { it.length }

        val used = BooleanArray(items.size)
        val tolerance = 0.012 * max(w, h)
        val result = ArrayList<Line>()
        for (i in items.indices) {
            if (used[i]) continue
            used[i] = true
            val ref = items[i]
            val dx = ref.s[2] - ref.s[0]
            val dy = ref.s[3] - ref.s[1]
            val n = hypot(dx, dy)
            val group = arrayListOf(ref)
            for (j in items.indices) {
                if (used[j]) continue
                val other = items[j]
                if (abs(other.angle - ref.angle) > 5) continue
                fun d(px: Double, py: Double) = abs(dy * (px - ref.s[0]) - dx * (py - ref.s[1])) / n
                if (max(d(other.s[0], other.s[1]), d(other.s[2], other.s[3])) < tolerance) {
                    used[j] = true
                    group.add(other)
                }
            }
            result.add(fit(group.map { it.s }, group.sumOf { it.length }))
        }
        result.sortByDescending { it.support }
        return result.take(MAX_LINES)
    }

    /** Principal-axis fit through all segment endpoints. */
    private fun fit(segs: List<DoubleArray>, support: Double): Line {
        var mx = 0.0; var my = 0.0
        for (s in segs) { mx += s[0] + s[2]; my += s[1] + s[3] }
        mx /= segs.size * 2; my /= segs.size * 2
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (s in segs) {
            for (k in 0..1) {
                val x = s[k * 2] - mx
                val y = s[k * 2 + 1] - my
                sxx += x * x; syy += y * y; sxy += x * y
            }
        }
        val theta = 0.5 * atan2(2 * sxy, sxx - syy)
        return Line(mx, my, cos(theta), sin(theta), support)
    }

    private fun intersect(a: Line, b: Line): Point? {
        val det = a.vx * (-b.vy) - (-b.vx) * a.vy
        if (abs(det) < 1e-6) return null
        val t = ((b.x0 - a.x0) * (-b.vy) - (-b.vx) * (b.y0 - a.y0)) / det
        return Point(a.x0 + t * a.vx, a.y0 + t * a.vy)
    }

    private fun polygonArea(q: Array<Point>): Double {
        var s = 0.0
        for (i in q.indices) {
            val a = q[i]
            val b = q[(i + 1) % q.size]
            s += a.x * b.y - b.x * a.y
        }
        return abs(s) / 2
    }

    private fun isConvex(q: Array<Point>): Boolean {
        var sign = 0
        for (i in q.indices) {
            val a = q[i]; val b = q[(i + 1) % 4]; val c = q[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s != 0) {
                if (sign == 0) sign = s else if (s != sign) return false
            }
        }
        return true
    }

    /** Fraction of the points along a side that lie within a few pixels of a detected edge. */
    private fun sideCoverage(p: Point, q: Point, dist: FloatArray, w: Int, h: Int): Double {
        val steps = (max(abs(q.x - p.x), abs(q.y - p.y)) / 2).toInt() + 1
        var hit = 0
        var total = 0
        for (k in 0..steps) {
            val x = p.x + (q.x - p.x) * k / steps
            val y = p.y + (q.y - p.y) * k / steps
            if (x < 0 || y < 0 || x >= w || y >= h) continue // outside the frame counts as neither
            total++
            if (dist[y.toInt() * w + x.toInt()] <= EDGE_TOLERANCE) hit++
        }
        return if (total > 0) hit.toDouble() / total else 0.0
    }

    private const val MAX_LINES = 7
    private const val EDGE_TOLERANCE = 3f
    private const val MIN_SCORE = 0.45
    private const val MIN_SIDE_COVERAGE = 0.3
}
