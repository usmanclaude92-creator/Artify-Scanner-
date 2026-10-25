package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth

/**
 * Identifies whose data is on screen: the signed-in Firebase user, or "guest". Documents and per-user
 * preferences are stored separately for each key, so every account sees only its own library and settings.
 */
object AccountScope {
    private const val MIGRATED = "__migrated"

    fun key(): String = try {
        FirebaseAuth.getInstance().currentUser?.uid
    } catch (e: Exception) {
        null
    } ?: "guest"

    /**
     * Preferences file of the current account. The first time an account opens a file, it starts from the
     * phone's earlier (pre-accounts) values when [inheritDevice] is true, so nobody loses their settings.
     */
    fun prefs(context: Context, name: String, inheritDevice: Boolean = true): SharedPreferences {
        val app = context.applicationContext
        val scoped = app.getSharedPreferences("${name}__${key()}", Context.MODE_PRIVATE)
        if (!scoped.contains(MIGRATED)) {
            val edit = scoped.edit()
            if (inheritDevice) {
                for ((k, v) in app.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
                    when (v) {
                        is Boolean -> edit.putBoolean(k, v)
                        is Int -> edit.putInt(k, v)
                        is Long -> edit.putLong(k, v)
                        is Float -> edit.putFloat(k, v)
                        is String -> edit.putString(k, v)
                    }
                }
            }
            edit.putBoolean(MIGRATED, true).apply()
        }
        return scoped
    }
}
