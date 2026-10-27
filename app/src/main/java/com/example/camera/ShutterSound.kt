package com.example.camera

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.R

/** Camera shutter click played for every captured page. */
object ShutterSound {
    private var pool: SoundPool? = null
    private var soundId = 0
    private var ready = false

    private fun ensure(context: Context) {
        if (pool != null) return
        val sp = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        sp.setOnLoadCompleteListener { _, _, status -> ready = status == 0 }
        soundId = sp.load(context.applicationContext, R.raw.shutter, 1)
        pool = sp
    }

    /** Load ahead of the first capture so the click has no delay. */
    fun preload(context: Context) = ensure(context)

    fun play(context: Context) {
        try {
            ensure(context)
            if (ready) pool?.play(soundId, 1f, 1f, 1, 0, 1f)
        } catch (e: Exception) {
            android.util.Log.w("Artify", "Shutter sound failed", e)
        }
    }

    fun release() {
        pool?.release()
        pool = null
        ready = false
    }
}
