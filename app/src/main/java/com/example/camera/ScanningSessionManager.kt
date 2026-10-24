package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.example.data.model.CapturedPage
import com.example.data.model.ScanFilter
import com.example.data.model.ScanMode
import com.example.imageprocessing.ImageProcessor
import com.example.ocr.OcrEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

object ScanningSessionManager {

    val capturedPages = mutableStateListOf<CapturedPage>()
    val scanMode = mutableStateOf(ScanMode.BATCH_DOCUMENTS)
    val isAutoCapture = mutableStateOf(false)
    val isFlashOn = mutableStateOf(false)
    val isProcessingCapture = mutableStateOf(false)
    val statusMessage = mutableStateOf<String?>(null)

    // Dedicated state for ID Card front/back mode
    val idCardFrontPage = mutableStateOf<CapturedPage?>(null)
    val idCardBackPage = mutableStateOf<CapturedPage?>(null)

    fun startNewSession(mode: ScanMode = ScanMode.BATCH_DOCUMENTS) {
        capturedPages.clear()
        scanMode.value = mode
        idCardFrontPage.value = null
        idCardBackPage.value = null
        isProcessingCapture.value = false
        statusMessage.value = null
    }

    suspend fun addPageFromBitmap(
        context: Context,
        rawBitmap: Bitmap,
        filter: ScanFilter = ScanFilter.MAGIC_COLOR
    ): CapturedPage = withContext(Dispatchers.IO) {
        isProcessingCapture.value = true
        statusMessage.value = "Enhancing document..."

        val pageId = UUID.randomUUID().toString()
        val tempDir = File(context.cacheDir, "scan_session").apply { mkdirs() }
        val originalFile = File(tempDir, "orig_${pageId}.jpg")
        val processedFile = File(tempDir, "proc_${pageId}.jpg")

        // 1. Save original capture
        ImageProcessor.saveBitmapToFile(rawBitmap, originalFile, quality = 95)

        // 2. Detect corners and apply perspective & filter
        val corners = ImageProcessor.detectDocumentCorners(rawBitmap)
        val correctedBitmap = ImageProcessor.applyPerspectiveCorrection(rawBitmap, corners)
        val filteredBitmap = ImageProcessor.applyFilter(correctedBitmap, filter)

        // 3. Save processed image
        ImageProcessor.saveBitmapToFile(filteredBitmap, processedFile, quality = 90)

        // 4. Run on-device OCR in background
        statusMessage.value = "Recognizing text..."
        val recognizedText = OcrEngine.recognizeText(filteredBitmap)

        val newPage = CapturedPage(
            id = pageId,
            localImagePath = processedFile.absolutePath,
            originalImagePath = originalFile.absolutePath,
            pageIndex = capturedPages.size,
            filter = filter,
            cropPoints = corners,
            ocrText = recognizedText,
            isProcessed = true
        )

        withContext(Dispatchers.Main) {
            if (scanMode.value == ScanMode.ID_CARD) {
                if (idCardFrontPage.value == null) {
                    idCardFrontPage.value = newPage
                    capturedPages.add(newPage)
                } else if (idCardBackPage.value == null) {
                    idCardBackPage.value = newPage
                    capturedPages.add(newPage)
                }
            } else {
                capturedPages.add(newPage)
            }
            isProcessingCapture.value = false
            statusMessage.value = null
        }

        newPage
    }

    suspend fun addPageFromUri(
        context: Context,
        uri: Uri,
        filter: ScanFilter = ScanFilter.MAGIC_COLOR
    ): CapturedPage? = withContext(Dispatchers.IO) {
        try {
            isProcessingCapture.value = true
            statusMessage.value = "Importing image..."
            var inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                addPageFromBitmap(context, bitmap, filter)
            } else {
                withContext(Dispatchers.Main) {
                    isProcessingCapture.value = false
                    statusMessage.value = null
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                isProcessingCapture.value = false
                statusMessage.value = null
            }
            null
        }
    }

    fun removeLastPage() {
        if (capturedPages.isNotEmpty()) {
            val removed = capturedPages.removeAt(capturedPages.size - 1)
            File(removed.localImagePath).delete()
            File(removed.originalImagePath).delete()
            reindexPages()
        }
    }

    fun deletePage(pageId: String) {
        val index = capturedPages.indexOfFirst { it.id == pageId }
        if (index != -1) {
            val removed = capturedPages.removeAt(index)
            File(removed.localImagePath).delete()
            File(removed.originalImagePath).delete()
            reindexPages()
        }
    }

    fun duplicatePage(context: Context, pageId: String) {
        val source = capturedPages.find { it.id == pageId } ?: return
        val newId = UUID.randomUUID().toString()
        val tempDir = File(context.cacheDir, "scan_session")
        val newProcessed = File(tempDir, "proc_${newId}.jpg")
        val newOrig = File(tempDir, "orig_${newId}.jpg")

        File(source.localImagePath).copyTo(newProcessed, overwrite = true)
        File(source.originalImagePath).copyTo(newOrig, overwrite = true)

        val newPage = source.copy(
            id = newId,
            localImagePath = newProcessed.absolutePath,
            originalImagePath = newOrig.absolutePath,
            pageIndex = capturedPages.size
        )
        capturedPages.add(newPage)
        reindexPages()
    }

    fun rotatePage(pageId: String, clockwise: Boolean = true) {
        val index = capturedPages.indexOfFirst { it.id == pageId }
        if (index != -1) {
            val page = capturedPages[index]
            val delta = if (clockwise) 90 else -90
            var newDeg = (page.rotationDegrees + delta) % 360
            if (newDeg < 0) newDeg += 360
            capturedPages[index] = page.copy(rotationDegrees = newDeg)
        }
    }

    fun movePageUp(index: Int) {
        if (index > 0 && index < capturedPages.size) {
            val item = capturedPages.removeAt(index)
            capturedPages.add(index - 1, item)
            reindexPages()
        }
    }

    fun movePageDown(index: Int) {
        if (index >= 0 && index < capturedPages.size - 1) {
            val item = capturedPages.removeAt(index)
            capturedPages.add(index + 1, item)
            reindexPages()
        }
    }

    fun updatePageFilter(context: Context, scope: CoroutineScope, pageId: String, newFilter: ScanFilter) {
        val index = capturedPages.indexOfFirst { it.id == pageId }
        if (index != -1) {
            val page = capturedPages[index]
            capturedPages[index] = page.copy(filter = newFilter)

            // Re-apply filter asynchronously
            scope.launch(Dispatchers.IO) {
                val origFile = File(page.originalImagePath)
                val rawBitmap = ImageProcessor.decodeSampledBitmap(origFile) ?: return@launch
                val corrected = ImageProcessor.applyPerspectiveCorrection(rawBitmap, page.cropPoints)
                val filtered = ImageProcessor.applyFilter(corrected, newFilter)
                ImageProcessor.saveBitmapToFile(filtered, File(page.localImagePath))
            }
        }
    }

    private fun reindexPages() {
        for (i in capturedPages.indices) {
            capturedPages[i] = capturedPages[i].copy(pageIndex = i)
        }
    }
}
