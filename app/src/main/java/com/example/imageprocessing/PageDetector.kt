package com.example.imageprocessing

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.abs
import com.example.data.model.NormalizedCornerPoints

/**
 * Finds a sheet of paper in a photo without any native library: the sheet is the largest bright,
 * roughly rectangular blob. Works on a small luma grid, so it is cheap enough to run on every preview tick.
 */
object PageDetector {

    private const val MIN_AREA = 0.18f      // sheet must cover at least this share of the frame
    private const val MAX_AREA = 0.985f     // ...and not be the whole frame
    private const val EDGE_STEP = 8        // brightness change per pixel (0-255) that counts as a sheet edge
    private const val GROW = 2
    private const val MIN_FILL = 0.72f      // blob area / quad area: rejects irregular shapes (page merged with a laptop)

    /** [lum] is an upright w x h grid of brightness values 0..255. Corners are returned as TL, TR, BR, BL. */
    fun detect(lum: IntArray, w: Int, h: Int): NormalizedCornerPoints? {
        val hist = IntArray(256)
        for (v in lum) hist[v.coerceIn(0, 255)]++
        val t1 = otsu(hist, 0, 255)
        val t2 = if (t1 < 250) otsu(hist, t1 + 1, 255) else t1
        val steep = steepPixels(lum, w, h)
        // Prefer the brightest class (paper against a laptop or table), fall back to the coarse split
        return quadOfLargestBlob(lum, steep, w, h, t2) ?: if (t1 != t2) quadOfLargestBlob(lum, steep, w, h, t1) else null
    }

    fun detectInBitmap(bitmap: Bitmap, gridW: Int = 240, gridH: Int = 320): NormalizedCornerPoints? {
        val small = Bitmap.createScaledBitmap(bitmap, gridW, gridH, true)
        val px = IntArray(gridW * gridH)
        small.getPixels(px, 0, gridW, 0, 0, gridW, gridH)
        if (small !== bitmap) small.recycle()
        val lum = IntArray(px.size) {
            val c = px[it]
            (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        return detect(lum, gridW, gridH)
    }

    /** Otsu threshold over the histogram range [lo, hi]. */
    private fun otsu(hist: IntArray, lo: Int, hi: Int): Int {
        var total = 0L
        var sumAll = 0L
        for (i in lo..hi) { total += hist[i]; sumAll += i.toLong() * hist[i] }
        if (total == 0L) return lo
        var wB = 0L
        var sumB = 0L
        var best = lo
        var bestVar = -1.0
        for (t in lo..hi) {
            wB += hist[t]
            if (wB == 0L) continue
            val wF = total - wB
            if (wF == 0L) break
            sumB += t.toLong() * hist[t]
            val mB = sumB.toDouble() / wB
            val mF = (sumAll - sumB).toDouble() / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > bestVar) { bestVar = between; best = t }
        }
        return best
    }

    /** Pixels sitting on a strong brightness step. Cutting them apart separates a sheet from a bright laptop it touches. */
    private fun steepPixels(lum: IntArray, w: Int, h: Int): BooleanArray {
        val steep = BooleanArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val gx = abs(lum[y * w + x + 1] - lum[y * w + x - 1]) / 2
                val gy = abs(lum[(y + 1) * w + x] - lum[(y - 1) * w + x]) / 2
                steep[y * w + x] = maxOf(gx, gy) >= EDGE_STEP
            }
        }
        return steep
    }

    private fun quadOfLargestBlob(lum: IntArray, steep: BooleanArray, w: Int, h: Int, threshold: Int): NormalizedCornerPoints? {
        val n = w * h
        val seen = BooleanArray(n)
        val queue = IntArray(n)
        var bestArea = 0
        var bestStart = -1

        for (start in 0 until n) {
            if (seen[start] || lum[start] <= threshold || steep[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            seen[start] = true
            while (head < tail) {
                val p = queue[head++]
                val x = p % w
                val y = p / w
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= h) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= w) continue
                        val q = ny * w + nx
                        if (!seen[q] && lum[q] > threshold && !steep[q]) { seen[q] = true; queue[tail++] = q }
                    }
                }
            }
            if (tail > bestArea) { bestArea = tail; bestStart = start }
        }
        if (bestStart < 0 || bestArea < MIN_AREA * n * 0.8f) return null

        // Rebuild the winning blob, grow it by two pixels to win back the edge pixels that were cut, take its extreme corners
        val blob = BooleanArray(n)
        run {
            var head = 0; var tail = 0
            queue[tail++] = bestStart; blob[bestStart] = true
            while (head < tail) {
                val p = queue[head++]
                val x = p % w; val y = p / w
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= h) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= w) continue
                        val q = ny * w + nx
                        if (!blob[q] && lum[q] > threshold && !steep[q]) { blob[q] = true; queue[tail++] = q }
                    }
                }
            }
        }
        var grown = blob
        repeat(GROW) {
            val next = grown.copyOf()
            for (p in 0 until n) if (grown[p]) {
                val x = p % w; val y = p / w
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h) next[ny * w + nx] = true
                }
            }
            grown = next
        }
        var minSum = Int.MAX_VALUE; var maxSum = Int.MIN_VALUE
        var minDiff = Int.MAX_VALUE; var maxDiff = Int.MIN_VALUE
        var bTL = 0; var bBR = 0; var bBL = 0; var bTR = 0
        var grownArea = 0
        for (p in 0 until n) if (grown[p]) {
            grownArea++
            val x = p % w; val y = p / w
            val sum = x + y; val diff = x - y
            if (sum < minSum) { minSum = sum; bTL = p }
            if (sum > maxSum) { maxSum = sum; bBR = p }
            if (diff < minDiff) { minDiff = diff; bBL = p }
            if (diff > maxDiff) { maxDiff = diff; bTR = p }
        }
        bestArea = grownArea

        fun pt(i: Int) = PointF((i % w + 0.5f) / w, (i / w + 0.5f) / h)
        val tl = pt(bTL); val tr = pt(bTR); val br = pt(bBR); val bl = pt(bBL)
        // Shoelace area of TL, TR, BR, BL (positive when the corners run clockwise on screen)
        val area = 0.5f * (
            (tl.x * tr.y - tr.x * tl.y) + (tr.x * br.y - br.x * tr.y) +
                (br.x * bl.y - bl.x * br.y) + (bl.x * tl.y - tl.x * bl.y)
            )
        if (area < MIN_AREA || area > MAX_AREA) return null
        if (bestArea.toFloat() / n / area < MIN_FILL) return null
        return NormalizedCornerPoints(tl, tr, br, bl)
    }
}
