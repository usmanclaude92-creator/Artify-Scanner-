package com.example.auth

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val photoUrl: String?,
    val provider: String, // "Google", "Email" or "Guest"
    val isGuest: Boolean
)

/**
 * Sign-in with Firebase Authentication (email + password, Google) plus a local guest profile.
 * Firebase only works once google-services.json is part of the build; without it the app reports that
 * sign-in is not configured and guests can still use everything on the device.
 */
object AuthManager {

    private const val PREFS = "artify_auth"
    const val NOT_CONFIGURED =
        "Sign-in is not set up in this build yet (google-services.json missing). You can continue as a guest."

    val profile = mutableStateOf<UserProfile?>(null)

    /** Bumped whenever the profile picture changes so avatars reload. */
    val photoVersion = mutableIntStateOf(0)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isConfigured(context: Context): Boolean = try {
        FirebaseApp.getApps(context).isNotEmpty()
    } catch (e: Exception) {
        false
    }

    fun init(context: Context) {
        val app = context.applicationContext
        if (isConfigured(app)) {
            FirebaseAuth.getInstance().currentUser?.let {
                profile.value = fromFirebase(app, it)
                return
            }
        }
        if (prefs(app).getBoolean("guest", false)) profile.value = guestProfile(app)
    }

    private fun fromFirebase(context: Context, user: FirebaseUser): UserProfile {
        val provider = if (user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }) "Google" else "Email"
        val override = prefs(context).getString("name_${user.uid}", null)
        return UserProfile(
            id = user.uid,
            name = override ?: user.displayName?.takeIf { it.isNotBlank() } ?: user.email?.substringBefore('@') ?: "Artify User",
            email = user.email ?: "",
            photoUrl = user.photoUrl?.toString(),
            provider = provider,
            isGuest = false
        )
    }

    private fun guestProfile(context: Context) = UserProfile(
        id = "guest",
        name = prefs(context).getString("name_guest", null) ?: "Guest",
        email = "Not signed in",
        photoUrl = null,
        provider = "Guest",
        isGuest = true
    )

    fun continueAsGuest(context: Context) {
        val app = context.applicationContext
        prefs(app).edit().putBoolean("guest", true).apply()
        profile.value = guestProfile(app)
        welcomeOnce(app)
    }

    suspend fun signUpWithEmail(context: Context, name: String, email: String, password: String): Result<Unit> = runCatching {
        check(isConfigured(context)) { NOT_CONFIGURED }
        val auth = FirebaseAuth.getInstance()
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).awaitResult()
        val user = result.user ?: error("Could not create the account")
        if (name.isNotBlank()) {
            user.updateProfile(userProfileChangeRequest { displayName = name.trim() }).awaitResult()
            prefs(context).edit().putString("name_${user.uid}", name.trim()).apply()
        }
        prefs(context).edit().putBoolean("guest", false).apply()
        profile.value = fromFirebase(context.applicationContext, user)
        welcomeOnce(context.applicationContext)
    }.mapError()

    suspend fun signInWithEmail(context: Context, email: String, password: String): Result<Unit> = runCatching {
        check(isConfigured(context)) { NOT_CONFIGURED }
        val result = FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password).awaitResult()
        val user = result.user ?: error("Sign-in failed")
        prefs(context).edit().putBoolean("guest", false).apply()
        profile.value = fromFirebase(context.applicationContext, user)
        welcomeOnce(context.applicationContext)
    }.mapError()

    suspend fun signInWithGoogle(activity: Activity): Result<Unit> = runCatching {
        check(isConfigured(activity)) { NOT_CONFIGURED }
        val resId = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        check(resId != 0) { "Google sign-in needs the Web client ID from google-services.json." }
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(activity.getString(resId))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected sign-in response"
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val result = FirebaseAuth.getInstance()
            .signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).awaitResult()
        val user = result.user ?: error("Sign-in failed")
        prefs(activity).edit().putBoolean("guest", false).apply()
        profile.value = fromFirebase(activity.applicationContext, user)
        welcomeOnce(activity.applicationContext)
    }.mapError()

    suspend fun sendPasswordReset(context: Context, email: String): Result<Unit> = runCatching {
        check(isConfigured(context)) { NOT_CONFIGURED }
        require(email.isNotBlank()) { "Enter your email first" }
        FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim()).awaitResult()
        Unit
    }.mapError()

    fun signOut(context: Context) {
        val app = context.applicationContext
        if (isConfigured(app)) FirebaseAuth.getInstance().signOut()
        prefs(app).edit().putBoolean("guest", false).apply()
        profile.value = null
    }

    suspend fun updateName(context: Context, name: String) {
        val app = context.applicationContext
        val current = profile.value ?: return
        val clean = name.trim().ifBlank { return }
        prefs(app).edit().putString("name_${current.id}", clean).apply()
        if (!current.isGuest && isConfigured(app)) {
            try {
                FirebaseAuth.getInstance().currentUser
                    ?.updateProfile(userProfileChangeRequest { displayName = clean })?.awaitResult()
            } catch (_: Exception) {
                // The name is kept locally even if the server update fails
            }
        }
        profile.value = current.copy(name = clean)
    }

    // ---- profile picture (stored privately on the device) ----

    fun photoFile(context: Context): File? =
        prefs(context).getString("photo_path", null)?.let(::File)?.takeIf { it.exists() }

    fun setPhoto(context: Context, uri: Uri): Boolean {
        val app = context.applicationContext
        return try {
            val target = File(app.filesDir, "profile_photo_${System.currentTimeMillis()}.jpg")
            app.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                ?: return false
            photoFile(app)?.delete()
            prefs(app).edit().putString("photo_path", target.absolutePath).apply()
            photoVersion.intValue++
            true
        } catch (e: Exception) {
            false
        }
    }

    fun removePhoto(context: Context) {
        val app = context.applicationContext
        photoFile(app)?.delete()
        prefs(app).edit().remove("photo_path").apply()
        photoVersion.intValue++
    }

    private fun welcomeOnce(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean("welcomed", false)) {
            p.edit().putBoolean("welcomed", true).apply()
            NotificationCenter.add(context, "Welcome to Artify Scanner", "Scan, organize and share your documents in seconds.")
        }
    }

    private fun <T> Result<T>.mapError(): Result<T> = fold(
        onSuccess = { this },
        onFailure = { Result.failure(IllegalStateException(friendly(it))) }
    )

    private fun friendly(e: Throwable): String = when ((e as? FirebaseAuthException)?.errorCode) {
        "ERROR_INVALID_EMAIL" -> "That email address looks wrong."
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "Wrong email or password."
        "ERROR_USER_NOT_FOUND" -> "No account with that email. Try signing up."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Try logging in."
        "ERROR_WEAK_PASSWORD" -> "Choose a stronger password (at least 6 characters)."
        "ERROR_NETWORK_REQUEST_FAILED" -> "No internet connection."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }
}
