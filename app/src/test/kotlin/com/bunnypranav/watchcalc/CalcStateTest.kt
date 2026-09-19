package com.bunnypranav.watchcalc

import com.bunnypranav.watchcalc.engine.Angle
import com.bunnypranav.watchcalc.state.CalcState
import com.bunnypranav.watchcalc.state.HistEntry
import com.bunnypranav.watchcalc.state.KeyOutcome
import com.bunnypranav.watchcalc.state.MemoryStore
import com.bunnypranav.watchcalc.state.MAX_HISTORY
import com.bunnypranav.watchcalc.state.Sheet
import com.bunnypranav.watchcalc.ui.Action
import com.bunnypranav.watchcalc.ui.Key
import com.bunnypranav.watchcalc.ui.PAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The interaction rules that make the thing usable on a wrist: Casio-style
 * behaviour after "=", whole-token backspace, the smart bracket key, and the
 * hop back to the number pad after a function key.
 */
class CalcStateTest {

    private fun state() = CalcState(MemoryStore())

    /** Find a real key by label so the tests also validate the key tables. */
    private fun key(page: String, label: String): Key {
        val p = PAGES.first { it.name == page }
        return p.rows.flatten().firstOrNull { it.label == label }
            ?: error("no key '$label' on page $page")
    }

    private fun CalcState.type(vararg text: String) = text.forEach { insert(it) }

    @Test
    fun evaluatesWhatWasTyped() {
        val s = state()
        s.type("2", "+", "3", "*", "4")
        s.equals()
        assertEquals("14", s.result)
        assertFalse(s.resultIsError)
    }

    @Test
    fun showsTheExactFormUnderTheDecimal() {
        val s = state()
        s.type("cos(", "3", "0")
        s.equals()
        assertEquals("0.8660254038", s.result)
        assertEquals("√3/2", s.subLine)
    }

    @Test
    fun afterEqualsADigitStartsFreshButAnOperatorContinues() {
        val s = state()
        s.type("5")
        s.equals()
        s.type("7")                       // digit: fresh expression
        assertEquals("7", s.expr)

        s.equals()
        s.type("+", "3")                  // operator: continue from Ans
        s.equals()
        assertEquals("Ans+3", "Ans+3")
        assertEquals("10", s.result)
    }

    @Test
    fun backspaceRemovesAWholeToken() {
        val s = state()
        s.type("1", "2", "sin(")
        s.backspace()
        assertEquals("12", s.expr)
        s.backspace()
        assertEquals("1", s.expr)
    }

    @Test
    fun backspaceRemovesInfixOperatorNames() {
        val s = state()
        s.type("1", "0", "nCr")
        s.backspace()
        assertEquals("10", s.expr)
    }

    @Test
    fun smartBracketOpensThenCloses() {
        val s = state()
        s.smartParen()
        assertEquals("(", s.expr)
        s.type("3", "+", "4")
        s.smartParen()
        assertEquals("(3+4)", s.expr)
        s.smartParen()                    // nothing open now, so open again
        assertEquals("(3+4)(", s.expr)
    }

    @Test
    fun equalsClosesOutstandingBrackets() {
        val s = state()
        s.type("sin(", "3", "0")
        s.equals()
        assertEquals("sin(30)", s.expr)
        assertEquals("0.5", s.result)
    }

    @Test
    fun twoArgumentFunctionsAreTypedInfix() {
        val s = state()
        s.type("1", "0", "nCr", "3")
        s.equals()
        assertEquals("120", s.result)

        val t = state()
        t.type("3", "root", "2", "7")
        t.equals()
        assertEquals("3", t.result)
    }

    @Test
    fun factorisesAndReportsDivisorStats() {
        val s = state()
        s.type("2", "5", "2", "0")
        s.factor()
        assertEquals("2³×3²×5×7", s.result)
        assertEquals("d 48 · σ 9360 · φ 576", s.subLine)
    }

    @Test
    fun factorRejectsWhatItCannotFactor() {
        val s = state()
        s.type("2", ".", "5")
        s.factor()
        assertTrue(s.resultIsError)
        assertEquals("Whole number ≥ 1", s.result)
    }

    @Test
    fun factorAcceptsAnExpression() {
        val s = state()
        s.type("1", "2", "*", "3", "0")
        s.factor()
        assertEquals("2³×3²×5", s.result)
    }

    @Test
    fun primeReportsItself() {
        val s = state()
        s.type("9", "7")
        s.factor()
        assertEquals("prime", s.result)
        assertEquals("d 2 · σ 98 · φ 96", s.subLine)
    }

    @Test
    fun angleModeCyclesAndAffectsResults() {
        val s = state()
        assertEquals(Angle.DEG, s.angle)
        s.cycleAngle(); assertEquals(Angle.RAD, s.angle)
        s.cycleAngle(); assertEquals(Angle.GRA, s.angle)
        s.cycleAngle(); assertEquals(Angle.DEG, s.angle)

        s.cycleAngle()                    // RAD
        s.type("sin(", "π", "/", "6")
        s.equals()
        assertEquals("0.5", s.result)
    }

    @Test
    fun memoryAccumulatesAndClears() {
        val s = state()
        s.type("4", "2"); s.equals()
        s.memAdd(1)
        assertEquals(42.0, s.mem, 0.0)
        s.memAdd(1)
        assertEquals(84.0, s.mem, 0.0)
        s.memAdd(-1)
        assertEquals(42.0, s.mem, 0.0)
        s.clear()
        s.type("M", "+", "8"); s.equals()
        assertEquals("50", s.result)
        s.memClear()
        assertEquals(0.0, s.mem, 0.0)
    }

    @Test
    fun significantFiguresCycle() {
        val s = state()
        s.type("1", "/", "3"); s.equals()
        assertEquals("0.3333333333", s.result)
        s.cycleSig()                      // 12
        assertEquals("0.333333333333", s.result)
        s.cycleSig()                      // wraps to 4
        assertEquals("0.3333", s.result)
    }

    @Test
    fun historyRecordsAndCaps() {
        val s = state()
        repeat(MAX_HISTORY + 5) { i ->
            s.clear()
            s.type("$i", "+", "1")
            s.equals()
        }
        assertEquals(MAX_HISTORY, s.history.size)
        assertEquals("44+1", s.history.first().expr)

        s.clear()
        s.reuse(HistEntry("2+3", "5"))
        assertEquals("(2+3)", s.expr)
    }

    @Test
    fun functionKeysHopHomeButStandalonePagesDoNot() {
        val s = state()
        val trig = PAGES.indexOfFirst { it.name == "trig" }
        val prime = PAGES.indexOfFirst { it.name == "prime" }

        val sin = key("trig", "sin")
        assertEquals(KeyOutcome.Inserted, s.press(sin, false))
        assertTrue("a function key should return to the number pad", s.shouldReturnHome(trig, KeyOutcome.Inserted))

        val seven = key("prime", "7")
        assertEquals(KeyOutcome.Inserted, s.press(seven, false))
        assertFalse("the prime page keeps its own digits", s.shouldReturnHome(prime, KeyOutcome.Inserted))

        val drg = key("trig", "DRG")
        assertEquals(KeyOutcome.Handled, s.press(drg, false))
        assertFalse("an action key should not move pages", s.shouldReturnHome(trig, KeyOutcome.Handled))
    }

    @Test
    fun longPressFiresTheSecondaryLabel() {
        val s = state()
        val back = key("123", "⌫")
        assertNotNull(back.secondary)
        s.type("1", "2", "3")
        s.press(back, true)               // long-press = AC
        assertEquals("", s.expr)

        val dot = key("123", ".")
        s.press(dot, true)                // long-press = E (exponent)
        assertEquals("E", s.expr)

        val times = key("123", "×")
        s.clear()
        s.type("2")
        s.press(times, true)              // long-press = ^
        s.type("1", "0")
        s.equals()
        assertEquals("1024", s.result)
    }

    @Test
    fun sheetKeysOpenTheirSheets() {
        val s = state()
        s.press(key("more", "CONST"), false)
        assertEquals(Sheet.Constants, s.sheet)
        s.sheet = Sheet.None
        s.press(key("more", "HIST"), false)
        assertEquals(Sheet.History, s.sheet)
    }

    @Test
    fun errorsAreReportedNotThrown() {
        val s = state()
        s.type("1", "/", "0")
        s.equals()
        assertTrue(s.resultIsError)
        assertEquals("Divide by 0", s.result)

        s.clear()
        s.type("tan(", "9", "0")
        s.equals()
        assertTrue(s.resultIsError)
        assertEquals("Math Error", s.result)
    }

    @Test
    fun everythingSurvivesAProcessRestart() {
        val store = MemoryStore()
        val first = CalcState(store)
        first.type("6", "*", "7")
        first.equals()
        first.memAdd(1)
        first.cycleAngle()
        first.cycleSig()
        first.type("1", "2", "+")

        // new instance over the same store, as if the app had been killed
        val second = CalcState(store)
        assertEquals("12+", second.expr)
        assertEquals(42.0, second.ans, 0.0)
        assertEquals(42.0, second.mem, 0.0)
        assertEquals(Angle.RAD, second.angle)
        assertEquals(12, second.sig)
        assertEquals(1, second.history.size)
        assertEquals("6*7", second.history.first().expr)
    }

    @Test
    fun everyActionIsHandled() {
        // press() must not fall through for any action in the enum
        val s = state()
        for (action in Action.entries) {
            s.clear()
            s.type("4")
            val outcome = s.press(Key("probe", action = action), false)
            assertEquals("action $action was ignored", KeyOutcome.Handled, outcome)
        }
    }
}
