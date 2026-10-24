package com.example.camera

import android.app.Activity
import android.content.IntentSender
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning

/**
 * Google's ML Kit Document Scanner: ML-based page detection, perspective crop and enhancement.
 * It runs on-device through Google Play services and needs no API key.
 */
object MlKitScanner {

    fun createIntentSender(
        activity: Activity,
        maxPages: Int,
        onReady: (IntentSender) -> Unit,
        onError: (String) -> Unit
    ) {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(maxPages)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener(onReady)
            .addOnFailureListener { onError(it.message ?: "Google scanner unavailable") }
    }
}
