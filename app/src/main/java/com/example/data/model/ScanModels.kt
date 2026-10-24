package com.example.data.model

import android.graphics.PointF

enum class DocumentCategory(val displayName: String, val iconName: String) {
    ALL("All", "folder"),
    INVOICES("Invoices", "receipt_long"),
    RECEIPTS("Receipts", "receipt"),
    ID_CARDS("ID & Passports", "badge"),
    CONTRACTS("Contracts", "description"),
    NOTES("Notes", "edit_note"),
    PERSONAL("Personal", "person")
}

enum class ScanFilter(val displayName: String) {
    ORIGINAL("Original"),
    MAGIC_COLOR("Magic Color"),
    BW_HIGH_CONTRAST("B&W Clean"),
    GRAYSCALE("Grayscale"),
    CLEAN_WHITE("Whitening")
}

enum class ScanMode(val title: String, val subtitle: String) {
    BATCH_DOCUMENTS("Batch Scan", "Continuous multi-page scanning"),
    SINGLE_PAGE("Single Page", "Capture one crisp page"),
    RECEIPT_INVOICE("Receipt & Invoice", "OCR & structured expense parsing"),
    ID_CARD("ID Card", "Front & Back combined on single sheet")
}

enum class PdfPageSize(val displayName: String, val widthPt: Int, val heightPt: Int) {
    ORIGINAL("Original Fit", 0, 0),
    A4("A4 (210 x 297 mm)", 595, 842),
    LETTER("US Letter (8.5 x 11 in)", 612, 792)
}

enum class PdfQuality(val displayName: String, val jpegQuality: Int) {
    HIGH("High Quality (95%)", 95),
    MEDIUM("Standard (80%)", 80),
    COMPRESSED("Compact (60%)", 60)
}

data class PdfExportConfig(
    val filename: String,
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val quality: PdfQuality = PdfQuality.HIGH,
    val includePageNumbers: Boolean = true,
    val watermarkText: String = "",
    val documentTitle: String = "",
    val author: String = "Artify Scanner User"
)

data class NormalizedCornerPoints(
    val topLeft: PointF = PointF(0.05f, 0.05f),
    val topRight: PointF = PointF(0.95f, 0.05f),
    val bottomRight: PointF = PointF(0.95f, 0.95f),
    val bottomLeft: PointF = PointF(0.05f, 0.95f)
)

data class CapturedPage(
    val id: String,
    val localImagePath: String,
    val originalImagePath: String,
    var pageIndex: Int,
    var rotationDegrees: Int = 0,
    var filter: ScanFilter = ScanFilter.MAGIC_COLOR,
    var cropPoints: NormalizedCornerPoints = NormalizedCornerPoints(),
    var ocrText: String = "",
    var isProcessed: Boolean = false
)

data class ScannedDocumentItem(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val category: DocumentCategory,
    val isFavorite: Boolean,
    val pdfFilePath: String,
    val thumbnailPath: String,
    val fileSizeBytes: Long,
    val ocrSummary: String = "",
    val tags: List<String> = emptyList()
)

data class ReceiptParsedData(
    val merchant: String = "Unknown Merchant",
    val date: String = "",
    val invoiceNumber: String = "",
    val currency: String = "$",
    val subtotal: String = "",
    val tax: String = "",
    val total: String = "",
    val lineItems: List<String> = emptyList()
)
