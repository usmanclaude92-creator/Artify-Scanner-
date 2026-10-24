package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.camera.ScanningSessionManager
import com.example.data.model.ScanMode
import com.example.ocr.OcrEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Artify Scanner", appName)
    }

    @Test
    fun `test receipt parsing extracted fields`() {
        val sampleReceiptText = """
            STARBUCKS COFFEE #1042
            123 Market St, San Francisco CA
            Date: 10/24/2026
            Invoice #: INV-4491
            Caffe Latte 1 $4.50
            Caramel Macchiato 1 $5.25
            Subtotal: $9.75
            Tax: $0.85
            Total: $10.60
            Thank you for visiting!
        """.trimIndent()

        val parsed = OcrEngine.parseReceiptOrInvoice(sampleReceiptText)
        assertEquals("STARBUCKS COFFEE #1042", parsed.merchant)
        assertEquals("10/24/2026", parsed.date)
        assertEquals("INV-4491", parsed.invoiceNumber)
        assertEquals("10.60", parsed.total)
    }

    @Test
    fun `test scanning session manager initialization`() {
        ScanningSessionManager.startNewSession(ScanMode.BATCH_DOCUMENTS)
        assertEquals(0, ScanningSessionManager.capturedPages.size)
        assertEquals(ScanMode.BATCH_DOCUMENTS, ScanningSessionManager.scanMode.value)
    }
}
