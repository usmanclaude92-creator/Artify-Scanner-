package com.example.imageprocessing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import com.example.data.model.NormalizedCornerPoints
import com.example.data.model.ScanFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ImageProcessor {

    /**
     * Efficiently decodes a bitmap from disk, scaling it down if larger than requested bounds
     * to avoid Out-Of-Memory errors on high-res camera captures.
     */
    fun decodeSampledBitmap(
        imageFile: File,
        reqWidth: Int = 1800,
        reqHeight: Int = 2400
    ): Bitmap? {
        if (!imageFile.exists() || imageFile.length() == 0L) return null

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imageFile.absolutePath, options)

        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight: Int = options.outHeight / 2
            val halfWidth: Int = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = max(1, inSampleSize)
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return try {
            BitmapFactory.decodeFile(imageFile.absolutePath, decodeOptions)
        } catch (e: OutOfMemoryError) {
            decodeOptions.inSampleSize *= 2
            BitmapFactory.decodeFile(imageFile.absolutePath, decodeOptions)
        }
    }

    /**
     * Rotate bitmap by 90, 180, or 270 degrees.
     */
    fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Applies perspective correction using 4 normalized corner points.
     */
    fun applyPerspectiveCorrection(
        source: Bitmap,
        corners: NormalizedCornerPoints
    ): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()

        // Source 4 points in pixel coordinates: TL, TR, BR, BL
        val src = floatArrayOf(
            corners.topLeft.x * w, corners.topLeft.y * h,
            corners.topRight.x * w, corners.topRight.y * h,
            corners.bottomRight.x * w, corners.bottomRight.y * h,
            corners.bottomLeft.x * w, corners.bottomLeft.y * h
        )

        // Calculate expected target width and height using Euclidean distances
        val widthTop = sqrt(
            ((src[2] - src[0]) * (src[2] - src[0]) + (src[3] - src[1]) * (src[3] - src[1])).toDouble()
        ).toFloat()
        val widthBottom = sqrt(
            ((src[4] - src[6]) * (src[4] - src[6]) + (src[5] - src[7]) * (src[5] - src[7])).toDouble()
        ).toFloat()
        val targetWidth = max(widthTop, widthBottom).coerceIn(400f, 3000f)

        val heightLeft = sqrt(
            ((src[6] - src[0]) * (src[6] - src[0]) + (src[7] - src[1]) * (src[7] - src[1])).toDouble()
        ).toFloat()
        val heightRight = sqrt(
            ((src[4] - src[2]) * (src[4] - src[2]) + (src[5] - src[3]) * (src[5] - src[3])).toDouble()
        ).toFloat()
        val targetHeight = max(heightLeft, heightRight).coerceIn(400f, 4000f)

        // Destination 4 points (upright rectangle)
        val dst = floatArrayOf(
            0f, 0f,
            targetWidth, 0f,
            targetWidth, targetHeight,
            0f, targetHeight
        )

        val matrix = Matrix()
        val success = matrix.setPolyToPoly(src, 0, dst, 0, 4)
        if (!success) {
            return source
        }

        val targetBitmap = Bitmap.createBitmap(
            targetWidth.toInt(),
            targetHeight.toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(targetBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return targetBitmap
    }

    /**
     * Applies document enhancement filters.
     */
    suspend fun applyFilter(
        source: Bitmap,
        filter: ScanFilter
    ): Bitmap = withContext(Dispatchers.Default) {
        when (filter) {
            ScanFilter.ORIGINAL -> source
            ScanFilter.MAGIC_COLOR -> applyMagicColor(source)
            ScanFilter.BW_HIGH_CONTRAST -> applyBwHighContrast(source)
            ScanFilter.GRAYSCALE -> applyGrayscale(source)
            ScanFilter.CLEAN_WHITE -> applyCleanWhitening(source)
        }
    }

    private fun applyMagicColor(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Contrast & Saturation Boost + slight brightness lift
        val contrast = 1.35f
        val brightness = 10f
        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        // Boost saturation
        val satMatrix = ColorMatrix().apply { setSaturation(1.25f) }
        cm.postConcat(satMatrix)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun applyGrayscale(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val cm = ColorMatrix().apply { setSaturation(0f) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun applyBwHighContrast(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // Compute average luminance to use as adaptive threshold
        var totalLuminance = 0L
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            totalLuminance += (299 * r + 587 * g + 114 * b) / 1000
        }
        val avgLuminance = (totalLuminance / pixels.size).toInt()
        val threshold = (avgLuminance * 0.92f).toInt().coerceIn(110, 165)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val lum = (299 * r + 587 * g + 114 * b) / 1000
            pixels[i] = if (lum > threshold) {
                Color.WHITE
            } else {
                Color.BLACK
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun applyCleanWhitening(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val lum = (299 * r + 587 * g + 114 * b) / 1000

            // If background is light (>160), boost smoothly to white
            if (lum > 165) {
                val factor = ((lum - 165) / 90f).coerceIn(0f, 1f)
                val newR = (r + (255 - r) * factor).toInt().coerceIn(0, 255)
                val newG = (g + (255 - g) * factor).toInt().coerceIn(0, 255)
                val newB = (b + (255 - b) * factor).toInt().coerceIn(0, 255)
                pixels[i] = Color.rgb(newR, newG, newB)
            } else {
                // Sharpen text slightly
                val darkFactor = 0.88f
                val newR = (r * darkFactor).toInt().coerceIn(0, 255)
                val newG = (g * darkFactor).toInt().coerceIn(0, 255)
                val newB = (b * darkFactor).toInt().coerceIn(0, 255)
                pixels[i] = Color.rgb(newR, newG, newB)
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Blur estimation heuristic: checks high-frequency gradient variance on downsampled grid.
     * Returns true if sharpness is sufficient.
     */
    fun isImageSharp(bitmap: Bitmap): Boolean {
        val sampleW = min(bitmap.width, 240)
        val sampleH = min(bitmap.height, 320)
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)
        val pixels = IntArray(sampleW * sampleH)
        scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)

        var edgeSum = 0L
        for (y in 1 until sampleH - 1) {
            for (x in 1 until sampleW - 1) {
                val center = pixels[y * sampleW + x] and 0xFF
                val right = pixels[y * sampleW + (x + 1)] and 0xFF
                val bottom = pixels[(y + 1) * sampleW + x] and 0xFF
                edgeSum += abs(center - right) + abs(center - bottom)
            }
        }
        val edgeScore = edgeSum.toFloat() / (sampleW * sampleH)
        return edgeScore > 4.5f
    }

    /**
     * Auto-detect document corners heuristic: scans for high contrast transitions
     * from frame edges towards the center.
     */
    fun detectDocumentCorners(bitmap: Bitmap): NormalizedCornerPoints {
        val fallback = NormalizedCornerPoints(
            topLeft = PointF(0.08f, 0.08f),
            topRight = PointF(0.92f, 0.08f),
            bottomRight = PointF(0.92f, 0.92f),
            bottomLeft = PointF(0.08f, 0.92f)
        )
        if (bitmap.width < 16 || bitmap.height < 16) return fallback

        // 1. Downsample to a small luminance grid (aspect preserved)
        val maxSide = 160
        val scale = maxSide.toFloat() / max(bitmap.width, bitmap.height)
        val w = max(8, (bitmap.width * scale).toInt())
        val h = max(8, (bitmap.height * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) scaled.recycle()
        val luma = IntArray(w * h) {
            val c = pixels[it]
            (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)).toInt()
        }

        // 2. Otsu threshold
        val hist = IntArray(256)
        for (v in luma) hist[v]++
        val total = luma.size
        var sumAll = 0L
        for (t in 0..255) sumAll += t.toLong() * hist[t]
        var sumB = 0L
        var wB = 0
        var best = 0.0
        var threshold = 128
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toLong() * hist[t]
            val mB = sumB.toDouble() / wB
            val mF = (sumAll - sumB).toDouble() / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) {
                best = between
                threshold = t
            }
        }

        // 3. The class that touches the image border least is the document
        var borderBright = 0
        var borderCount = 0
        for (x in 0 until w) {
            for (y in intArrayOf(0, h - 1)) {
                borderCount++
                if (luma[y * w + x] > threshold) borderBright++
            }
        }
        for (y in 1 until h - 1) {
            for (x in intArrayOf(0, w - 1)) {
                borderCount++
                if (luma[y * w + x] > threshold) borderBright++
            }
        }
        val documentIsBright = borderBright * 2 < borderCount
        val mask = BooleanArray(w * h) { (luma[it] > threshold) == documentIsBright }

        // 4. Largest connected component (iterative flood fill)
        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var bestComponent = IntArray(0)
        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            while (head < tail) {
                val idx = queue[head++]
                val x = idx % w
                val y = idx / w
                if (x > 0 && mask[idx - 1] && !visited[idx - 1]) { visited[idx - 1] = true; queue[tail++] = idx - 1 }
                if (x < w - 1 && mask[idx + 1] && !visited[idx + 1]) { visited[idx + 1] = true; queue[tail++] = idx + 1 }
                if (y > 0 && mask[idx - w] && !visited[idx - w]) { visited[idx - w] = true; queue[tail++] = idx - w }
                if (y < h - 1 && mask[idx + w] && !visited[idx + w]) { visited[idx + w] = true; queue[tail++] = idx + w }
            }
            if (tail > bestComponent.size) bestComponent = queue.copyOf(tail)
        }
        if (bestComponent.size < total * 0.15f) return fallback

        // 5. Corners = extreme points along the diagonals
        var tl = -1; var tr = -1; var br = -1; var bl = -1
        var tlV = Int.MAX_VALUE; var trV = Int.MIN_VALUE; var brV = Int.MIN_VALUE; var blV = Int.MAX_VALUE
        for (idx in bestComponent) {
            val x = idx % w
            val y = idx / w
            val sum = x + y
            val diff = x - y
            if (sum < tlV) { tlV = sum; tl = idx }
            if (sum > brV) { brV = sum; br = idx }
            if (diff > trV) { trV = diff; tr = idx }
            if (diff < blV) { blV = diff; bl = idx }
        }
        fun pt(idx: Int) = PointF(((idx % w) + 0.5f) / w, ((idx / w) + 0.5f) / h)
        val result = NormalizedCornerPoints(pt(tl), pt(tr), pt(br), pt(bl))

        // 6. Reject degenerate quads (shoelace area)
        val q = listOf(result.topLeft, result.topRight, result.bottomRight, result.bottomLeft)
        var area = 0f
        for (i in q.indices) {
            val a = q[i]
            val b = q[(i + 1) % q.size]
            area += a.x * b.y - b.x * a.y
        }
        return if (kotlin.math.abs(area) / 2f < 0.15f) fallback else result
    }

    /**
     * Saves bitmap to a target destination file with high quality.
     */
    suspend fun saveBitmapToFile(
        bitmap: Bitmap,
        targetFile: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 92
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            targetFile.parentFile?.mkdirs()
            FileOutputStream(targetFile).use { out ->
                bitmap.compress(format, quality, out)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("Artify", "Operation failed", e)
            false
        }
    }
}
