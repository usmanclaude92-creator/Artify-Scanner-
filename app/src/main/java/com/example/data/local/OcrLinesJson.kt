package com.example.data.local

import com.example.data.model.OcrLine
import org.json.JSONArray
import org.json.JSONObject

/** Compact JSON (de)serialization for OCR line boxes stored in [PageEntity.ocrLinesJson]. */
object OcrLinesJson {

    fun encode(lines: List<OcrLine>): String {
        val arr = JSONArray()
        for (l in lines) {
            arr.put(
                JSONObject()
                    .put("t", l.text)
                    .put("l", l.left.toDouble())
                    .put("T", l.top.toDouble())
                    .put("r", l.right.toDouble())
                    .put("b", l.bottom.toDouble())
            )
        }
        return arr.toString()
    }

    fun decode(json: String): List<OcrLine> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                OcrLine(
                    o.getString("t"),
                    o.getDouble("l").toFloat(),
                    o.getDouble("T").toFloat(),
                    o.getDouble("r").toFloat(),
                    o.getDouble("b").toFloat()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
