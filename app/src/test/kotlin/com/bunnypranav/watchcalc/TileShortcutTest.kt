package com.bunnypranav.watchcalc

import com.bunnypranav.watchcalc.tile.SHORTCUTS
import com.bunnypranav.watchcalc.ui.PAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tile promises three shortcuts into the first three keypads. If the pages
 * are ever reordered, these are the assertions that should complain.
 */
class TileShortcutTest {

    @Test
    fun shortcutsPointAtTheFirstThreePages() {
        assertEquals(3, SHORTCUTS.size)
        assertEquals(listOf(0, 1, 2), SHORTCUTS.map { it.page })
        assertEquals(listOf("123", "log", "trig"), SHORTCUTS.map { it.label })
    }

    @Test
    fun everyShortcutLabelMatchesItsPage() {
        for (shortcut in SHORTCUTS) {
            assertEquals(PAGES[shortcut.page].name, shortcut.label)
            assertTrue(
                "shortcut ${shortcut.label} needs a spoken description",
                shortcut.description.isNotBlank()
            )
        }
    }
}
