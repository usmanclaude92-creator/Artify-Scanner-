package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.example.pdf.PdfFooter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfFooterTest {
    @Test fun footerIsAddedBelowTheScan() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scan = Bitmap.createBitmap(1190, 1500, Bitmap.Config.ARGB_8888)
        Canvas(scan).apply {
            drawColor(Color.rgb(250, 240, 200))
            drawRect(100f, 100f, 1090f, 1400f, Paint().apply { color = Color.rgb(230, 200, 120) })
        }
        val out = PdfFooter.compose(context, scan, 2, 5, true)
        assertEquals(scan.width, out.width)
        assertEquals(scan.height + PdfFooter.stripHeightPx(scan.width), out.height)
        val dir = File("build/screenshots").apply { mkdirs() }
        // Bottom part only, so the strip is easy to inspect
        val crop = Bitmap.createBitmap(out, 0, out.height - 300, out.width, 300)
        FileOutputStream(File(dir, "pdf_footer.png")).use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val noPage = PdfFooter.compose(context, scan, 1, 1, false)
        val crop2 = Bitmap.createBitmap(noPage, 0, noPage.height - 300, noPage.width, 300)
        FileOutputStream(File(dir, "pdf_footer_nopage.png")).use { crop2.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
