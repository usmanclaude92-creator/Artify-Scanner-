package com.example.ui.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend

/** Sends a document's OCR text plus a user request to Gemini via Firebase AI Logic. */
object DocumentAi {

    private const val MODEL = "gemini-2.5-flash"
    private const val MAX_DOCUMENT_CHARS = 30_000

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(MODEL)
    }

    suspend fun ask(title: String, ocrText: String, request: String): Result<String> {
        if (ocrText.isBlank()) {
            return Result.failure(IllegalStateException("This document has no recognized text to analyze."))
        }
        val prompt = buildString {
            appendLine("You are a careful document assistant. Answer using ONLY the document text below.")
            appendLine("If the answer is not in the text, say so. Do not invent facts, dates or legal conclusions,")
            appendLine("and remind the user that you are not a lawyer when explaining contract clauses.")
            appendLine()
            appendLine("Document title: $title")
            appendLine("--- DOCUMENT TEXT ---")
            appendLine(ocrText.take(MAX_DOCUMENT_CHARS))
            appendLine("--- END DOCUMENT TEXT ---")
            appendLine()
            append("Request: $request")
        }
        return try {
            val text = model.generateContent(prompt).text
            if (text.isNullOrBlank()) Result.failure(IllegalStateException("The model returned an empty answer."))
            else Result.success(text)
        } catch (e: Exception) {
            android.util.Log.e("DocumentAi", "Gemini request failed", e)
            Result.failure(e)
        }
    }

    fun describeError(e: Throwable): String = when (e) {
        is IllegalStateException ->
            if (e.message?.contains("FirebaseApp") == true)
                "AI is not configured. Add google-services.json with Firebase AI Logic enabled."
            else e.message ?: "AI request failed."
        else -> "AI request failed: ${e.message ?: "unknown error"}. Check your connection and Firebase AI Logic setup."
    }
}
