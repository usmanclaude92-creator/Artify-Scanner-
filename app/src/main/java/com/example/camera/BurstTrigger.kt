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
        val message: String = "Point at the first page"
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
        publish(Phase.SEARCHING, 0f, "Point at the first page")
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

            if (phase == Phase.TURN_PAGE) {
                if (motion >= TURN_MOTION) sawMovement = true
                if (sawMovement && motion < STEADY_MOTION) {
                    phase = Phase.SEARCHING
                    steadyTicks = 0
                    publish(Phase.SEARCHING, 0f, "Hold the next page steady")
                } else {
                    publish(Phase.TURN_PAGE, 0f, "Captured. Turn the page...")
                }
                return
            }

            steadyTicks = if (motion < STEADY_MOTION) steadyTicks + 1 else 0
            if (steadyTicks == 0) {
                phase = Phase.SEARCHING
                publish(Phase.SEARCHING, 0f, "Hold steady...")
            } else if (steadyTicks >= NEEDED_TICKS) {
                captureInFlight = true
                phase = Phase.CAPTURING
                publish(Phase.CAPTURING, 1f, "Capturing...")
                main.post { onCaptureRequested?.invoke() }
            } else {
                phase = Phase.HOLD
                publish(Phase.HOLD, steadyTicks.toFloat() / NEEDED_TICKS, "Hold steady...")
            }
        } catch (e: Exception) {
            android.util.Log.w("Artify", "Burst analysis failed", e)
        }
    }

    private fun publish(phase: Phase, progress: Float, message: String) {
        val next = Status(phase, progress, message)
        main.post { status.value = next }
    }

    /** Coarse brightness grid of the Y plane; enough to tell a steady scene from a moving one. */
    private fun signature(image: ImageProxy): IntArray? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val w = image.width
        val h = image.height
        val sig = IntArray(GRID * GRID)
        for (gy in 0 until GRID) {
            val y = ((gy + 0.5f) * h / GRID).toInt().coerceIn(0, h - 1)
            for (gx in 0 until GRID) {
                val x = ((gx + 0.5f) * w / GRID).toInt().coerceIn(0, w - 1)
                val pos = y * rowStride + x
                if (pos >= buffer.limit()) return null
                sig[gy * GRID + gx] = buffer.get(pos).toInt() and 0xFF
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
        const val NEEDED_TICKS = 7          // about 1 s without movement
        const val GRID = 24
        const val STEADY_MOTION = 3f        // average brightness change between ticks that still counts as steady
        const val TURN_MOTION = 7f          // movement that proves the page was turned or moved away
    }
}
