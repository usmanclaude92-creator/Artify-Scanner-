package com.example.imageprocessing

import org.opencv.android.OpenCVLoader

/** Loads the bundled OpenCV native library once. */
object OpenCvSupport {
    private val loaded: Boolean by lazy {
        try {
            OpenCVLoader.initLocal()
        } catch (e: Throwable) {
            android.util.Log.w("Artify", "OpenCV unavailable", e)
            false
        }
    }

    fun ensureLoaded(): Boolean = loaded
}
