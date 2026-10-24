package com.example.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Biometric / device-credential gate for the document library. */
object AppLock {

    private const val PREFS = "artify_security"
    private const val KEY_ENABLED = "app_lock_enabled"
    private const val RELOCK_AFTER_MS = 30_000L

    /** True while the library must stay hidden behind the lock screen. */
    val locked = mutableStateOf(false)

    private var backgroundedAt = 0L

    // Device credentials as a fallback are only supported by BiometricPrompt on API 30+
    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        else BIOMETRIC_WEAK

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) locked.value = false
    }

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    /** Call from Activity.onCreate. */
    fun lockIfEnabledOnLaunch(context: Context) {
        locked.value = isEnabled(context)
    }

    fun onAppBackgrounded() {
        backgroundedAt = System.currentTimeMillis()
    }

    fun onAppForegrounded(context: Context) {
        if (isEnabled(context) && !locked.value &&
            System.currentTimeMillis() - backgroundedAt > RELOCK_AFTER_MS
        ) {
            locked.value = true
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Artify Scanner",
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure(errString.toString())
                }
            }
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(authenticators)
        if (authenticators and DEVICE_CREDENTIAL == 0) builder.setNegativeButtonText("Cancel")
        prompt.authenticate(builder.build())
    }
}
