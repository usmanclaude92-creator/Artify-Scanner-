package com.example

import com.example.ocr.OcrEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrParsingTest {

    @Test
    fun `normalizes thousands and decimal separators`() {
        assertEquals("1250.00", OcrEngine.normalizeAmount("1,250.00"))
        assertEquals("1250.00", OcrEngine.normalizeAmount("1.250,00"))
        assertEquals("10.60", OcrEngine.normalizeAmount("10,60"))
        assertEquals("1998.00", OcrEngine.normalizeAmount("1998.00"))
    }

    @Test
    fun `parses totals with thousands separators`() {
        val parsed = OcrEngine.parseReceiptOrInvoice(
            """
            ACME CLOUD SOLUTIONS LLC
            Subtotal: ${'$'}1,850.00
            Tax: ${'$'}148.00
            Total Due: ${'$'}1,998.00
            """.trimIndent()
        )
        assertEquals("ACME CLOUD SOLUTIONS LLC", parsed.merchant)
        assertEquals("1850.00", parsed.subtotal)
        assertEquals("148.00", parsed.tax)
    }

    @Test
    fun `csv quotes every field`() {
        val csv = OcrEngine.exportToCsv(
            com.example.data.model.ReceiptParsedData(merchant = "A \"B\", Inc", total = "1.00")
        )
        assertTrue(csv.contains("Merchant,\"A \"\"B\"\", Inc\""))
        assertTrue(csv.contains("Total,\"1.00\""))
    }
}
