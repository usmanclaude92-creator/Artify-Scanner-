package com.example.camera

import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.mutableStateOf
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
        val message: String = "Scanning\u2026"
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
        publish(phase, 0f, if (success) "Captured. Turn the page..." else "Capture failed, trying again...")
    }

    fun reset() {
        captureInFlight = false
        steadyTicks = 0
        sawMovement = false
        prev = null
        phase = Phase.SEARCHING
        publish(Phase.SEARCHING, 0f, "Scanning\u2026")
    }

    fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (!enabled) {
            if (status.value.phase != Phase.SEARCHING || status.value.message != MANUAL_MESSAGE) publish(Phase.SEARCHING, 0f, MANUAL_MESSAGE)
            return
        }
        if (captureInFlight || now - lastTick < TICK_MS) return
        lastTick = now
        try {
            val sig = signature(image) ?: return
            val before = prev
            prev = sig
            if (before == null) return
            val motion = diff(before, sig)
            // A dark frame (lens covered, no light) is never a page
            if (sig.average() < MIN_LUMA) {
                steadyTicks = 0
                if (phase != Phase.TURN_PAGE) publish(Phase.SEARCHING, 0f, "Scanning\u2026")
                return
            }

            if (phase == Phase.TURN_PAGE) {
                if (motion >= TURN_MOTION) sawMovement = true
                if (sawMovement && motion < STEADY_MOTION) {
                    phase = Phase.SEARCHING
                    steadyTicks = 0
                    publish(Phase.SEARCHING, 0f, "Scanning\u2026 hold steady")
                } else {
                    publish(Phase.TURN_PAGE, 0f, "Captured. Turn the page...")
                }
                return
            }

            // Small shake neither counts nor resets; only real movement restarts the wait
            steadyTicks = when {
                motion < STEADY_MOTION -> steadyTicks + 1
                motion < SHAKE_MOTION -> steadyTicks
                else -> 0
            }
            if (steadyTicks == 0) {
                phase = Phase.SEARCHING
                publish(Phase.SEARCHING, 0f, "Scanning\u2026 hold steady")
            } else if (steadyTicks >= NEEDED_TICKS) {
                captureInFlight = true
                phase = Phase.CAPTURING
                publish(Phase.CAPTURING, 1f, "Capturing...")
                main.post { onCaptureRequested?.invoke() }
            } else {
                phase = Phase.HOLD
                publish(Phase.HOLD, steadyTicks.toFloat() / NEEDED_TICKS, "Scanning\u2026 hold steady")
            }
        } catch (e: Exception) {
            android.util.Log.w("Artify", "Burst analysis failed", e)
        }
    }

    private fun publish(phase: Phase, progress: Float, message: String) {
        val next = Status(phase, progress, message)
        main.post { status.value = next }
    }

    /** Coarse grid of block-averaged brightness, so hand shake and sensor noise barely change it. */
    private fun signature(image: ImageProxy): IntArray? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val w = image.width
        val h = image.height
        val cellW = w / GRID
        val cellH = h / GRID
        if (cellW < SAMPLES || cellH < SAMPLES) return null
        val sig = IntArray(GRID * GRID)
        for (gy in 0 until GRID) {
            for (gx in 0 until GRID) {
                var sum = 0
                for (sy in 0 until SAMPLES) {
                    val y = gy * cellH + (sy * 2 + 1) * cellH / (SAMPLES * 2)
                    for (sx in 0 until SAMPLES) {
                        val x = gx * cellW + (sx * 2 + 1) * cellW / (SAMPLES * 2)
                        val pos = y * rowStride + x
                        if (pos >= buffer.limit()) return null
                        sum += buffer.get(pos).toInt() and 0xFF
                    }
                }
                sig[gy * GRID + gx] = sum / (SAMPLES * SAMPLES)
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
        const val SAMPLES = 4               // sample points per grid cell side
        const val GRID = 24
        const val MIN_LUMA = 45.0           // average brightness (0-255) needed before anything is captured
        const val STEADY_MOTION = 4f        // average brightness change between ticks that still counts as steady
        const val SHAKE_MOTION = 9f         // above this the camera is really moving
        const val TURN_MOTION = 9f          // movement that proves the page was turned or moved away
    }
}
