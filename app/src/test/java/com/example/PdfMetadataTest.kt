package com.example

import com.example.pdf.PdfMetadata
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfMetadataTest {

    private val minimalPdf = (
        "%PDF-1.4\n" +
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
            "2 0 obj\n<< /Type /Pages /Kids [] /Count 0 >>\nendobj\n" +
            "xref\n0 3\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n" +
            "trailer\n<< /Size 3 /Root 1 0 R >>\nstartxref\n107\n%%EOF\n"
        ).toByteArray(Charsets.ISO_8859_1)

    @Test
    fun `appends info dictionary as incremental update`() {
        val out = PdfMetadata.appendInfo(minimalPdf, "My Title", "Jane", "Artify Scanner")
        assertNotNull(out)
        val text = String(out!!, Charsets.ISO_8859_1)
        assertTrue(text.startsWith(String(minimalPdf, Charsets.ISO_8859_1)))
        assertTrue(text.contains("3 0 obj"))
        assertTrue(text.contains("/Info 3 0 R"))
        assertTrue(text.contains("/Prev 107"))
        assertTrue(text.contains("/Size 4"))
        assertTrue(text.contains("/Title <FEFF004D00790020005400690074006C0065>"))
        val offset = Regex("""startxref\s+(\d+)\s+%%EOF\s*$""").find(text)!!.groupValues[1].toInt()
        assertTrue(text.substring(offset).startsWith("xref"))
    }

    @Test
    fun `returns null for non-pdf input`() {
        assertNull(PdfMetadata.appendInfo("hello".toByteArray(), "t", "a", "c"))
    }
}
