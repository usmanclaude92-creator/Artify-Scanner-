package com.example.auth

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** Small in-app notification list (welcome message, saved documents) shown from the header bell. */
object NotificationCenter {

    data class Item(val title: String, val body: String, val time: Long)

    val items = mutableStateListOf<Item>()
    val unread = mutableIntStateOf(0)

    private const val PREFS = "artify_notifications"

    fun init(context: Context) {
        if (items.isNotEmpty()) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        try {
            val arr = JSONArray(p.getString("items", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(Item(o.getString("t"), o.getString("b"), o.getLong("ts")))
            }
        } catch (_: Exception) {
        }
        unread.intValue = p.getInt("unread", 0)
    }

    fun add(context: Context, title: String, body: String) {
        items.add(0, Item(title, body, System.currentTimeMillis()))
        while (items.size > 30) items.removeAt(items.size - 1)
        unread.intValue += 1
        save(context)
    }

    fun markAllRead(context: Context) {
        unread.intValue = 0
        save(context)
    }

    fun clear(context: Context) {
        items.clear()
        unread.intValue = 0
        save(context)
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("t", it.title).put("b", it.body).put("ts", it.time)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("items", arr.toString()).putInt("unread", unread.intValue).apply()
    }
}
