package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import com.example.data.model.CapturedPage
import com.example.data.model.OcrLine
import com.example.data.model.PdfExportConfig
import com.example.data.model.PdfPageSize
import com.example.data.model.PdfQuality
import com.example.imageprocessing.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object PdfEngine {

    /**
     * Combines multiple captured/edited pages into ONE multi-page PDF with a searchable text layer.
     * Pages are decoded, written and recycled one at a time to keep memory flat.
     */
    suspend fun createMultiPagePdf(
        context: Context,
        pages: List<CapturedPage>,
        config: PdfExportConfig,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext false

        val tempFile = File(context.cacheDir, "export_temp_${System.currentTimeMillis()}.pdf")
        val totalPages = pages.count { File(it.localImagePath).exists() }
        if (totalPages == 0) return@withContext false

        try {
            val writtenPages = PdfWriter(
                BufferedOutputStream(FileOutputStream(tempFile)),
                PdfInfo(
                    title = config.documentTitle.ifBlank { config.filename },
                    author = config.author,
                    creator = "Artify Scanner"
                )
            ).use { writer ->
            for ((index, page) in pages.withIndex()) {
                onProgress(index + 1, pages.size)

                val (reqW, reqH) = when (config.quality) {
                    PdfQuality.HIGH -> Pair(1600, 2200)
                    PdfQuality.MEDIUM -> Pair(1240, 1754)
                    PdfQuality.COMPRESSED -> Pair(900, 1250)
                }
                var bitmap = ImageProcessor.decodeSampledBitmap(File(page.localImagePath), reqW, reqH) ?: continue

                if (page.rotationDegrees != 0) {
                    val rotated = ImageProcessor.rotateBitmap(bitmap, page.rotationDegrees)
                    if (rotated != bitmap) {
                        bitmap.recycle()
                        bitmap = rotated
                    }
                }

                try {
                    writer.addPage(buildPageSpec(context, bitmap, page, config, writer.pageCount + 1, totalPages))
                } finally {
                    bitmap.recycle()
                }
            }

            if (writer.pageCount > 0) writer.finish()
            writer.pageCount
            }

            if (writtenPages == 0) {
                tempFile.delete()
                return@withContext false
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                outputFile.parentFile?.mkdirs()
                if (outputFile.exists()) outputFile.delete()
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("Artify", "PDF export failed", e)
            tempFile.delete()
            false
        }
    }

    private fun buildPageSpec(
        context: Context,
        bitmap: Bitmap,
        page: CapturedPage,
        config: PdfExportConfig,
        pageNumber: Int,
        totalPages: Int
    ): PdfPageSpec {
        // The scan fills the page edge to edge; only a thin branding strip is added underneath.
        // The sheet is as wide as A4/Letter and as tall as the scan plus the strip needs.
        val pageWidth = if (config.pageSize == PdfPageSize.LETTER) PdfPageSize.LETTER.widthPt.toFloat() else PdfPageSize.A4.widthPt.toFloat()
        val combined = PdfFooter.compose(context, bitmap, pageNumber, totalPages, config.includePageNumbers)
        val pageHeight = pageWidth * combined.height / combined.width
        val scanHeightPt = pageHeight * bitmap.height / combined.height

        val jpeg = ByteArrayOutputStream().also {
            combined.compress(Bitmap.CompressFormat.JPEG, config.quality.jpegQuality, it)
        }.toByteArray()
        combined.recycle()

        return PdfPageSpec(
            widthPt = pageWidth,
            heightPt = pageHeight,
            jpeg = jpeg,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height + PdfFooter.stripHeightPx(bitmap.width),
            drawLeft = 0f,
            drawBottom = 0f,
            drawWidth = pageWidth,
            drawHeight = pageHeight,
            hiddenText = textBoxes(page, 0f, pageHeight - scanHeightPt, pageWidth, scanHeightPt),
            watermark = config.watermarkText
        )
    }

    /** Maps OCR line boxes (normalized to the unrotated image) onto the drawn page image. */
    private fun textBoxes(
        page: CapturedPage,
        left: Float,
        bottom: Float,
        drawWidth: Float,
        drawHeight: Float
    ): List<PdfTextBox> {
        val lines = page.ocrLines.ifEmpty { evenlySpacedLines(page.ocrText) }
        return lines.mapNotNull { line ->
            if (line.text.isBlank()) return@mapNotNull null
            val r = PdfWriter.rotateNormalized(line.left, line.top, line.right, line.bottom, page.rotationDegrees)
            val boxHeight = (r[3] - r[1]) * drawHeight
            val boxWidth = (r[2] - r[0]) * drawWidth
            if (boxHeight <= 0f || boxWidth <= 0f) return@mapNotNull null
            PdfTextBox(
                text = line.text,
                x = left + r[0] * drawWidth,
                baselineY = bottom + (1f - r[3]) * drawHeight + boxHeight * 0.2f,
                width = boxWidth,
                fontSize = boxHeight * 0.85f
            )
        }
    }

    /** Fallback for pages without stored line boxes: still searchable, only roughly positioned. */
    private fun evenlySpacedLines(text: String): List<OcrLine> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val step = 0.9f / lines.size
        return lines.mapIndexed { i, t ->
            val top = 0.05f + i * step
            OcrLine(t, 0.05f, top, 0.95f, top + step * 0.8f)
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
