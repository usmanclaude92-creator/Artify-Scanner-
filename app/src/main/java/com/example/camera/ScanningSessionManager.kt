package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import com.example.data.model.CapturedPage
import com.example.data.model.NormalizedCornerPoints
import com.example.data.model.ScanFilter
import com.example.data.model.ScanMode
import com.example.imageprocessing.ImageProcessor
import com.example.ocr.OcrEngine
import com.example.data.local.OcrLinesJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ScanningSessionManager {

    val capturedPages = mutableStateListOf<CapturedPage>()
    val scanMode = mutableStateOf(ScanMode.BATCH_DOCUMENTS)
    val isAutoCapture = mutableStateOf(false)

    /** Open the corner editor after every shutter press (detection only pre-places the corners). */
    val confirmCropAfterCapture = mutableStateOf(true)
    val isFlashOn = mutableStateOf(false)
    val isProcessingCapture = mutableStateOf(false)
    val statusMessage = mutableStateOf<String?>(null)

    // Dedicated state for ID Card front/back mode
    val idCardFrontPage = mutableStateOf<CapturedPage?>(null)
    val idCardBackPage = mutableStateOf<CapturedPage?>(null)

    private var sessionFile: File? = null
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistJob: Job? = null

    /**
     * Mirrors the in-progress session to disk so an Android process kill (low memory, camera
     * app switching) does not lose captured pages. Call once from the Activity.
     */
    fun init(context: Context, restore: Boolean) {
        val file = File(context.filesDir, "scan_session.json")
        sessionFile = file
        reloadPrefs(context)
        if (restore && capturedPages.isEmpty()) restoreFrom(file)
        if (persistJob == null) {
            persistJob = persistScope.launch {
                snapshotFlow { capturedPages.toList() to scanMode.value }.collect { (pages, mode) ->
                    writeSession(file, pages, mode)
                }
            }
        }
    }

    /** Re-reads the current account's scan preferences (after sign-in, sign-out or account switch). */
    fun reloadPrefs(context: Context) {
        confirmCropAfterCapture.value = com.example.auth.AccountScope.prefs(context, "artify_scan").getBoolean("confirm_crop", true)
    }

    fun setConfirmCrop(context: Context, enabled: Boolean) {
        confirmCropAfterCapture.value = enabled
        com.example.auth.AccountScope.prefs(context, "artify_scan").edit().putBoolean("confirm_crop", enabled).apply()
    }

    private fun writeSession(file: File, pages: List<CapturedPage>, mode: ScanMode) {
        try {
            if (pages.isEmpty()) {
                file.delete()
                return
            }
            val arr = JSONArray()
            for (p in pages) {
                val c = p.cropPoints
                arr.put(
                    JSONObject()
                        .put("id", p.id)
                        .put("img", p.localImagePath)
                        .put("orig", p.originalImagePath)
                        .put("rot", p.rotationDegrees)
                        .put("filter", p.filter.name)
                        .put("ocr", p.ocrText)
                        .put("lines", OcrLinesJson.encode(p.ocrLines))
                        .put(
                            "crop",
                            JSONArray(
                                listOf(
                                    c.topLeft.x, c.topLeft.y, c.topRight.x, c.topRight.y,
                                    c.bottomRight.x, c.bottomRight.y, c.bottomLeft.x, c.bottomLeft.y
                                ).map { it.toDouble() }
                            )
                        )
                )
            }
            file.writeText(JSONObject().put("mode", mode.name).put("pages", arr).toString())
        } catch (e: Exception) {
            android.util.Log.e("ScanningSession", "Could not persist session", e)
        }
    }

    private fun restoreFrom(file: File) {
        try {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            val arr = root.getJSONArray("pages")
            val restored = (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val img = o.getString("img")
                if (!File(img).exists()) return@mapNotNull null // cache was evicted
                val c = o.getJSONArray("crop")
                fun f(k: Int) = c.getDouble(k).toFloat()
                CapturedPage(
                    id = o.getString("id"),
                    localImagePath = img,
                    originalImagePath = o.getString("orig"),
                    pageIndex = i,
                    rotationDegrees = o.getInt("rot"),
                    filter = try { ScanFilter.valueOf(o.getString("filter")) } catch (e: Exception) { ScanFilter.MAGIC_COLOR },
                    cropPoints = NormalizedCornerPoints(
                        PointF(f(0), f(1)), PointF(f(2), f(3)), PointF(f(4), f(5)), PointF(f(6), f(7))
                    ),
                    ocrText = o.getString("ocr"),
                    ocrLines = OcrLinesJson.decode(o.optString("lines", "")),
                    isProcessed = true
                )
            }
            capturedPages.clear()
            capturedPages.addAll(restored.mapIndexed { i, p -> p.copy(pageIndex = i) })
            scanMode.value = try { ScanMode.valueOf(root.getString("mode")) } catch (e: Exception) { ScanMode.BATCH_DOCUMENTS }
        } catch (e: Exception) {
            android.util.Log.e("ScanningSession", "Could not restore session", e)
        }
    }

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
        filter: ScanFilter = ScanFilter.MAGIC_COLOR,
        autoCrop: Boolean = false,
        corners: NormalizedCornerPoints? = null
    ): CapturedPage = withContext(Dispatchers.IO) {
        isProcessingCapture.value = true
        statusMessage.value = "Enhancing document..."

        val pageId = UUID.randomUUID().toString()
        val tempDir = File(context.cacheDir, "scan_session").apply { mkdirs() }
        val originalFile = File(tempDir, "orig_${pageId}.jpg")
        val processedFile = File(tempDir, "proc_${pageId}.jpg")

        var correctedBitmap: Bitmap? = null
        var filteredBitmap: Bitmap? = null
        try {
            // 1. Save original capture
            ImageProcessor.saveBitmapToFile(rawBitmap, originalFile, quality = 95)

            // 2. Detect corners and apply perspective & filter
            val pageCorners = corners ?: NormalizedCornerPoints(
                PointF(0f, 0f), PointF(1f, 0f), PointF(1f, 1f), PointF(0f, 1f)
            )
            correctedBitmap = ImageProcessor.applyPerspectiveCorrection(rawBitmap, pageCorners)
            filteredBitmap = ImageProcessor.applyFilter(correctedBitmap, filter)

            // 3. Save processed image
            ImageProcessor.saveBitmapToFile(filteredBitmap, processedFile, quality = 90)

            // 4. Run on-device OCR in background
            statusMessage.value = "Recognizing text..."
            val ocr = OcrEngine.recognize(filteredBitmap)

            val newPage = CapturedPage(
                id = pageId,
                localImagePath = processedFile.absolutePath,
                originalImagePath = originalFile.absolutePath,
                pageIndex = capturedPages.size,
                filter = filter,
                cropPoints = pageCorners,
                ocrText = ocr.text,
                ocrLines = ocr.lines,
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
            }
            newPage
        } finally {
            // Free native bitmap memory; the page lives on disk from here on
            if (filteredBitmap != null && filteredBitmap !== correctedBitmap) filteredBitmap.recycle()
            if (correctedBitmap != null && correctedBitmap !== rawBitmap) correctedBitmap.recycle()
            if (!rawBitmap.isRecycled) rawBitmap.recycle()
            withContext(NonCancellable + Dispatchers.Main) {
                isProcessingCapture.value = false
                statusMessage.value = null
            }
        }
    }

    suspend fun addPageFromUri(
        context: Context,
        uri: Uri,
        filter: ScanFilter = ScanFilter.MAGIC_COLOR,
        autoCrop: Boolean = false
    ): CapturedPage? = withContext(Dispatchers.IO) {
        // Copy to a temp file first so large photos can be decoded down-sampled
        val temp = File(context.cacheDir, "import_${UUID.randomUUID()}.tmp")
        try {
            isProcessingCapture.value = true
            statusMessage.value = "Importing image..."
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { input.copyTo(it) }
            } ?: return@withContext null
            val bitmap = ImageProcessor.decodeSampledBitmap(temp, reqWidth = 3000, reqHeight = 4000)
                ?: return@withContext null
            addPageFromBitmap(context, bitmap, filter, autoCrop)
        } catch (e: Exception) {
            android.util.Log.e("ScanningSession", "Image import failed", e)
            null
        } finally {
            temp.delete()
            withContext(NonCancellable + Dispatchers.Main) {
                isProcessingCapture.value = false
                statusMessage.value = null
            }
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
        if (index == -1) return
        val page = capturedPages[index]
        capturedPages[index] = page.copy(filter = newFilter)

        // Re-render into a NEW file: the image loader caches by path and would keep showing the old look
        scope.launch(Dispatchers.IO) {
            isProcessingCapture.value = true
            statusMessage.value = "Applying ${newFilter.displayName}..."
            var raw: Bitmap? = null
            var corrected: Bitmap? = null
            var filtered: Bitmap? = null
            try {
                raw = ImageProcessor.decodeSampledBitmap(File(page.originalImagePath), 3000, 4000) ?: return@launch
                corrected = ImageProcessor.applyPerspectiveCorrection(raw, page.cropPoints)
                filtered = ImageProcessor.applyFilter(corrected, newFilter)
                val dir = File(context.cacheDir, "scan_session").apply { mkdirs() }
                val newFile = File(dir, "proc_${UUID.randomUUID()}.jpg")
                ImageProcessor.saveBitmapToFile(filtered, newFile, quality = 90)
                withContext(Dispatchers.Main) {
                    val idx = capturedPages.indexOfFirst { it.id == pageId }
                    if (idx != -1 && capturedPages[idx].filter == newFilter) {
                        val old = capturedPages[idx].localImagePath
                        capturedPages[idx] = capturedPages[idx].copy(localImagePath = newFile.absolutePath)
                        File(old).delete()
                    } else {
                        newFile.delete() // a newer filter choice superseded this one
                    }
                }
            } finally {
                if (filtered != null && filtered !== corrected) filtered.recycle()
                if (corrected != null && corrected !== raw) corrected.recycle()
                raw?.recycle()
                withContext(NonCancellable + Dispatchers.Main) {
                    isProcessingCapture.value = false
                    statusMessage.value = null
                }
            }
        }
    }

    /** Re-renders a page from its original capture using user-adjusted corners, then re-runs OCR. */
    fun updatePageCrop(context: Context, scope: CoroutineScope, pageId: String, corners: NormalizedCornerPoints) {
        val page = capturedPages.find { it.id == pageId } ?: return
        scope.launch(Dispatchers.IO) {
            isProcessingCapture.value = true
            statusMessage.value = "Applying crop..."
            var raw: Bitmap? = null
            var corrected: Bitmap? = null
            var filtered: Bitmap? = null
            try {
                raw = ImageProcessor.decodeSampledBitmap(File(page.originalImagePath), 3000, 4000) ?: return@launch
                corrected = ImageProcessor.applyPerspectiveCorrection(raw, corners)
                filtered = ImageProcessor.applyFilter(corrected, page.filter)
                // New file name so image caches don't keep showing the old crop
                val newFile = File(File(context.cacheDir, "scan_session").apply { mkdirs() }, "proc_${UUID.randomUUID()}.jpg")
                ImageProcessor.saveBitmapToFile(filtered, newFile, quality = 90)
                val ocr = OcrEngine.recognize(filtered)
                withContext(Dispatchers.Main) {
                    val idx = capturedPages.indexOfFirst { it.id == pageId }
                    if (idx != -1) {
                        val old = capturedPages[idx].localImagePath
                        capturedPages[idx] = capturedPages[idx].copy(
                            localImagePath = newFile.absolutePath,
                            cropPoints = corners,
                            ocrText = ocr.text,
                            ocrLines = ocr.lines
                        )
                        File(old).delete()
                    }
                }
            } finally {
                if (filtered != null && filtered !== corrected) filtered.recycle()
                if (corrected != null && corrected !== raw) corrected.recycle()
                raw?.recycle()
                withContext(NonCancellable + Dispatchers.Main) {
                    isProcessingCapture.value = false
                    statusMessage.value = null
                }
            }
        }
    }

    private fun reindexPages() {
        for (i in capturedPages.indices) {
            capturedPages[i] = capturedPages[i].copy(pageIndex = i)
        }
    }
}
