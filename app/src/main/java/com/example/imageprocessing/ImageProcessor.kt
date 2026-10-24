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
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
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

    /** Crops a bitmap to a region given in normalized (0..1) coordinates. */
    fun cropNormalized(source: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val x = (left * source.width).toInt().coerceIn(0, source.width - 1)
        val y = (top * source.height).toInt().coerceIn(0, source.height - 1)
        val w = ((right - left) * source.width).toInt().coerceIn(1, source.width - x)
        val h = ((bottom - top) * source.height).toInt().coerceIn(1, source.height - y)
        return Bitmap.createBitmap(source, x, y, w, h)
    }

    private val fullFrame = NormalizedCornerPoints(
        topLeft = PointF(0f, 0f),
        topRight = PointF(1f, 0f),
        bottomRight = PointF(1f, 1f),
        bottomLeft = PointF(0f, 1f)
    )

    /**
     * Finds the page outline with OpenCV (Canny edges, then the largest convex four-point contour).
     * Returns the full frame when no confident quadrilateral exists, so nothing is ever cut off by a
     * wrong guess.
     */
    fun detectDocumentCorners(bitmap: Bitmap): NormalizedCornerPoints {
        if (bitmap.width < 32 || bitmap.height < 32 || !OpenCvSupport.ensureLoaded()) return fullFrame

        val scale = min(1f, 800f / max(bitmap.width, bitmap.height))
        val w = max(32, (bitmap.width * scale).toInt())
        val h = max(32, (bitmap.height * scale).toInt())
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, w, h, true) else bitmap

        val rgba = Mat()
        val gray = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        try {
            Utils.bitmapToMat(small, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(gray, edges, 40.0, 130.0)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.dilate(edges, edges, kernel)
            kernel.release()
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val frameArea = (small.width * small.height).toDouble()
            var best: Array<org.opencv.core.Point>? = null
            var bestArea = 0.0
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area < frameArea * 0.2 || area > frameArea * 0.985 || area <= bestArea) continue
                val c2f = MatOfPoint2f(*contour.toArray())
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(c2f, approx, 0.02 * Imgproc.arcLength(c2f, true), true)
                val pts = approx.toArray()
                if (pts.size == 4 && Imgproc.isContourConvex(MatOfPoint(*pts))) {
                    best = pts
                    bestArea = area
                }
                c2f.release()
                approx.release()
            }
            val quad = best ?: return fullFrame

            // Order corners: TL = min(x+y), BR = max(x+y), TR = max(x-y), BL = min(x-y)
            val tl = quad.minByOrNull { it.x + it.y }!!
            val br = quad.maxByOrNull { it.x + it.y }!!
            val tr = quad.maxByOrNull { it.x - it.y }!!
            val bl = quad.minByOrNull { it.x - it.y }!!
            fun norm(p: org.opencv.core.Point) = PointF(
                (p.x / small.width).toFloat().coerceIn(0f, 1f),
                (p.y / small.height).toFloat().coerceIn(0f, 1f)
            )
            val result = NormalizedCornerPoints(norm(tl), norm(tr), norm(br), norm(bl))
            return if (isPlausibleQuad(result, bitmap.width, bitmap.height)) result else fullFrame
        } catch (e: Exception) {
            android.util.Log.w("Artify", "Edge detection failed", e)
            return fullFrame
        } finally {
            rgba.release()
            gray.release()
            edges.release()
            hierarchy.release()
            contours.forEach { it.release() }
            if (small !== bitmap) small.recycle()
        }
    }

    /** Rejects quads with tiny sides or extreme aspect ratios. */
    private fun isPlausibleQuad(q: NormalizedCornerPoints, width: Int, height: Int): Boolean {
        fun dist(a: PointF, b: PointF): Float {
            val dx = (a.x - b.x) * width
            val dy = (a.y - b.y) * height
            return sqrt(dx * dx + dy * dy)
        }
        val top = dist(q.topLeft, q.topRight)
        val bottom = dist(q.bottomLeft, q.bottomRight)
        val left = dist(q.topLeft, q.bottomLeft)
        val right = dist(q.topRight, q.bottomRight)
        val minSide = min(min(top, bottom), min(left, right))
        val aspect = (top + bottom) / (left + right)
        return minSide >= 0.2f * min(width, height) && aspect in 0.3f..3.5f
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
