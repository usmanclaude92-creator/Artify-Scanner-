package com.example.ocr

import android.graphics.Bitmap
import com.example.data.model.ReceiptParsedData
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.regex.Pattern
import kotlin.coroutines.resume

object OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { error ->
                    error.printStackTrace()
                    continuation.resume("")
                }
        } catch (e: Exception) {
            e.printStackTrace()
            continuation.resume("")
        }
    }

    /**
     * Parses structured receipt or invoice fields from raw OCR text.
     */
    fun parseReceiptOrInvoice(ocrText: String): ReceiptParsedData {
        if (ocrText.isBlank()) return ReceiptParsedData()

        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }

        // 1. Merchant name: usually first bold/prominent line that isn't a date or generic word
        var merchant = "Unknown Merchant"
        for (line in lines.take(4)) {
            val lower = line.lowercase()
            if (!lower.contains("receipt") && !lower.contains("invoice") &&
                !lower.contains("tax") && !lower.contains("welcome") &&
                !line.matches(Regex(".*\\d{2,}.*")) && line.length in 3..40
            ) {
                merchant = line
                break
            }
        }
        if (merchant == "Unknown Merchant" && lines.isNotEmpty()) {
            merchant = lines.first().take(35)
        }

        // 2. Dates
        var date = ""
        val datePattern = Pattern.compile(
            "(\\b\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}\\b)|(\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},?\\s+\\d{2,4}\\b)",
            Pattern.CASE_INSENSITIVE
        )
        for (line in lines) {
            val matcher = datePattern.matcher(line)
            if (matcher.find()) {
                date = matcher.group()
                break
            }
        }

        // 3. Invoice / Receipt Number
        var invoiceNum = ""
        val invPattern = Pattern.compile(
            "(invoice|inv|receipt|bill|order|ref|ticket)\\s*[:#.]?\\s*([A-Z0-9_-]{3,20})",
            Pattern.CASE_INSENSITIVE
        )
        for (line in lines) {
            val matcher = invPattern.matcher(line)
            if (matcher.find()) {
                invoiceNum = matcher.group(2) ?: ""
                break
            }
        }

        // 4. Currency
        var currency = "$"
        if (ocrText.contains("€") || ocrText.contains("EUR")) currency = "€"
        else if (ocrText.contains("£") || ocrText.contains("GBP")) currency = "£"
        else if (ocrText.contains("¥") || ocrText.contains("JPY")) currency = "¥"
        else if (ocrText.contains("₹") || ocrText.contains("INR")) currency = "₹"

        // 5. Total, Subtotal, Tax Amounts
        var total = ""
        var subtotal = ""
        var tax = ""

        val totalPattern = Pattern.compile("(total|grand total|amount due|balance due)\\s*[:$€£]?\\s*([0-9]+[.,][0-9]{2})", Pattern.CASE_INSENSITIVE)
        val subtotalPattern = Pattern.compile("(subtotal|sub-total|sub total|net)\\s*[:$€£]?\\s*([0-9]+[.,][0-9]{2})", Pattern.CASE_INSENSITIVE)
        val taxPattern = Pattern.compile("(tax|vat|gst|sales tax)\\s*[:$€£]?\\s*([0-9]+[.,][0-9]{2})", Pattern.CASE_INSENSITIVE)

        for (line in lines) {
            if (total.isBlank()) {
                val m = totalPattern.matcher(line)
                if (m.find()) total = m.group(2) ?: ""
            }
            if (subtotal.isBlank()) {
                val m = subtotalPattern.matcher(line)
                if (m.find()) subtotal = m.group(2) ?: ""
            }
            if (tax.isBlank()) {
                val m = taxPattern.matcher(line)
                if (m.find()) tax = m.group(2) ?: ""
            }
        }

        // Fallback for total: find maximum decimal value in document if explicit "Total" label missing
        if (total.isBlank()) {
            val amountPattern = Pattern.compile("([0-9]+[.,][0-9]{2})")
            var maxAmount = 0.0
            for (line in lines) {
                val m = amountPattern.matcher(line)
                while (m.find()) {
                    val candidate = m.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                    if (candidate > maxAmount && candidate < 100000.0) {
                        maxAmount = candidate
                        total = String.format("%.2f", candidate)
                    }
                }
            }
        }

        // 6. Extracted Line items
        val lineItems = lines.filter { line ->
            line.matches(Regex(".*[0-9]+[.,][0-9]{2}.*")) &&
            !line.lowercase().contains("total") &&
            !line.lowercase().contains("subtotal") &&
            !line.lowercase().contains("tax") &&
            line.length > 5
        }.take(8)

        return ReceiptParsedData(
            merchant = merchant,
            date = date,
            invoiceNumber = invoiceNum,
            currency = currency,
            subtotal = subtotal,
            tax = tax,
            total = total,
            lineItems = lineItems
        )
    }

    /**
     * Export structured receipt or invoice records to CSV string.
     */
    fun exportToCsv(parsed: ReceiptParsedData): String {
        val sb = StringBuilder()
        sb.append("Field,Value\n")
        sb.append("Merchant,\"${parsed.merchant.replace("\"", "\"\"")}\"\n")
        sb.append("Invoice Number,\"${parsed.invoiceNumber}\"\n")
        sb.append("Date,\"${parsed.date}\"\n")
        sb.append("Currency,\"${parsed.currency}\"\n")
        sb.append("Subtotal,\"${parsed.subtotal}\"\n")
        sb.append("Tax/VAT,\"${parsed.tax}\"\n")
        sb.append("Total,\"${parsed.total}\"\n")
        if (parsed.lineItems.isNotEmpty()) {
            sb.append("\nLine Items:\n")
            parsed.lineItems.forEach { item ->
                sb.append("\"${item.replace("\"", "\"\"")}\"\n")
            }
        }
        return sb.toString()
    }
}
