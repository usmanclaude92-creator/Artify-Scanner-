package com.example.camera

import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.mutableStateOf
import com.example.data.model.NormalizedCornerPoints
import com.example.imageprocessing.ImageProcessor
import com.example.imageprocessing.OpenCvSupport
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max

/**
 * Hands-free page-after-page capture. Watches the live preview for a page outline, waits until it is
 * steady and sharp, asks the screen to take the photo, then waits for the page to be turned before
 * looking for the next one. It never stops by itself; the user finishes the session.
 */
class BurstScanner {

    enum class Phase { SEARCHING, HOLD, CAPTURING, TURN_PAGE }

    data class Status(
        val phase: Phase = Phase.SEARCHING,
        val quad: NormalizedCornerPoints? = null,
        val progress: Float = 0f,
        val message: String = "Looking for a document..."
    )

    val status = mutableStateOf(Status())

    /** Called on the main thread with the steady page outline (normalized to the upright frame). */
    var onCaptureRequested: ((NormalizedCornerPoints) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var lastTick = 0L
    private var prevQuad: NormalizedCornerPoints? = null
    private var stableTicks = 0
    private var lostSince = 0L
    private var lastCapturedSig: IntArray? = null
    private var phase = Phase.SEARCHING

    @Volatile
    private var captureInFlight = false

    /** The screen calls this when the photo has been saved (or failed), re-arming the scanner. */
    fun captureFinished(success: Boolean) {
        captureInFlight = false
        phase = if (success) Phase.TURN_PAGE else Phase.SEARCHING
        stableTicks = 0
        lostSince = 0L
        publish(phase, null, 0f, if (success) "Page captured. Turn the page..." else "Capture failed, trying again...")
    }

    fun reset() {
        captureInFlight = false
        phase = Phase.SEARCHING
        prevQuad = null
        stableTicks = 0
        lastCapturedSig = null
        publish(Phase.SEARCHING, null, 0f, "Looking for a document...")
    }

    fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (captureInFlight || now - lastTick < TICK_MS || !OpenCvSupport.ensureLoaded()) return
        lastTick = now

        var gray: Mat? = null
        try {
            gray = uprightGray(image) ?: return
            val sig = signature(gray)
            val quad = ImageProcessor.detectQuadInGray(gray, gray.cols(), gray.rows())

            if (phase == Phase.TURN_PAGE) {
                handleTurnPage(now, quad, sig)
                return
            }

            if (quad == null) {
                stableTicks = 0
                prevQuad = null
                phase = Phase.SEARCHING
                publish(Phase.SEARCHING, null, 0f, "Looking for a document...")
                return
            }

            val moved = prevQuad?.let { cornerShift(it, quad) } ?: 1f
            prevQuad = quad
            stableTicks = if (moved < STEADY_SHIFT) stableTicks + 1 else 0

            val sameAsLast = lastCapturedSig?.let { signatureDiff(it, sig) < NEW_PAGE_DIFF } ?: false
            if (sameAsLast) {
                publish(Phase.HOLD, quad, 0f, "Show the next page")
                return
            }

            if (stableTicks == 0) {
                phase = Phase.SEARCHING
                publish(Phase.SEARCHING, quad, 0f, "Hold steady...")
                return
            }

            val progress = (stableTicks.toFloat() / NEEDED_TICKS).coerceAtMost(1f)
            phase = Phase.HOLD
            if (stableTicks >= NEEDED_TICKS && sharpness(gray) >= MIN_SHARPNESS) {
                captureInFlight = true
                phase = Phase.CAPTURING
                lastCapturedSig = sig
                publish(Phase.CAPTURING, quad, 1f, "Capturing...")
                main.post { onCaptureRequested?.invoke(quad) }
            } else {
                publish(Phase.HOLD, quad, progress, if (stableTicks >= NEEDED_TICKS) "Focusing..." else "Hold steady...")
            }
        } catch (e: Exception) {
            android.util.Log.w("Artify", "Burst analysis failed", e)
        } finally {
            gray?.release()
        }
    }

    private fun handleTurnPage(now: Long, quad: NormalizedCornerPoints?, sig: IntArray) {
        val changed = lastCapturedSig?.let { signatureDiff(it, sig) >= PAGE_TURNED_DIFF } ?: true
        if (quad == null) {
            if (lostSince == 0L) lostSince = now
        } else {
            lostSince = 0L
        }
        val gone = lostSince != 0L && now - lostSince > LOST_MS
        if (changed || gone) {
            phase = Phase.SEARCHING
            prevQuad = null
            stableTicks = 0
            lostSince = 0L
            publish(Phase.SEARCHING, quad, 0f, "Looking for the next page...")
        } else {
            publish(Phase.TURN_PAGE, null, 0f, "Page captured. Turn the page...")
        }
    }

    private fun publish(phase: Phase, quad: NormalizedCornerPoints?, progress: Float, message: String) {
        val next = Status(phase, quad, progress, message)
        main.post { status.value = next }
    }

    /** Luma plane rotated upright and downscaled, lightly blurred. Caller releases the Mat. */
    private fun uprightGray(image: ImageProxy): Mat? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val w = image.width
        val h = image.height
        val raw = Mat(h, w, CvType.CV_8UC1)
        val row = ByteArray(w)
        for (y in 0 until h) {
            val pos = y * rowStride
            if (pos + w > buffer.limit()) {
                raw.release()
                return null
            }
            buffer.position(pos)
            buffer.get(row, 0, w)
            raw.put(y, 0, row)
        }
        val rotated = Mat()
        when (image.imageInfo.rotationDegrees) {
            90 -> Core.rotate(raw, rotated, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(raw, rotated, Core.ROTATE_180)
            270 -> Core.rotate(raw, rotated, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> raw.copyTo(rotated)
        }
        raw.release()
        val scale = FRAME_MAX_SIDE.toDouble() / max(rotated.cols(), rotated.rows())
        val small = Mat()
        if (scale < 1.0) Imgproc.resize(rotated, small, Size(), scale, scale, Imgproc.INTER_AREA) else rotated.copyTo(small)
        rotated.release()
        Imgproc.GaussianBlur(small, small, Size(5.0, 5.0), 0.0)
        return small
    }

    private fun signature(gray: Mat): IntArray {
        val tiny = Mat()
        Imgproc.resize(gray, tiny, Size(SIG_SIZE.toDouble(), SIG_SIZE.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        val bytes = ByteArray(SIG_SIZE * SIG_SIZE)
        tiny.get(0, 0, bytes)
        tiny.release()
        return IntArray(bytes.size) { bytes[it].toInt() and 0xFF }
    }

    private fun signatureDiff(a: IntArray, b: IntArray): Float {
        var sum = 0L
        for (i in a.indices) sum += abs(a[i] - b[i])
        return sum.toFloat() / a.size
    }

    private fun sharpness(gray: Mat): Double {
        val lap = Mat()
        val mean = MatOfDouble()
        val std = MatOfDouble()
        Imgproc.Laplacian(gray, lap, CvType.CV_64F)
        Core.meanStdDev(lap, mean, std)
        val s = std.toArray().firstOrNull() ?: 0.0
        lap.release(); mean.release(); std.release()
        return s * s
    }

    private fun cornerShift(a: NormalizedCornerPoints, b: NormalizedCornerPoints): Float {
        fun d(p: PointF, q: PointF) = max(abs(p.x - q.x), abs(p.y - q.y))
        return maxOf(
            d(a.topLeft, b.topLeft), d(a.topRight, b.topRight),
            d(a.bottomRight, b.bottomRight), d(a.bottomLeft, b.bottomLeft)
        )
    }

    private companion object {
        const val TICK_MS = 140L
        const val NEEDED_TICKS = 6          // about 0.85 s of a steady outline
        const val STEADY_SHIFT = 0.03f      // corners may drift 3% of the frame between ticks
        const val MIN_SHARPNESS = 20.0
        const val FRAME_MAX_SIDE = 480
        const val SIG_SIZE = 16
        const val NEW_PAGE_DIFF = 9f        // must differ this much from the last page to count as new
        const val PAGE_TURNED_DIFF = 16f    // scene change that proves the page was turned
        const val LOST_MS = 700L
    }
}
