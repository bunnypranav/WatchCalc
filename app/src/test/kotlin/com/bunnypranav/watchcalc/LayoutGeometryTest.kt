package com.bunnypranav.watchcalc

import com.bunnypranav.watchcalc.ui.DISPLAY_HEIGHT
import com.bunnypranav.watchcalc.ui.DISPLAY_PAD_TOP
import com.bunnypranav.watchcalc.ui.DOTS_HEIGHT
import com.bunnypranav.watchcalc.ui.EXPR_HEIGHT
import com.bunnypranav.watchcalc.ui.EXPR_WIDTH
import com.bunnypranav.watchcalc.ui.PAD_HEIGHT
import com.bunnypranav.watchcalc.ui.PAGES
import com.bunnypranav.watchcalc.ui.RESULT_HEIGHT
import com.bunnypranav.watchcalc.ui.RESULT_WIDTH
import com.bunnypranav.watchcalc.ui.ROW_GAP
import com.bunnypranav.watchcalc.ui.ROW_HEIGHT
import com.bunnypranav.watchcalc.ui.ROW_WIDTH
import com.bunnypranav.watchcalc.ui.STATUS_HEIGHT
import com.bunnypranav.watchcalc.ui.STATUS_WIDTH
import com.bunnypranav.watchcalc.ui.SUB_HEIGHT
import com.bunnypranav.watchcalc.ui.SUB_WIDTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * The display is a circle, not a square: at the top and bottom of a round
 * watch the usable width collapses, so a row that is merely "on screen" can
 * still have its corners hidden under the bezel.
 *
 * This reproduces the layout arithmetic in units (1 unit = 1% of the smaller
 * screen side) and asserts every rectangle stays inside the inscribed circle.
 * It is the check that was previously only possible by eye in a browser.
 */
class LayoutGeometryTest {

    /** Margin in units between a rectangle's furthest corner and the bezel. */
    private fun margin(left: Float, top: Float, right: Float, bottom: Float): Float {
        val c = 50f
        val worst = maxOf(
            hypot(left - c, top - c), hypot(right - c, top - c),
            hypot(left - c, bottom - c), hypot(right - c, bottom - c)
        )
        return 50f - worst
    }

    private fun centred(width: Float, top: Float, height: Float) =
        margin((100 - width) / 2f, top, (100 + width) / 2f, top + height)

    private fun rowTop(index: Int) = DISPLAY_HEIGHT + index * (ROW_HEIGHT + ROW_GAP)

    @Test
    fun everyKeyRowStaysInsideTheBezel() {
        for (i in ROW_WIDTH.indices) {
            val m = centred(ROW_WIDTH[i], rowTop(i), ROW_HEIGHT)
            assertTrue(
                "key row $i (width ${ROW_WIDTH[i]}u at y=${rowTop(i)}u) " +
                    "overflows the circle by ${-m} units",
                m > 0f
            )
        }
    }

    @Test
    fun everyDisplayLineStaysInsideTheBezel() {
        val lines = listOf(
            Triple("status", STATUS_WIDTH, DISPLAY_PAD_TOP to STATUS_HEIGHT),
            Triple("expr", EXPR_WIDTH, (DISPLAY_PAD_TOP + STATUS_HEIGHT) to EXPR_HEIGHT),
            Triple(
                "result", RESULT_WIDTH,
                (DISPLAY_PAD_TOP + STATUS_HEIGHT + EXPR_HEIGHT) to RESULT_HEIGHT
            ),
            Triple(
                "sub", SUB_WIDTH,
                (DISPLAY_PAD_TOP + STATUS_HEIGHT + EXPR_HEIGHT + RESULT_HEIGHT) to SUB_HEIGHT
            )
        )
        for ((name, width, band) in lines) {
            val m = centred(width, band.first, band.second)
            assertTrue("$name line overflows the circle by ${-m} units", m > 0f)
        }
        // the display block must not run into the first key row
        val used = DISPLAY_PAD_TOP + STATUS_HEIGHT + EXPR_HEIGHT + RESULT_HEIGHT + SUB_HEIGHT
        assertTrue("display content ($used u) exceeds its $DISPLAY_HEIGHT u box", used <= DISPLAY_HEIGHT)
    }

    @Test
    fun pageIndicatorStaysInsideTheBezel() {
        val dotsTop = DISPLAY_HEIGHT + PAD_HEIGHT
        val width = PAGES.size * 1.6f + (PAGES.size - 1) * 1.8f
        assertTrue(margin((100 - width) / 2f, dotsTop, (100 + width) / 2f, dotsTop + DOTS_HEIGHT) > 0f)
        assertTrue("layout is taller than the screen", dotsTop + DOTS_HEIGHT <= 100f)
    }

    /**
     * The pager is given a fixed height, so the rows have to add up to exactly
     * that or the bottom row gets clipped.
     */
    @Test
    fun rowsFillThePagerExactly() {
        val rows = ROW_WIDTH.size
        val needed = rows * ROW_HEIGHT + (rows - 1) * ROW_GAP
        assertEquals(PAD_HEIGHT, needed, 0.001f)
    }

    @Test
    fun everyPageHasTheExpectedShape() {
        for (page in PAGES) {
            assertEquals("page ${page.name} must have ${ROW_WIDTH.size} rows", ROW_WIDTH.size, page.rows.size)
            page.rows.forEachIndexed { i, row ->
                assertTrue(
                    "page ${page.name} row $i has ${row.size} keys",
                    row.size in 1..5
                )
            }
            // a key must do something
            page.rows.flatten().forEach { key ->
                assertTrue(
                    "key '${key.label}' on ${page.name} neither inserts nor acts",
                    key.insert != null || key.action != null
                )
            }
        }
        assertEquals(5, PAGES.size)
        assertEquals(listOf("123", "log", "trig", "more", "prime"), PAGES.map { it.name })
        assertTrue("only the prime page is standalone", PAGES.count { it.standalone } == 1)
    }

    /** Physical sanity check across the round sizes this will actually run on. */
    @Test
    fun reportsUsableKeySizeOnRealWatches() {
        for (px in listOf(396, 450, 454, 480)) {
            val u = px / 100f
            val tightest = ROW_WIDTH.indices.minOf { centred(ROW_WIDTH[it], rowTop(it), ROW_HEIGHT) }
            val keyW = ROW_WIDTH[1] / 5f * u
            val keyH = ROW_HEIGHT * u
            println(
                "${px}px: tightest row clears the bezel by %.1f px; key ~%.0f x %.0f px"
                    .format(tightest * u, keyW, keyH)
            )
            assertTrue("keys too small at ${px}px", keyW > 20f && keyH > 20f)
            assertTrue("row overflows at ${px}px", tightest * u > 1f)
        }
    }
}
