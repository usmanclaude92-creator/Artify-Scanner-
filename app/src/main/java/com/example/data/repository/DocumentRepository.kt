package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.example.data.local.AppDatabase
import com.example.data.local.DocumentEntity
import com.example.data.local.PageEntity
import com.example.data.model.CapturedPage
import com.example.data.model.DocumentCategory
import com.example.data.model.PdfExportConfig
import com.example.data.model.ScanFilter
import com.example.data.model.ScannedDocumentItem
import com.example.imageprocessing.ImageProcessor
import com.example.pdf.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DocumentRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.documentDao()

    val documentsDir: File
        get() = File(context.filesDir, "documents").apply { mkdirs() }

    val pdfsDir: File
        get() = File(context.filesDir, "pdfs").apply { mkdirs() }

    fun getAllDocuments(): Flow<List<ScannedDocumentItem>> {
        return dao.getAllDocuments().map { list -> list.map { it.toItem() } }
    }

    fun getFavoriteDocuments(): Flow<List<ScannedDocumentItem>> {
        return dao.getFavoriteDocuments().map { list -> list.map { it.toItem() } }
    }

    fun getDocumentsByCategory(category: DocumentCategory): Flow<List<ScannedDocumentItem>> {
        return if (category == DocumentCategory.ALL) {
            getAllDocuments()
        } else {
            dao.getDocumentsByCategory(category.name).map { list -> list.map { it.toItem() } }
        }
    }

    fun searchDocuments(query: String): Flow<List<ScannedDocumentItem>> {
        return dao.searchDocuments(query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")).map { list -> list.map { it.toItem() } }
    }

    suspend fun getOcrText(documentId: String): String {
        return dao.getDocumentById(documentId)?.ocrFullText ?: ""
    }

    suspend fun getDocumentById(id: String): ScannedDocumentItem? {
        return dao.getDocumentById(id)?.toItem()
    }

    suspend fun getPagesForDocument(documentId: String): List<CapturedPage> {
        return dao.getPagesForDocumentSync(documentId).map { entity ->
            CapturedPage(
                id = entity.id,
                localImagePath = entity.imagePath,
                originalImagePath = entity.originalImagePath,
                pageIndex = entity.pageIndex,
                rotationDegrees = entity.rotationDegrees,
                filter = try { ScanFilter.valueOf(entity.filterName) } catch (e: Exception) { ScanFilter.MAGIC_COLOR },
                ocrText = entity.ocrText,
                ocrLines = OcrLinesJson.decode(entity.ocrLinesJson)
            )
        }
    }

    suspend fun toggleFavorite(documentId: String, currentStatus: Boolean) {
        dao.setFavorite(documentId, !currentStatus)
    }

    suspend fun updateDocumentTitle(documentId: String, newTitle: String) {
        dao.updateTitle(documentId, newTitle, System.currentTimeMillis())
    }

    suspend fun deleteDocument(documentId: String) = withContext(Dispatchers.IO) {
        val doc = dao.getDocumentById(documentId)
        val pages = dao.getPagesForDocumentSync(documentId)

        dao.deletePagesForDocument(documentId)
        dao.deleteDocument(documentId)

        // Delete associated files
        doc?.let {
            val pdf = File(it.pdfFilePath)
            if (pdf.exists()) pdf.delete()
        }
        File(documentsDir, documentId).deleteRecursively()
        for (page in pages) {
            val img = File(page.imagePath)
            if (img.exists()) img.delete()
            val orig = File(page.originalImagePath)
            if (orig.exists()) orig.delete()
        }
    }

    /**
     * Saves a complete scanning session as a persisted Document with multi-page PDF.
     * Page images are copied into permanent storage. Throws [IOException] if saving fails.
     */
    suspend fun saveScanSession(
        title: String,
        category: DocumentCategory,
        pages: List<CapturedPage>,
        pdfConfig: PdfExportConfig
    ): ScannedDocumentItem = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // 1. Generate multi-page PDF file
        val cleanTitle = title.ifBlank {
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            "Scan_${dateFormat.format(Date())}"
        }.replace(Regex("[^a-zA-Z0-9_.-]"), "_")

        // Move page images out of the (evictable) cache into permanent app storage
        val docDir = File(documentsDir, docId).apply { mkdirs() }
        val persistedPages = try {
            pages.mapIndexed { index, page ->
                val pageKey = page.id.ifBlank { UUID.randomUUID().toString() }
                val processed = File(docDir, "p${index}_$pageKey.jpg")
                val original = File(docDir, "o${index}_$pageKey.jpg")
                File(page.localImagePath).copyTo(processed, overwrite = true)
                val origSrc = File(page.originalImagePath)
                if (origSrc.exists()) origSrc.copyTo(original, overwrite = true) else processed.copyTo(original, overwrite = true)
                page.copy(localImagePath = processed.absolutePath, originalImagePath = original.absolutePath)
            }
        } catch (e: Exception) {
            docDir.deleteRecursively()
            throw IOException("Could not save page images: ${e.message}", e)
        }

        val pdfFile = File(pdfsDir, "${cleanTitle}_$docId.pdf")
        val pdfOk = PdfEngine.createMultiPagePdf(
            context = context,
            pages = persistedPages,
            config = pdfConfig.copy(filename = cleanTitle),
            outputFile = pdfFile
        )
        if (!pdfOk || !pdfFile.exists() || pdfFile.length() == 0L) {
            pdfFile.delete()
            docDir.deleteRecursively()
            throw IOException("PDF generation failed")
        }

        // 2. Persist page records
        val pageEntities = persistedPages.mapIndexed { index, page ->
            PageEntity(
                id = page.id.ifBlank { UUID.randomUUID().toString() },
                documentId = docId,
                pageIndex = index,
                imagePath = page.localImagePath,
                originalImagePath = page.originalImagePath,
                rotationDegrees = page.rotationDegrees,
                filterName = page.filter.name,
                ocrText = page.ocrText,
                ocrLinesJson = OcrLinesJson.encode(page.ocrLines)
            )
        }

        // Combine OCR text across all pages for full-text search
        val fullOcrText = persistedPages.joinToString("\n\n") { it.ocrText }

        val thumbnailPath = persistedPages.firstOrNull()?.localImagePath ?: ""
        val fileSizeBytes = if (pdfFile.exists()) pdfFile.length() else 0L

        val docEntity = DocumentEntity(
            id = docId,
            title = title.ifBlank { "Scanned Document" },
            createdAt = timestamp,
            updatedAt = timestamp,
            pageCount = pages.size,
            category = category.name,
            isFavorite = false,
            pdfFilePath = pdfFile.absolutePath,
            thumbnailPath = thumbnailPath,
            fileSizeBytes = fileSizeBytes,
            ocrFullText = fullOcrText,
            tags = "Scanned,${category.displayName}"
        )
        dao.insertDocument(docEntity)
        dao.insertPages(pageEntities)

        docEntity.toItem()
    }

    /**
     * One-time repair for documents saved by earlier versions, whose page images still live in the
     * evictable cache: copies any surviving files into permanent storage and rewrites the paths.
     */
    suspend fun migrateCachedPageImages() = withContext(Dispatchers.IO) {
        val cachePrefix = context.cacheDir.absolutePath
        val docs = dao.getAllDocuments().firstOrNull().orEmpty()
        for (doc in docs) {
            val pages = dao.getPagesForDocumentSync(doc.id)
            if (pages.none { it.imagePath.startsWith(cachePrefix) || it.originalImagePath.startsWith(cachePrefix) }) continue
            val docDir = File(documentsDir, doc.id).apply { mkdirs() }
            fun move(path: String): String {
                if (!path.startsWith(cachePrefix)) return path
                val src = File(path)
                if (!src.exists()) return path
                return try {
                    val dest = File(docDir, src.name)
                    src.copyTo(dest, overwrite = true)
                    dest.absolutePath
                } catch (e: IOException) {
                    path
                }
            }
            val moved = pages.map { it.copy(imagePath = move(it.imagePath), originalImagePath = move(it.originalImagePath)) }
            dao.insertPages(moved)
            val newThumb = moved.firstOrNull()?.imagePath ?: doc.thumbnailPath
            if (newThumb != doc.thumbnailPath) dao.updateDocument(doc.copy(thumbnailPath = newThumb))
        }
    }

    /** Like [saveScanSession] but returns null instead of throwing when saving fails. */
    suspend fun saveScanSessionOrNull(
        title: String,
        category: DocumentCategory,
        pages: List<CapturedPage>,
        pdfConfig: PdfExportConfig
    ): ScannedDocumentItem? = try {
        saveScanSession(title, category, pages, pdfConfig)
    } catch (e: IOException) {
        null
    }

    /**
     * Seeds realistic onboarding documents if the database is currently empty.
     */
    suspend fun seedSampleDocumentsIfEmpty() = withContext(Dispatchers.IO) {
        val existing = dao.getAllDocuments().firstOrNull()
        if (!existing.isNullOrEmpty()) return@withContext

        // Create sample Welcome Document
        createSampleGuideDocument()
        createSampleInvoiceDocument()
    }

    private suspend fun createSampleGuideDocument() {
        val docId = "sample_welcome_guide"
        val timestamp = System.currentTimeMillis() - (3600 * 1000 * 5)
        val guideFile = File(documentsDir, "sample_guide_p1.jpg")

        // Draw a realistic high quality sample document page bitmap
        val bmp = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Header banner
        paint.color = Color.parseColor("#0F172A")
        canvas.drawRect(Rect(0, 0, 1200, 220), paint)

        paint.color = Color.WHITE
        paint.textSize = 52f
        paint.isFakeBoldText = true
        canvas.drawText("ARTIFY SCANNER", 70f, 110f, paint)

        paint.color = Color.parseColor("#38BDF8")
        paint.textSize = 30f
        paint.isFakeBoldText = false
        canvas.drawText("Quick Start Guide & Feature Overview", 70f, 165f, paint)

        // Body text
        paint.color = Color.parseColor("#1E293B")
        paint.textSize = 36f
        paint.isFakeBoldText = true
        canvas.drawText("1. Continuous Multi-Page Scanning", 70f, 320f, paint)

        paint.color = Color.parseColor("#475569")
        paint.textSize = 28f
        paint.isFakeBoldText = false
        val bodyLines = listOf(
            "Artify Scanner allows scanning an unlimited sequence of pages",
            "into a single session. Point camera at document and hit shutter.",
            "You can review, rotate, recrop, apply filters, and reorder pages",
            "before combining everything into a single, crystal-clear PDF.",
            "",
            "2. Advanced Image Processing & Filters:",
            "• Magic Color: Boosts clarity, shadow depth, and vibrant text contrast",
            "• B&W Clean: Sharp binarization ideal for signatures and invoices",
            "• Whitening: Removes dark background shadows from office desks",
            "",
            "3. On-Device OCR & Searchable PDFs:",
            "Every page can be text-recognized offline using Google ML Kit.",
            "Search across all your scanned contracts and invoices instantly.",
            "",
            "4. Built-in PDF Productivity Toolkit:",
            "Merge PDFs, extract pages, compress sizes, and add signatures."
        )
        var y = 380f
        for (line in bodyLines) {
            if (line.startsWith("2.") || line.startsWith("3.") || line.startsWith("4.")) {
                paint.color = Color.parseColor("#1E293B")
                paint.textSize = 34f
                paint.isFakeBoldText = true
            } else {
                paint.color = Color.parseColor("#475569")
                paint.textSize = 28f
                paint.isFakeBoldText = false
            }
            canvas.drawText(line, 70f, y, paint)
            y += 44f
        }

        ImageProcessor.saveBitmapToFile(bmp, guideFile)
        bmp.recycle()

        val sampleOcrText = bodyLines.joinToString("\n")
        val samplePdf = File(pdfsDir, "Artify_Scanner_Welcome_Guide.pdf")
        val page = CapturedPage(
            id = "guide_page_1",
            localImagePath = guideFile.absolutePath,
            originalImagePath = guideFile.absolutePath,
            pageIndex = 0,
            ocrText = sampleOcrText
        )

        PdfEngine.createMultiPagePdf(
            context = context,
            pages = listOf(page),
            config = PdfExportConfig(filename = "Artify_Scanner_Welcome_Guide", documentTitle = "Artify Scanner User Guide"),
            outputFile = samplePdf
        )

        dao.insertDocument(DocumentEntity(
            id = docId,
            title = "Artify Scanner Welcome Guide",
            createdAt = timestamp,
            updatedAt = timestamp,
            pageCount = 1,
            category = DocumentCategory.ALL.name,
            isFavorite = true,
            pdfFilePath = samplePdf.absolutePath,
            thumbnailPath = guideFile.absolutePath,
            fileSizeBytes = samplePdf.length(),
            ocrFullText = sampleOcrText,
            tags = "Guide,Help,Welcome"
        ))

        dao.insertPages(listOf(
            PageEntity(
                id = "guide_page_1",
                documentId = docId,
                pageIndex = 0,
                imagePath = guideFile.absolutePath,
                originalImagePath = guideFile.absolutePath,
                rotationDegrees = 0,
                filterName = ScanFilter.MAGIC_COLOR.name,
                ocrText = sampleOcrText
            )
        ))
    }

    private suspend fun createSampleInvoiceDocument() {
        val docId = "sample_business_invoice"
        val timestamp = System.currentTimeMillis() - (3600 * 1000 * 24)
        val invoiceFile = File(documentsDir, "sample_invoice_p1.jpg")

        val bmp = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 48f
        paint.isFakeBoldText = true
        canvas.drawText("ACME CLOUD SOLUTIONS LLC", 70f, 130f, paint)

        paint.color = Color.parseColor("#64748B")
        paint.textSize = 28f
        paint.isFakeBoldText = false
        canvas.drawText("Invoice #: INV-2026-8942", 70f, 180f, paint)
        canvas.drawText("Date: October 24, 2026", 70f, 220f, paint)
        canvas.drawText("Due Date: November 07, 2026", 70f, 260f, paint)

        // Divider
        paint.color = Color.parseColor("#E2E8F0")
        paint.strokeWidth = 3f
        canvas.drawLine(70f, 300f, 1130f, 300f, paint)

        // Items table
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 30f
        paint.isFakeBoldText = true
        canvas.drawText("Description", 70f, 360f, paint)
        canvas.drawText("Amount", 950f, 360f, paint)

        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#334155")
        canvas.drawText("Enterprise Cloud Infrastructure - Oct", 70f, 430f, paint)
        canvas.drawText("$1,250.00", 950f, 430f, paint)

        canvas.drawText("Dedicated Database Cluster & Backup", 70f, 500f, paint)
        canvas.drawText("$420.00", 950f, 500f, paint)

        canvas.drawText("AI Logic API Compute Units (Tier 2)", 70f, 570f, paint)
        canvas.drawText("$180.00", 950f, 570f, paint)

        canvas.drawLine(70f, 640f, 1130f, 640f, paint)

        paint.textSize = 32f
        canvas.drawText("Subtotal:", 750f, 700f, paint)
        canvas.drawText("$1,850.00", 950f, 700f, paint)

        canvas.drawText("Sales Tax (8%):", 750f, 750f, paint)
        canvas.drawText("$148.00", 950f, 750f, paint)

        paint.color = Color.parseColor("#0369A1")
        paint.textSize = 38f
        paint.isFakeBoldText = true
        canvas.drawText("Total Due:", 750f, 820f, paint)
        canvas.drawText("$1,998.00", 950f, 820f, paint)

        ImageProcessor.saveBitmapToFile(bmp, invoiceFile)
        bmp.recycle()

        val invoiceOcr = """
            ACME CLOUD SOLUTIONS LLC
            Invoice #: INV-2026-8942
            Date: October 24, 2026
            Due Date: November 07, 2026
            Description Amount
            Enterprise Cloud Infrastructure - Oct $1,250.00
            Dedicated Database Cluster & Backup $420.00
            AI Logic API Compute Units (Tier 2) $180.00
            Subtotal: $1,850.00
            Tax: $148.00
            Total: $1,998.00
        """.trimIndent()

        val samplePdf = File(pdfsDir, "Invoice_Acme_Cloud_2026.pdf")
        val page = CapturedPage(
            id = "invoice_page_1",
            localImagePath = invoiceFile.absolutePath,
            originalImagePath = invoiceFile.absolutePath,
            pageIndex = 0,
            ocrText = invoiceOcr
        )

        PdfEngine.createMultiPagePdf(
            context = context,
            pages = listOf(page),
            config = PdfExportConfig(filename = "Invoice_Acme_Cloud_2026", documentTitle = "Acme Cloud Invoice"),
            outputFile = samplePdf
        )

        dao.insertDocument(DocumentEntity(
            id = docId,
            title = "Invoice - Acme Cloud Solutions",
            createdAt = timestamp,
            updatedAt = timestamp,
            pageCount = 1,
            category = DocumentCategory.INVOICES.name,
            isFavorite = false,
            pdfFilePath = samplePdf.absolutePath,
            thumbnailPath = invoiceFile.absolutePath,
            fileSizeBytes = samplePdf.length(),
            ocrFullText = invoiceOcr,
            tags = "Invoice,Acme,Cloud,Expense"
        ))

        dao.insertPages(listOf(
            PageEntity(
                id = "invoice_page_1",
                documentId = docId,
                pageIndex = 0,
                imagePath = invoiceFile.absolutePath,
                originalImagePath = invoiceFile.absolutePath,
                rotationDegrees = 0,
                filterName = ScanFilter.MAGIC_COLOR.name,
                ocrText = invoiceOcr
            )
        ))
    }

    private fun DocumentEntity.toItem(): ScannedDocumentItem {
        return ScannedDocumentItem(
            id = id,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            pageCount = pageCount,
            category = try { DocumentCategory.valueOf(category) } catch (e: Exception) { DocumentCategory.ALL },
            isFavorite = isFavorite,
            pdfFilePath = pdfFilePath,
            thumbnailPath = thumbnailPath,
            fileSizeBytes = fileSizeBytes,
            ocrSummary = ocrFullText.take(120),
            tags = tags.split(",").filter { it.isNotBlank() }
        )
    }
}
