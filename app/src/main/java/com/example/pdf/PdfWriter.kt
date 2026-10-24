package com.example.pdf

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class PdfInfo(val title: String, val author: String, val creator: String)

/** Invisible text positioned over the page image so readers can search and select it. */
data class PdfTextBox(
    val text: String,
    val x: Float,
    val baselineY: Float,
    val width: Float,
    val fontSize: Float
)

class PdfPageSpec(
    val widthPt: Float,
    val heightPt: Float,
    val jpeg: ByteArray,
    val imageWidth: Int,
    val imageHeight: Int,
    val drawLeft: Float,
    val drawBottom: Float,
    val drawWidth: Float,
    val drawHeight: Float,
    val hiddenText: List<PdfTextBox> = emptyList(),
    val watermark: String = "",
    val footerLeft: String = "",
    val footerRight: String = "",
    val footerMargin: Float = 18f
)

/**
 * Minimal streaming PDF 1.4 writer: one JPEG image per page plus an invisible text layer
 * (text render mode 3), which Android's [android.graphics.pdf.PdfDocument] cannot produce.
 * Coordinates are PDF points with the origin at the bottom-left.
 */
class PdfWriter(output: OutputStream, private val info: PdfInfo) : java.io.Closeable {

    private val out = CountingStream(output)
    private val offsets = HashMap<Int, Long>()
    private val pageObjects = ArrayList<Int>()
    private var nextObject = FIRST_PAGE_OBJECT
    private var finished = false

    val pageCount: Int get() = pageObjects.size

    init {
        out.write("%PDF-1.4\n%".toByteArray(Charsets.ISO_8859_1))
        out.write(byteArrayOf(0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))
        obj(CATALOG) { "<< /Type /Catalog /Pages $PAGES 0 R >>" }
        obj(INFO) {
            val sb = StringBuilder("<< ")
            if (info.title.isNotBlank()) sb.append("/Title ${hexString(info.title)} ")
            if (info.author.isNotBlank()) sb.append("/Author ${hexString(info.author)} ")
            if (info.creator.isNotBlank()) sb.append("/Creator ${hexString(info.creator)} ")
            sb.append("/Producer ${hexString("Artify Scanner")} >>").toString()
        }
        obj(FONT) { "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>" }
        obj(EXT_GSTATE) { "<< /Type /ExtGState /ca 0.18 >>" }
    }

    fun addPage(page: PdfPageSpec) {
        check(!finished) { "PDF already finished" }
        val imageObj = nextObject++
        val contentObj = nextObject++
        val pageObj = nextObject++

        stream(imageObj, "/Type /XObject /Subtype /Image /Width ${page.imageWidth} /Height ${page.imageHeight} " +
            "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode", page.jpeg)
        stream(contentObj, "", buildContent(page))
        obj(pageObj) {
            "<< /Type /Page /Parent $PAGES 0 R /MediaBox [0 0 ${num(page.widthPt)} ${num(page.heightPt)}] " +
                "/Contents $contentObj 0 R /Resources << /XObject << /Im0 $imageObj 0 R >> " +
                "/Font << /F1 $FONT 0 R >> /ExtGState << /GS1 $EXT_GSTATE 0 R >> >> >>"
        }
        pageObjects.add(pageObj)
    }

    fun finish() {
        check(!finished) { "PDF already finished" }
        finished = true
        obj(PAGES) {
            "<< /Type /Pages /Count ${pageObjects.size} /Kids [${pageObjects.joinToString(" ") { "$it 0 R" }}] >>"
        }
        val xrefOffset = out.position
        val size = nextObject
        val sb = StringBuilder("xref\n0 $size\n0000000000 65535 f \n")
        for (n in 1 until size) {
            sb.append(String.format(Locale.US, "%010d 00000 n \n", offsets[n] ?: 0L))
        }
        sb.append("trailer\n<< /Size $size /Root $CATALOG 0 R /Info $INFO 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    override fun close() = out.close()

    private fun buildContent(page: PdfPageSpec): ByteArray {
        val c = StringBuilder()
        c.append("q\n${num(page.drawWidth)} 0 0 ${num(page.drawHeight)} ${num(page.drawLeft)} ${num(page.drawBottom)} cm\n/Im0 Do\nQ\n")

        if (page.hiddenText.isNotEmpty()) {
            c.append("q\nBT\n3 Tr\n")
            for (box in page.hiddenText) {
                if (box.text.isBlank() || box.fontSize <= 0f) continue
                val natural = box.text.length * box.fontSize * AVG_GLYPH_WIDTH
                val scale = if (natural > 0f) (box.width / natural * 100f).coerceIn(20f, 400f) else 100f
                c.append("/F1 ${num(box.fontSize)} Tf\n${num(scale)} Tz\n1 0 0 1 ${num(box.x)} ${num(box.baselineY)} Tm\n")
                c.append(literal(box.text)).append(" Tj\n")
            }
            c.append("ET\nQ\n")
        }

        if (page.watermark.isNotBlank()) {
            val size = 34f
            val angle = -35.0 * PI / 180.0
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            val halfWidth = page.watermark.length * size * AVG_GLYPH_WIDTH / 2f
            val cx = page.widthPt / 2f
            val cy = page.heightPt / 2f
            val tx = cx - halfWidth * cosA
            val ty = cy - halfWidth * sinA
            c.append("q\n/GS1 gs\n0.47 g\nBT\n/F1 ${num(size)} Tf\n")
            c.append("${num(cosA)} ${num(sinA)} ${num(-sinA)} ${num(cosA)} ${num(tx)} ${num(ty)} Tm\n")
            c.append(literal(page.watermark)).append(" Tj\nET\nQ\n")
        }

        if (page.footerLeft.isNotBlank() || page.footerRight.isNotBlank()) {
            c.append("q\n0.3 g\nBT\n/F1 9 Tf\n")
            if (page.footerLeft.isNotBlank()) {
                c.append("1 0 0 1 ${num(page.footerMargin + 8f)} 10 Tm\n${literal(page.footerLeft)} Tj\n")
            }
            if (page.footerRight.isNotBlank()) {
                val w = page.footerRight.length * 9f * AVG_GLYPH_WIDTH
                c.append("1 0 0 1 ${num(page.widthPt - page.footerMargin - 8f - w)} 10 Tm\n${literal(page.footerRight)} Tj\n")
            }
            c.append("ET\nQ\n")
        }
        return c.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun obj(number: Int, body: () -> String) {
        offsets[number] = out.position
        out.write("$number 0 obj\n${body()}\nendobj\n".toByteArray(Charsets.ISO_8859_1))
    }

    private fun stream(number: Int, dictEntries: String, data: ByteArray) {
        offsets[number] = out.position
        out.write("$number 0 obj\n<< $dictEntries /Length ${data.size} >>\nstream\n".toByteArray(Charsets.ISO_8859_1))
        out.write(data)
        out.write("\nendstream\nendobj\n".toByteArray(Charsets.ISO_8859_1))
    }

    private class CountingStream(private val delegate: OutputStream) : OutputStream() {
        var position = 0L
            private set

        override fun write(b: Int) {
            delegate.write(b)
            position++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            position += len
        }

        override fun flush() = delegate.flush()

        override fun close() = delegate.close()
    }

    companion object {
        private const val CATALOG = 1
        private const val PAGES = 2
        private const val INFO = 3
        private const val FONT = 4
        private const val EXT_GSTATE = 5
        private const val FIRST_PAGE_OBJECT = 6
        private const val AVG_GLYPH_WIDTH = 0.5f

        private fun num(v: Float): String = String.format(Locale.US, "%.2f", v)

        /** UTF-16BE hex string, safe for any characters. */
        internal fun hexString(value: String): String {
            val sb = StringBuilder("<FEFF")
            for (b in value.toByteArray(Charsets.UTF_16BE)) sb.append(String.format(Locale.US, "%02X", b))
            return sb.append('>').toString()
        }

        /** Literal string in WinAnsi/Latin-1; characters outside it become '?'. */
        internal fun literal(text: String): String {
            val sb = StringBuilder("(")
            for (ch in text) {
                when {
                    ch == '(' || ch == ')' || ch == '\\' -> sb.append('\\').append(ch)
                    ch.code < 32 -> sb.append(' ')
                    ch.code > 255 -> sb.append('?')
                    else -> sb.append(ch)
                }
            }
            return sb.append(')').toString()
        }

        /** Rotates a normalized box (l, t, r, b) clockwise by 0/90/180/270 degrees. */
        fun rotateNormalized(l: Float, t: Float, r: Float, b: Float, degrees: Int): FloatArray =
            when (((degrees % 360) + 360) % 360) {
                90 -> floatArrayOf(1f - b, l, 1f - t, r)
                180 -> floatArrayOf(1f - r, 1f - b, 1f - l, 1f - t)
                270 -> floatArrayOf(t, 1f - r, b, 1f - l)
                else -> floatArrayOf(l, t, r, b)
            }

        fun jpegBytes(write: (ByteArrayOutputStream) -> Unit): ByteArray =
            ByteArrayOutputStream().also(write).toByteArray()
    }
}
