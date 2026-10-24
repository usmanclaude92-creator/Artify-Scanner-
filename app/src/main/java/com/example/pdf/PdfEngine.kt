package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import com.example.data.model.CapturedPage
import com.example.data.model.PdfExportConfig
import com.example.data.model.PdfPageSize
import com.example.data.model.PdfQuality
import com.example.imageprocessing.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object PdfEngine {

    /**
     * Combines multiple captured/edited pages into ONE valid multi-page PDF document.
     * Processes pages sequentially and recycles bitmaps to avoid memory spikes.
     */
    suspend fun createMultiPagePdf(
        context: Context,
        pages: List<CapturedPage>,
        config: PdfExportConfig,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext false

        val pdfDocument = PdfDocument()
        val tempFile = File(context.cacheDir, "export_temp_${System.currentTimeMillis()}.pdf")

        try {
            val totalPages = pages.size

            for ((index, page) in pages.withIndex()) {
                onProgress(index + 1, totalPages)

                // 1. Decode page image
                val imageFile = File(page.localImagePath)
                val (reqW, reqH) = when (config.quality) {
                    PdfQuality.HIGH -> Pair(1600, 2200)
                    PdfQuality.MEDIUM -> Pair(1240, 1754)
                    PdfQuality.COMPRESSED -> Pair(900, 1250)
                }
                var rawBitmap = ImageProcessor.decodeSampledBitmap(
                    imageFile,
                    reqWidth = reqW,
                    reqHeight = reqH
                ) ?: continue
                rawBitmap = reencodeJpeg(rawBitmap, config.quality.jpegQuality)

                // 2. Apply rotation if specified
                if (page.rotationDegrees != 0) {
                    val rotated = ImageProcessor.rotateBitmap(rawBitmap, page.rotationDegrees)
                    if (rotated != rawBitmap) {
                        rawBitmap.recycle()
                        rawBitmap = rotated
                    }
                }

                // 3. Determine PDF Page Dimensions
                val (pageWidthPt, pageHeightPt) = when (config.pageSize) {
                    PdfPageSize.A4 -> Pair(PdfPageSize.A4.widthPt, PdfPageSize.A4.heightPt)
                    PdfPageSize.LETTER -> Pair(PdfPageSize.LETTER.widthPt, PdfPageSize.LETTER.heightPt)
                    PdfPageSize.ORIGINAL -> {
                        // Scale proportionally to reasonable PDF pt
                        val scale = min(595f / rawBitmap.width, 842f / rawBitmap.height)
                        val w = (rawBitmap.width * scale).toInt().coerceAtLeast(300)
                        val h = (rawBitmap.height * scale).toInt().coerceAtLeast(400)
                        Pair(w, h)
                    }
                }

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = pdfPage.canvas

                // Clear background to white
                canvas.drawColor(Color.WHITE)

                // Margin calculation
                val marginPt = if (config.pageSize == PdfPageSize.ORIGINAL) 0f else 18f
                val availableWidth = pageWidthPt - (marginPt * 2)
                val availableHeight = pageHeightPt - (marginPt * 2) - if (config.includePageNumbers) 24f else 0f

                // Scale image to fit inside available bounds while preserving aspect ratio
                val imgWidth = rawBitmap.width.toFloat()
                val imgHeight = rawBitmap.height.toFloat()
                val imgAspect = imgWidth / imgHeight
                val targetAspect = availableWidth / availableHeight

                val drawWidth: Float
                val drawHeight: Float
                if (imgAspect > targetAspect) {
                    drawWidth = availableWidth
                    drawHeight = availableWidth / imgAspect
                } else {
                    drawHeight = availableHeight
                    drawWidth = availableHeight * imgAspect
                }

                val left = marginPt + (availableWidth - drawWidth) / 2f
                val top = marginPt + (availableHeight - drawHeight) / 2f

                val destRect = Rect(
                    left.toInt(),
                    top.toInt(),
                    (left + drawWidth).toInt(),
                    (top + drawHeight).toInt()
                )

                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(rawBitmap, null, destRect, paint)

                // Searchable OCR Text Layer (invisible text mapped over document)
                if (page.ocrText.isNotBlank()) {
                    val ocrPaint = Paint().apply {
                        color = Color.TRANSPARENT
                        textSize = 8f
                    }
                    val sampleLines = page.ocrText.lines().take(40)
                    var lineY = top + 15f
                    for (line in sampleLines) {
                        if (line.isNotBlank() && lineY < top + drawHeight - 10f) {
                            canvas.drawText(line.take(80), left + 10f, lineY, ocrPaint)
                            lineY += 12f
                        }
                    }
                }

                // Optional Watermark
                if (config.watermarkText.isNotBlank()) {
                    val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(45, 120, 120, 120)
                        textSize = 34f
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.save()
                    canvas.rotate(-35f, pageWidthPt / 2f, pageHeightPt / 2f)
                    canvas.drawText(config.watermarkText, pageWidthPt / 2f, pageHeightPt / 2f, watermarkPaint)
                    canvas.restore()
                }

                // Optional Page Numbering
                if (config.includePageNumbers) {
                    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.DKGRAY
                        textSize = 9f
                        textAlign = Paint.Align.RIGHT
                    }
                    val footerText = "Page ${index + 1} of $totalPages"
                    canvas.drawText(footerText, pageWidthPt - marginPt - 8f, pageHeightPt - 10f, footerPaint)

                    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.GRAY
                        textSize = 8f
                        textAlign = Paint.Align.LEFT
                    }
                    canvas.drawText("Artify Scanner", marginPt + 8f, pageHeightPt - 10f, brandPaint)
                }

                pdfDocument.finishPage(pdfPage)
                rawBitmap.recycle()
            }

            // Write to temp file first
            tempFile.parentFile?.mkdirs()
            FileOutputStream(tempFile).use { out ->
                pdfDocument.writeTo(out)
            }

            // Validate temp file exists and has size
            if (tempFile.exists() && tempFile.length() > 0) {
                PdfMetadata.apply(
                    tempFile,
                    title = config.documentTitle.ifBlank { config.filename },
                    author = config.author,
                    creator = "Artify Scanner"
                )
                outputFile.parentFile?.mkdirs()
                if (outputFile.exists()) outputFile.delete()
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tempFile.delete()
            false
        } finally {
            pdfDocument.close()
        }
    }

    /** Round-trips the bitmap through JPEG so the requested quality level shrinks the output. */
    private fun reencodeJpeg(source: Bitmap, quality: Int): Bitmap {
        if (quality >= 100) return source
        return try {
            val out = ByteArrayOutputStream()
            source.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return source
            source.recycle()
            decoded
        } catch (e: OutOfMemoryError) {
            source
        }
    }

    /**
     * Combines multiple images into a single PDF (Images to PDF tool).
     */
    suspend fun createPdfFromImages(
        context: Context,
        imageFiles: List<File>,
        outputFile: File,
        config: PdfExportConfig
    ): Boolean = withContext(Dispatchers.IO) {
        val capturedPages = imageFiles.mapIndexed { index, file ->
            CapturedPage(
                id = "img_${System.currentTimeMillis()}_$index",
                localImagePath = file.absolutePath,
                originalImagePath = file.absolutePath,
                pageIndex = index
            )
        }
        createMultiPagePdf(context, capturedPages, config, outputFile)
    }
}
