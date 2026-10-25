package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.example.R
import kotlin.math.roundToInt

/**
 * Branding strip under every PDF page: the logo and name centred between thin lines, page counter on the right.
 * The scanned page fills the rest of the sheet, so the strip is kept thin (about 26 pt on a 595 pt wide page).
 */
object PdfFooter {
    private const val HEIGHT_RATIO = 26f / 595f
    private const val REF_WIDTH = 1785f          // the design was drawn on a 1785 px wide page

    private val navy = Color.rgb(15, 23, 42)
    private val blue = Color.rgb(2, 132, 199)
    private val slate = Color.rgb(71, 85, 105)
    private val rule = Color.rgb(203, 213, 225)
    private val stripBg = Color.rgb(241, 245, 249)

    fun stripHeightPx(widthPx: Int): Int = (widthPx * HEIGHT_RATIO).roundToInt().coerceAtLeast(24)

    /** Returns [scan] with the footer strip added below it. The caller keeps ownership of [scan]. */
    fun compose(context: Context, scan: Bitmap, pageNumber: Int, totalPages: Int, showPageNumber: Boolean): Bitmap {
        val w = scan.width
        val strip = stripHeightPx(w)
        val out = Bitmap.createBitmap(w, scan.height + strip, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(scan, 0f, 0f, null)

        val s = w / REF_WIDTH
        val top = scan.height.toFloat()
        val cy = top + strip / 2f
        val fill = Paint().apply { color = stripBg }
        canvas.drawRect(0f, top, w.toFloat(), top + strip, fill)

        val bold = font(context, R.font.inter_bold, Typeface.DEFAULT_BOLD)
        val semi = font(context, R.font.inter_semibold, Typeface.DEFAULT_BOLD)
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = 30f * s }
        val artify = "Artify "
        val scanner = "Scanner"
        val nameWidth = namePaint.measureText(artify + scanner)
        val iconSize = 54f * s
        val total = 68f * s + nameWidth
        val x0 = (w - total) / 2f

        // Thin lines either side of the seal
        val linePaint = Paint().apply { color = rule }
        val rightEnd = if (showPageNumber) w - 190f * s else w - 60f * s
        canvas.drawRect(60f * s, cy - s, x0 - 30f * s, cy + s, linePaint)
        canvas.drawRect(x0 + total + 30f * s, cy - s, rightEnd, cy + s, linePaint)

        // Logo (background removed) and name
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.logo_footer)
        if (logo != null) {
            val dst = RectF(x0, cy - iconSize / 2f, x0 + iconSize, cy + iconSize / 2f)
            canvas.drawBitmap(logo, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            logo.recycle()
        }
        val baseline = cy - (namePaint.fontMetrics.ascent + namePaint.fontMetrics.descent) / 2f
        val textX = x0 + 68f * s
        namePaint.color = navy
        canvas.drawText(artify, textX, baseline, namePaint)
        namePaint.color = blue
        canvas.drawText(scanner, textX + namePaint.measureText(artify), baseline, namePaint)

        // Page counter pill
        if (showPageNumber) {
            val pill = RectF(w - 170f * s, cy - 19f * s, w - 60f * s, cy + 19f * s)
            canvas.drawRoundRect(pill, 19f * s, 19f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            canvas.drawRoundRect(
                pill, 19f * s, 19f * s,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * s; color = rule }
            )
            val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = semi; textSize = 22f * s; color = slate; textAlign = Paint.Align.CENTER
            }
            val pageBase = cy - (pagePaint.fontMetrics.ascent + pagePaint.fontMetrics.descent) / 2f
            canvas.drawText("$pageNumber / $totalPages", pill.centerX(), pageBase, pagePaint)
        }
        return out
    }

    private fun font(context: Context, id: Int, fallback: Typeface): Typeface =
        try { ResourcesCompat.getFont(context, id) ?: fallback } catch (e: Exception) { fallback }
}
