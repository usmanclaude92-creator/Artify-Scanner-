package com.example.camera

import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.mutableStateOf
import com.example.data.model.NormalizedCornerPoints
import com.example.imageprocessing.PageDetector
import android.graphics.PointF
import kotlin.math.abs

/**
 * Hands-free page-after-page capture. Watches the live preview, waits until the scene is steady,
 * asks the screen to take the photo, then waits until the page is moved or turned before arming
 * again. It never stops by itself; the user finishes the session.
 */
class BurstTrigger {

    enum class Phase { SEARCHING, HOLD, CAPTURING, TURN_PAGE }

    data class Status(
        val phase: Phase = Phase.SEARCHING,
        val progress: Float = 0f,
        val message: String = "Scanning\u2026",
        val quad: NormalizedCornerPoints? = null
    )

    val status = mutableStateOf(Status())

    /** Called on the main thread when the scene has been steady long enough. */
    var onCaptureRequested: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var lastTick = 0L
    private var prev: IntArray? = null
    private var steadyTicks = 0
    private var phase = Phase.SEARCHING
    private var sawMovement = false

    /** False in Manual mode: the screen only shows the camera and the shutter button takes the photo. */
    @Volatile
    var enabled = true

    @Volatile
    private var captureInFlight = false

    /** The screen calls this once the photo has been saved (or failed), so the next page can be found. */
    fun captureFinished(success: Boolean) {
        captureInFlight = false
        steadyTicks = 0
        sawMovement = false
        phase = if (success) Phase.TURN_PAGE else Phase.SEARCHING
        publish(phase, 0f, if (success) "Captured. Turn the page..." else "Capture failed, trying again...", null)
    }

    fun reset() {
        captureInFlight = false
        steadyTicks = 0
        sawMovement = false
        prev = null
        phase = Phase.SEARCHING
        publish(Phase.SEARCHING, 0f, "Scanning\u2026", null)
    }

    /** Latest page outline seen in the preview (normalized to the upright frame), for cropping the photo. */
    @Volatile
    var lastQuad: NormalizedCornerPoints? = null
        private set

    private var smoothQuad: NormalizedCornerPoints? = null
    private var quadMisses = 0

    fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (captureInFlight || now - lastTick < TICK_MS) return
        lastTick = now
        try {
            val grid = uprightGrid(image) ?: return
            val sig = signature(grid)
            val before = prev
            prev = sig
            if (before == null) return
            val motion = diff(before, sig)

            // Page outline, lightly smoothed so the blue frame does not jitter
            val found = PageDetector.detect(grid, UW, UH)
            val old = smoothQuad
            val quad: NormalizedCornerPoints? = when {
                found == null -> if (++quadMisses >= 3) null else old
                else -> { quadMisses = 0; if (old != null && quadShift(old, found) < 0.08f) blend(old, found) else found }
            }
            smoothQuad = quad
            lastQuad = quad
            val quadSteady = old != null && quad != null && quadShift(old, quad) < QUAD_STEADY

            if (!enabled) {
                publish(Phase.SEARCHING, 0f, MANUAL_MESSAGE, quad)
                return
            }

            // A dark frame (lens covered, no light) is never a page
            if (sig.average() < MIN_LUMA) {
                steadyTicks = 0
                if (phase != Phase.TURN_PAGE) publish(Phase.SEARCHING, 0f, "Scanning\u2026", null)
                return
            }

            if (phase == Phase.TURN_PAGE) {
                if (motion >= TURN_MOTION || quad == null) sawMovement = true
                if (sawMovement && quad != null && motion < STEADY_MOTION) {
                    phase = Phase.SEARCHING
                    steadyTicks = 0
                    publish(Phase.SEARCHING, 0f, "Scanning\u2026 hold steady", quad)
                } else {
                    publish(Phase.TURN_PAGE, 0f, "Captured. Turn the page...", null)
                }
                return
            }

            if (quad == null) {
                steadyTicks = 0
                phase = Phase.SEARCHING
                publish(Phase.SEARCHING, 0f, "Scanning\u2026", null)
                return
            }

            // Small shake neither counts nor resets; only real movement restarts the wait
            steadyTicks = when {
                motion < STEADY_MOTION && quadSteady -> steadyTicks + 1
                motion < SHAKE_MOTION -> steadyTicks
                else -> 0
            }
            if (steadyTicks >= NEEDED_TICKS) {
                captureInFlight = true
                phase = Phase.CAPTURING
                publish(Phase.CAPTURING, 1f, "Capturing\u2026", quad)
                main.post { onCaptureRequested?.invoke() }
            } else {
                phase = Phase.HOLD
                publish(Phase.HOLD, steadyTicks.toFloat() / NEEDED_TICKS, "Scanning\u2026 hold steady", quad)
            }
        } catch (e: Exception) {
            android.util.Log.w("Artify", "Burst analysis failed", e)
        }
    }

    private fun publish(phase: Phase, progress: Float, message: String, quad: NormalizedCornerPoints?) {
        val next = Status(phase, progress, message, quad)
        main.post { status.value = next }
    }

    private fun quadShift(a: NormalizedCornerPoints, b: NormalizedCornerPoints): Float {
        fun d(p: PointF, q: PointF) = maxOf(abs(p.x - q.x), abs(p.y - q.y))
        return maxOf(d(a.topLeft, b.topLeft), d(a.topRight, b.topRight), d(a.bottomRight, b.bottomRight), d(a.bottomLeft, b.bottomLeft))
    }

    private fun blend(a: NormalizedCornerPoints, b: NormalizedCornerPoints): NormalizedCornerPoints {
        fun m(p: PointF, q: PointF) = PointF((p.x + q.x) / 2f, (p.y + q.y) / 2f)
        return NormalizedCornerPoints(m(a.topLeft, b.topLeft), m(a.topRight, b.topRight), m(a.bottomRight, b.bottomRight), m(a.bottomLeft, b.bottomLeft))
    }

    /** Y plane rotated upright and reduced to a UW x UH grid (each cell averages 2x2 source pixels). */
    private fun uprightGrid(image: ImageProxy): IntArray? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val sw = image.width
        val sh = image.height
        val rotation = image.imageInfo.rotationDegrees
        val grid = IntArray(UW * UH)
        for (yu in 0 until UH) {
            val v = (yu + 0.5f) / UH
            for (xu in 0 until UW) {
                val u = (xu + 0.5f) / UW
                val a: Float
                val b: Float
                when (rotation) {
                    90 -> { a = v; b = 1f - u }
                    180 -> { a = 1f - u; b = 1f - v }
                    270 -> { a = 1f - v; b = u }
                    else -> { a = u; b = v }
                }
                val x = (a * sw).toInt().coerceIn(0, sw - 2)
                val y = (b * sh).toInt().coerceIn(0, sh - 2)
                val p = y * rowStride + x
                if (p + rowStride + 1 >= buffer.limit()) return null
                grid[yu * UW + xu] = ((buffer.get(p).toInt() and 0xFF) + (buffer.get(p + 1).toInt() and 0xFF) +
                    (buffer.get(p + rowStride).toInt() and 0xFF) + (buffer.get(p + rowStride + 1).toInt() and 0xFF)) / 4
            }
        }
        return grid
    }

    /** Coarse block-averaged brightness; hand shake and sensor noise barely change it. */
    private fun signature(grid: IntArray): IntArray {
        val bw = UW / GRID
        val bh = UH / GRID
        val sig = IntArray(GRID * GRID)
        for (gy in 0 until GRID) {
            for (gx in 0 until GRID) {
                var sum = 0
                for (y in 0 until bh) for (x in 0 until bw) sum += grid[(gy * bh + y) * UW + gx * bw + x]
                sig[gy * GRID + gx] = sum / (bw * bh)
            }
        }
        return sig
    }

    private fun diff(a: IntArray, b: IntArray): Float {
        var sum = 0L
        for (i in a.indices) sum += abs(a[i] - b[i])
        return sum.toFloat() / a.size
    }

    private companion object {
        const val MANUAL_MESSAGE = "Tap the shutter to capture"
        const val TICK_MS = 140L
        const val NEEDED_TICKS = 6          // about 0.85 s without real movement
        const val GRID = 24
        const val UW = 144                  // preview grid used for page detection and motion (3:4, upright)
        const val UH = 192
        const val QUAD_STEADY = 0.025f      // page outline may drift 2.5% of the frame between ticks
        const val MIN_LUMA = 45.0           // average brightness (0-255) needed before anything is captured
        const val STEADY_MOTION = 4f        // average brightness change between ticks that still counts as steady
        const val SHAKE_MOTION = 9f         // above this the camera is really moving
        const val TURN_MOTION = 9f          // movement that proves the page was turned or moved away
    }
}
