package com.example

import com.example.imageprocessing.PageDetector
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PageDetectorTest {
    private val w = 144
    private val h = 192

    private fun frame(background: Int, block: (Int, Int) -> Int?): IntArray =
        IntArray(w * h) { i -> block(i % w, i / w) ?: background }

    @Test fun findsSheetOnDarkerDesk() {
        // Desk 90, sheet 215 spanning x 20..120, y 25..170, with dark "text" lines inside
        val lum = frame(90) { x, y ->
            if (x in 20..120 && y in 25..170) (if (y % 12 == 0 && x in 30..110) 60 else 215) else null
        }
        val q = PageDetector.detect(lum, w, h)
        assertNotNull(q)
        assertEquals(20f / w, q!!.topLeft.x, 0.04f)
        assertEquals(25f / h, q.topLeft.y, 0.04f)
        assertEquals(120f / w, q.bottomRight.x, 0.04f)
        assertEquals(170f / h, q.bottomRight.y, 0.04f)
    }

    @Test fun separatesSheetFromBrighterBlockTouchingIt() {
        // Silver block (170) touching the right edge of the sheet (215): the sheet outline must not swallow it
        val lum = frame(90) { x, y ->
            when {
                x in 20..100 && y in 25..170 -> 215
                x in 101..143 && y in 20..120 -> 170
                else -> null
            }
        }
        val q = PageDetector.detect(lum, w, h)
        assertNotNull(q)
        assertEquals(100f / w, q!!.topRight.x, 0.06f)
    }

    @Test fun nothingOnFlatFrame() {
        assertNull(PageDetector.detect(IntArray(w * h) { 120 }, w, h))
    }
}
