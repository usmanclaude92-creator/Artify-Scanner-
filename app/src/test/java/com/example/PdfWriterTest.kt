package com.example

import com.example.pdf.PdfInfo
import com.example.pdf.PdfPageSpec
import com.example.pdf.PdfTextBox
import com.example.pdf.PdfWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class PdfWriterTest {

    private fun page(text: String) = PdfPageSpec(
        widthPt = 595f, heightPt = 842f,
        jpeg = byteArrayOf(1, 2, 3, 4, 5),
        imageWidth = 10, imageHeight = 20,
        drawLeft = 18f, drawBottom = 18f, drawWidth = 559f, drawHeight = 780f,
        hiddenText = listOf(PdfTextBox(text, 40f, 700f, 200f, 12f)),
        watermark = "DRAFT", footerLeft = "Artify Scanner", footerRight = "Page 1 of 2"
    )

    private fun build(): String {
        val bytes = ByteArrayOutputStream()
        PdfWriter(bytes, PdfInfo("My Title", "Jane", "Artify")).use { w ->
            w.addPage(page("Invoice (total) 42"))
            w.addPage(page("Second page"))
            w.finish()
        }
        return String(bytes.toByteArray(), Charsets.ISO_8859_1)
    }

    @Test
    fun `xref offsets point at the right objects`() {
        val pdf = build()
        val xrefAt = Regex("""startxref\s+(\d+)""").find(pdf)!!.groupValues[1].toInt()
        assertTrue(pdf.substring(xrefAt).startsWith("xref"))
        val entries = Regex("""(\d{10}) 00000 n """).findAll(pdf.substring(xrefAt)).map { it.groupValues[1].toInt() }.toList()
        assertEquals(11, entries.size) // objects 1..11: 5 fixed + 3 per page
        entries.forEachIndexed { i, offset ->
            assertTrue("object ${i + 1} at $offset", pdf.substring(offset).startsWith("${i + 1} 0 obj"))
        }
    }

    @Test
    fun `contains invisible text layer, escaped, and metadata`() {
        val pdf = build()
        assertTrue(pdf.contains("3 Tr"))
        assertTrue(pdf.contains("(Invoice \\(total\\) 42) Tj"))
        assertTrue(pdf.contains("/Count 2"))
        assertTrue(pdf.contains("/Title <FEFF004D00790020005400690074006C0065>"))
        assertTrue(pdf.contains("/Filter /DCTDecode"))
        assertTrue(pdf.trimEnd().endsWith("%%EOF"))
    }

    @Test
    fun `rotates normalized boxes clockwise`() {
        val box = floatArrayOf(0.1f, 0.2f, 0.5f, 0.3f)
        assertArrayEquals(box, PdfWriter.rotateNormalized(0.1f, 0.2f, 0.5f, 0.3f, 0), 1e-6f)
        assertArrayEquals(floatArrayOf(0.7f, 0.1f, 0.8f, 0.5f), PdfWriter.rotateNormalized(0.1f, 0.2f, 0.5f, 0.3f, 90), 1e-6f)
        assertArrayEquals(floatArrayOf(0.5f, 0.7f, 0.9f, 0.8f), PdfWriter.rotateNormalized(0.1f, 0.2f, 0.5f, 0.3f, 180), 1e-6f)
        assertArrayEquals(floatArrayOf(0.2f, 0.5f, 0.3f, 0.9f), PdfWriter.rotateNormalized(0.1f, 0.2f, 0.5f, 0.3f, 270), 1e-6f)
    }
}
