package com.example.pdf

import java.io.File

/**
 * Android's [android.graphics.pdf.PdfDocument] cannot set document properties, so the Info
 * dictionary is added afterwards as a standard PDF incremental update. On any parse problem the
 * file is left untouched.
 */
object PdfMetadata {

    fun apply(file: File, title: String, author: String, creator: String): Boolean {
        return try {
            val updated = appendInfo(file.readBytes(), title, author, creator) ?: return false
            file.writeBytes(updated)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun appendInfo(pdf: ByteArray, title: String, author: String, creator: String): ByteArray? {
        val text = String(pdf, Charsets.ISO_8859_1)
        val startXrefIdx = text.lastIndexOf("startxref")
        if (startXrefIdx < 0) return null
        val prevXref = Regex("""startxref\s+(\d+)""").find(text, startXrefIdx)?.groupValues?.get(1)
            ?: return null
        val trailerIdx = text.lastIndexOf("trailer", startXrefIdx)
        if (trailerIdx < 0) return null // cross-reference streams are not handled
        val trailer = text.substring(trailerIdx, startXrefIdx)
        val size = Regex("""/Size\s+(\d+)""").find(trailer)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val root = Regex("""/Root\s+(\d+)\s+(\d+)\s+R""").find(trailer)?.let {
            "${it.groupValues[1]} ${it.groupValues[2]} R"
        } ?: return null

        val objNum = size
        val body = StringBuilder()
        if (!pdf.endsWithNewline()) body.append('\n')
        val objOffset = pdf.size + body.length
        body.append("$objNum 0 obj\n<< ")
        if (title.isNotBlank()) body.append("/Title ${pdfString(title)} ")
        if (author.isNotBlank()) body.append("/Author ${pdfString(author)} ")
        if (creator.isNotBlank()) body.append("/Creator ${pdfString(creator)} ")
        body.append("/Producer ${pdfString("Artify Scanner")} >>\nendobj\n")
        val xrefOffset = pdf.size + body.length
        body.append("xref\n$objNum 1\n")
        body.append(String.format("%010d 00000 n \n", objOffset))
        body.append("trailer\n<< /Size ${objNum + 1} /Root $root /Info $objNum 0 R /Prev $prevXref >>\n")
        body.append("startxref\n$xrefOffset\n%%EOF\n")
        return pdf + body.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun ByteArray.endsWithNewline() = isNotEmpty() && (last() == '\n'.code.toByte() || last() == '\r'.code.toByte())

    /** UTF-16BE hex string, safe for any characters. */
    private fun pdfString(value: String): String {
        val sb = StringBuilder("<FEFF")
        for (b in value.toByteArray(Charsets.UTF_16BE)) sb.append(String.format("%02X", b))
        return sb.append('>').toString()
    }
}
