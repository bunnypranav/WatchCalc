package com.bunnypranav.watchcalc.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bunnypranav.watchcalc.engine.Angle
import com.bunnypranav.watchcalc.engine.CalcError
import com.bunnypranav.watchcalc.engine.Env
import com.bunnypranav.watchcalc.engine.autoClose
import com.bunnypranav.watchcalc.engine.evaluate
import com.bunnypranav.watchcalc.engine.exact
import com.bunnypranav.watchcalc.engine.factorString
import com.bunnypranav.watchcalc.engine.factorize
import com.bunnypranav.watchcalc.engine.fmt
import com.bunnypranav.watchcalc.ui.Action
import com.bunnypranav.watchcalc.ui.Key
import com.bunnypranav.watchcalc.ui.PAGES

enum class Sheet { None, Constants, History }

const val MAX_HISTORY = 40

/** What a keypress did, so the caller knows whether to hop back to page 123. */
enum class KeyOutcome { Inserted, Handled }

/**
 * All calculator state and behaviour — the port of js/ui.js. Kept free of
 * Compose UI so it stays readable and could be unit-tested directly.
 */
class CalcState(private val prefs: Store) {

    var expr by mutableStateOf(prefs.expr)
        private set
    var angle by mutableStateOf(prefs.angle)
        private set
    var ans by mutableStateOf(prefs.ans)
        private set
    var mem by mutableStateOf(prefs.mem)
        private set
    var sig by mutableStateOf(prefs.sig)
        private set
    var history by mutableStateOf(prefs.history)
        private set

    /** Big result line, plus the small blue line under it. */
    var result by mutableStateOf("")
        private set
    var resultIsError by mutableStateOf(false)
        private set
    var subLine by mutableStateOf("")
        private set

    var sheet by mutableStateOf(Sheet.None)
    var page by mutableStateOf(prefs.page)
        private set

    /** True once "=" has produced a value: the next digit starts fresh. */
    private var done = false

    private fun env() = Env(angle, ans, mem)

    private fun setResult(text: String, isError: Boolean, sub: String) {
        result = text
        resultIsError = isError
        subLine = sub
    }

    /* ---------------- entry ---------------- */

    fun insert(text: String) {
        // Casio-style: after "=", a value starts fresh but an operator continues
        if (done) {
            val continues = (text.length == 1 && text[0] in "+-*/^") || text == "!" || text == "%"
            expr = if (continues) "Ans" else ""
            done = false
            setResult("", false, "")
        }
        expr += text
        prefs.expr = expr
    }

    fun smartParen() {
        var open = 0
        for (c in expr) {
            if (c == '(') open++ else if (c == ')') open--
        }
        val last = expr.lastOrNull()
        val wantClose = open > 0 && last != null && last !in "(+-*/^,"
        insert(if (wantClose) ")" else "(")
    }

    /** Whole tokens, longest first, so one press removes all of "sin(". */
    private val tokens = listOf(
        "asinh(", "acosh(", "atanh(", "sinh(", "cosh(", "tanh(",
        "asin(", "acos(", "atan(", "asec(", "acsc(", "acot(",
        "sqrt(", "cbrt(", "logb(", "log2(", "root(",
        "nCr(", "nPr(", "mod(", "gcd(", "lcm(", "abs(", "exp(",
        "sin(", "cos(", "tan(", "sec(", "csc(", "cot(",
        "log(", "deg(", "rad(", "ln(",
        "logb", "root", "nCr", "nPr", "mod", "gcd", "lcm",
        "Ans", "^(-1)", "10^(",
        "Rinf", "atm", "eV", "Na", "kB", "qe", "me", "mp", "mn", "ke", "bW",
        "Vm", "Me", "Re", "a0", "ly", "au", "ε0", "μ0"
    )

    fun backspace() {
        if (done) {
            clear()
            return
        }
        val token = tokens.firstOrNull { expr.endsWith(it) }
        expr = if (token != null) expr.dropLast(token.length) else expr.dropLast(1)
        prefs.expr = expr
    }

    fun clear() {
        expr = ""
        done = false
        setResult("", false, "")
        prefs.expr = ""
    }

    /* ---------------- results ---------------- */

    fun equals() {
        if (expr.isEmpty()) return
        expr = autoClose(expr)
        prefs.expr = expr
        val v = try {
            evaluate(expr, env())
        } catch (e: CalcError) {
            setResult(e.message ?: "Error", true, "")
            return
        }
        ans = v
        done = true
        val text = fmt(v, sig)
        val closed = exact(v)
        setResult(text, false, if (closed != null && closed != text) closed else "")
        history = (listOf(HistEntry(expr, text)) + history).take(MAX_HISTORY)
        prefs.ans = v
        prefs.history = history
    }

    fun factor() {
        if (expr.isEmpty()) return
        expr = autoClose(expr)
        prefs.expr = expr
        val n = try {
            evaluate(expr, env())
        } catch (e: CalcError) {
            setResult(e.message ?: "Error", true, "")
            return
        }
        val f = factorize(n)
        if (f == null) {
            val why = if (n > 9007199254740991.0) "Too large" else "Whole number ≥ 1"
            setResult(why, true, "")
            return
        }
        ans = n
        done = true
        // d, sigma and phi are whole numbers; print them as such
        val stats = "d ${f.d} · σ ${f.sigma} · φ ${f.phi}"
        when {
            n == 1.0 -> setResult("1", false, "no prime factors")
            f.factors.size == 1 && f.factors[0].second == 1 -> setResult("prime", false, stats)
            else -> setResult(factorString(f.factors), false, stats)
        }
        prefs.ans = n
    }

    /* ---------------- modes and memory ---------------- */

    fun cycleAngle() {
        angle = when (angle) {
            Angle.DEG -> Angle.RAD
            Angle.RAD -> Angle.GRA
            Angle.GRA -> Angle.DEG
        }
        prefs.angle = angle
    }

    fun cycleSig() {
        sig = if (sig >= 12) 4 else sig + 2
        prefs.sig = sig
        if (done) {
            val text = fmt(ans, sig)
            setResult(text, false, exact(ans)?.takeIf { it != text } ?: "")
        } else {
            subLine = "$sig sig fig"
        }
    }

    fun memAdd(sign: Int) {
        mem += sign * ans
        prefs.mem = mem
    }

    fun memClear() {
        mem = 0.0
        prefs.mem = mem
    }

    fun goToPage(p: Int) {
        page = p
        prefs.page = p
    }

    /** Paste a history row back in, wrapped so it stays one operand. */
    fun reuse(entry: HistEntry) {
        insert("(" + entry.expr + ")")
    }

    /* ---------------- dispatch ---------------- */

    fun press(key: Key, secondary: Boolean): KeyOutcome {
        val k = if (secondary && key.secondary != null) key.secondary else key
        k.action?.let { action ->
            when (action) {
                Action.Back -> backspace()
                Action.Clear -> clear()
                Action.Paren -> smartParen()
                Action.Equals -> equals()
                Action.Drg -> cycleAngle()
                Action.Sig -> cycleSig()
                Action.MemPlus -> memAdd(1)
                Action.MemMinus -> memAdd(-1)
                Action.MemClear -> memClear()
                Action.Constants -> sheet = Sheet.Constants
                Action.History -> sheet = Sheet.History
                Action.Factor -> factor()
            }
            return KeyOutcome.Handled
        }
        val text = k.insert ?: return KeyOutcome.Handled
        insert(text)
        return KeyOutcome.Inserted
    }

    /** Standalone pages keep their own digits instead of bouncing home. */
    fun shouldReturnHome(fromPage: Int, outcome: KeyOutcome): Boolean =
        outcome == KeyOutcome.Inserted && fromPage != 0 && !PAGES[fromPage].standalone
}
