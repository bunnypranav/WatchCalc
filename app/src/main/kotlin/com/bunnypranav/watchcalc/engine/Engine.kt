package com.bunnypranav.watchcalc.engine

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.acosh
import kotlin.math.asin
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.atanh
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/*
 * Tokenizer + shunting-yard + RPN evaluator, ported from js/engine.js.
 *
 * Two deliberate quirks are preserved because the whole point is that this
 * behaves like the calculator the user already trained on:
 *
 *   - implicit multiplication binds tighter than explicit * and /, so
 *     1/2pi == 1/(2pi), the way a Casio fx-991 reads it;
 *   - a two-argument function written between its operands (10nCr3) is an
 *     infix operator sitting between x/÷ and implicit multiplication.
 */

enum class Angle { DEG, RAD, GRA }

/** Evaluation environment: angle mode plus the Ans and M registers. */
class Env(
    var angle: Angle = Angle.DEG,
    var ans: Double = 0.0,
    var mem: Double = 0.0
)

class CalcError(message: String) : Exception(message)

private fun err(msg: String): Nothing = throw CalcError(msg)

internal fun isInt(x: Double) = x.isFinite() && floor(x) == x

/* ---------------- special functions ---------------- */

private fun gamma(z0: Double): Double {
    val p = doubleArrayOf(
        676.5203681218851, -1259.1392167224028, 771.32342877765313,
        -176.61502916214059, 12.507343278686905, -0.13857109526572012,
        9.9843695780195716e-6, 1.5056327351493116e-7
    )
    if (z0 < 0.5) return Math.PI / (sin(Math.PI * z0) * gamma(1 - z0))
    val z = z0 - 1
    var x = 0.99999999999980993
    for (i in 0 until 8) x += p[i] / (z + i + 1)
    val t = z + 7.5
    return sqrt(2 * Math.PI) * t.pow(z + 0.5) * exp(-t) * x
}

internal fun factorial(n: Double): Double {
    if (isInt(n)) {
        if (n < 0) err("Math Error")
        if (n > 170) return Double.POSITIVE_INFINITY
        var r = 1.0
        var i = 2.0
        while (i <= n) { r *= i; i++ }
        return r
    }
    if (n < 0) err("Math Error")
    return gamma(n + 1)
}

/**
 * Euclid on Doubles. The NaN guard mirrors JS `while (b)`, where a NaN
 * loop variable is falsy and quietly ends the loop; a plain `b != 0.0`
 * here would spin forever on e.g. `1E400 gcd 2`.
 */
private fun gcdD(a0: Double, b0: Double): Double {
    var a = abs(a0)
    var b = abs(b0)
    while (b != 0.0 && !b.isNaN()) {
        val t = a % b
        a = b
        b = t
    }
    return a
}

private fun nPr(n: Double, r: Double): Double {
    if (!isInt(n) || !isInt(r) || r < 0 || n < 0 || r > n) err("Math Error")
    var p = 1.0
    var i = 0
    while (i < r) { p *= (n - i); i++ }
    return p
}

private fun nCr(n: Double, r0: Double): Double {
    if (!isInt(n) || !isInt(r0) || r0 < 0 || n < 0 || r0 > n) err("Math Error")
    val r = minOf(r0, n - r0)
    var c = 1.0
    var i = 1
    while (i <= r) { c = c * (n - r + i) / i; i++ }
    return if (c < 9e15) Math.round(c).toDouble() else c
}

/* ---------------- angle handling ---------------- */

private fun toRad(x: Double, env: Env): Double = when (env.angle) {
    Angle.DEG -> x * Math.PI / 180
    Angle.GRA -> x * Math.PI / 200
    Angle.RAD -> x
}

private fun fromRad(x: Double, env: Env): Double = when (env.angle) {
    Angle.DEG -> x * 180 / Math.PI
    Angle.GRA -> x * 200 / Math.PI
    Angle.RAD -> x
}

private val SIN_Q = doubleArrayOf(0.0, 1.0, 0.0, -1.0)
private val COS_Q = doubleArrayOf(1.0, 0.0, -1.0, 0.0)

/** Exact at quadrant boundaries, so sin 180 is 0 and not 1.2e-16. */
private fun trig(name: String, x: Double, env: Env): Double {
    if (env.angle != Angle.RAD) {
        val per = if (env.angle == Angle.DEG) 360.0 else 400.0
        val q = if (env.angle == Angle.DEG) 90.0 else 100.0
        val r = x % per
        if (abs(r % q) < 1e-11) {
            val k = (((Math.round(r / q) % 4L) + 4L) % 4L).toInt()
            when (name) {
                "sin" -> return SIN_Q[k]
                "cos" -> return COS_Q[k]
                else -> {
                    if (k == 1 || k == 3) err("Math Error")
                    return 0.0
                }
            }
        }
    }
    val v = when (name) {
        "sin" -> sin(toRad(x, env))
        "cos" -> cos(toRad(x, env))
        else -> tan(toRad(x, env))
    }
    return if (env.angle != Angle.RAD && abs(v) < 1e-12) 0.0 else v
}

/* ---------------- function tables ---------------- */

private val FN1: Map<String, (Double, Env) -> Double> = mapOf(
    "sin" to { x, e -> trig("sin", x, e) },
    "cos" to { x, e -> trig("cos", x, e) },
    "tan" to { x, e -> trig("tan", x, e) },
    "asin" to { x, e -> if (x < -1 || x > 1) err("Math Error") else fromRad(asin(x), e) },
    "acos" to { x, e -> if (x < -1 || x > 1) err("Math Error") else fromRad(acos(x), e) },
    "atan" to { x, e -> fromRad(atan(x), e) },
    // reciprocal ratios, derived from sin/cos so poles and snapping agree
    "sec" to { x, e -> val c = trig("cos", x, e); if (c == 0.0) err("Math Error") else 1 / c },
    "csc" to { x, e -> val s = trig("sin", x, e); if (s == 0.0) err("Math Error") else 1 / s },
    "cot" to { x, e ->
        val s = trig("sin", x, e)
        if (s == 0.0) err("Math Error") else trig("cos", x, e) / s
    },
    // NCERT principal ranges: sec⁻¹ [0,π], cosec⁻¹ [−π/2,π/2], cot⁻¹ (0,π)
    "asec" to { x, e -> if (abs(x) < 1) err("Math Error") else fromRad(acos(1 / x), e) },
    "acsc" to { x, e -> if (abs(x) < 1) err("Math Error") else fromRad(asin(1 / x), e) },
    "acot" to { x, e ->
        val r = when {
            x == 0.0 -> Math.PI / 2
            x > 0 -> atan(1 / x)
            else -> Math.PI + atan(1 / x)
        }
        fromRad(r, e)
    },
    "sinh" to { x, _ -> sinh(x) },
    "cosh" to { x, _ -> cosh(x) },
    "tanh" to { x, _ -> tanh(x) },
    "asinh" to { x, _ -> asinh(x) },
    "acosh" to { x, _ -> if (x < 1) err("Math Error") else acosh(x) },
    "atanh" to { x, _ -> if (x <= -1 || x >= 1) err("Math Error") else atanh(x) },
    "ln" to { x, _ -> if (x <= 0) err("Math Error") else ln(x) },
    "log" to { x, _ -> if (x <= 0) err("Math Error") else ln(x) / 2.302585092994046 },
    "log2" to { x, _ -> if (x <= 0) err("Math Error") else ln(x) / 0.6931471805599453 },
    "sqrt" to { x, _ -> if (x < 0) err("Math Error") else sqrt(x) },
    "cbrt" to { x, _ -> cbrt(x) },
    "abs" to { x, _ -> abs(x) },
    "exp" to { x, _ -> exp(x) },
    "sign" to { x, _ -> sign(x) },
    "floor" to { x, _ -> floor(x) },
    "ceil" to { x, _ -> ceil(x) },
    "round" to { x, _ -> Math.floor(x + 0.5) },
    "deg" to { x, _ -> x * 180 / Math.PI },
    "rad" to { x, _ -> x * Math.PI / 180 },
    "fact" to { x, _ -> factorial(x) }
)

private val FN2: Map<String, (Double, Double) -> Double> = mapOf(
    "nCr" to { a, b -> nCr(a, b) },
    "nPr" to { a, b -> nPr(a, b) },
    "mod" to { a, b -> if (b == 0.0) err("Math Error") else a % b },
    "gcd" to { a, b -> gcdD(a, b) },
    "lcm" to { a, b ->
        val d = gcdD(a, b)
        if (d != 0.0 && !d.isNaN()) abs(a * b) / d else 0.0
    },
    "logb" to { b, x ->
        if (x <= 0 || b <= 0 || b == 1.0) err("Math Error") else ln(x) / ln(b)
    },
    "root" to { n, x ->
        when {
            n == 0.0 -> err("Math Error")
            x < 0 -> if (isInt(n) && abs(n % 2) == 1.0) -((-x).pow(1 / n)) else err("Math Error")
            else -> x.pow(1 / n)
        }
    },
    "max" to { a, b -> maxOf(a, b) },
    "min" to { a, b -> minOf(a, b) }
)

/** Every identifier the tokenizer knows, longest first so "exp" beats "e". */
private val NAMES: List<String> =
    (FN1.keys + FN2.keys + CONSTS.keys + listOf("Ans", "M"))
        .sortedByDescending { it.length }

/* ---------------- tokenizer ---------------- */

private sealed interface Tok
private data class TNum(val v: Double) : Tok
private data class TF1(val name: String) : Tok
private data class TF2(val name: String) : Tok
private data class TOp(val v: String) : Tok
private data class TPost(val v: String) : Tok
private data object TLParen : Tok
private data object TRParen : Tok
private data object TComma : Tok

private val NUM_RE = Regex("""(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?""")

private fun tokenize(src: String, env: Env): List<Tok> {
    val out = ArrayList<Tok>()
    var i = 0
    while (i < src.length) {
        val ch = src[i]
        if (ch == ' ') { i++; continue }

        val m = NUM_RE.matchAt(src, i)
        if (m != null) {
            out.add(TNum(m.value.toDouble()))
            i += m.value.length
            continue
        }

        val name = NAMES.firstOrNull { src.startsWith(it, i) }
        if (name != null) {
            when {
                FN1.containsKey(name) -> out.add(TF1(name))
                FN2.containsKey(name) -> out.add(TF2(name))
                name == "Ans" -> out.add(TNum(env.ans))
                name == "M" -> out.add(TNum(env.mem))
                else -> out.add(TNum(CONSTS.getValue(name)))
            }
            i += name.length
            continue
        }

        when (ch) {
            '×' -> out.add(TOp("*"))
            '÷' -> out.add(TOp("/"))
            '−' -> out.add(TOp("-"))
            '+', '-', '*', '/', '^' -> out.add(TOp(ch.toString()))
            '(' -> out.add(TLParen)
            ')' -> out.add(TRParen)
            ',' -> out.add(TComma)
            '!', '%' -> out.add(TPost(ch.toString()))
            else -> err("Syntax Error")
        }
        i++
    }
    return out
}

/* ---------------- shunting-yard ---------------- */

private val PREC = mapOf(
    "+" to 2.0, "-" to 2.0,
    "*" to 3.0, "/" to 3.0,
    "IMUL" to 4.0,
    "u-" to 5.0,
    "^" to 6.0
)
private val RIGHT = setOf("u-", "^")
private const val INFIX = 3.5

private sealed interface Rpn
private data class RNum(val v: Double) : Rpn
private data class RFn(val name: String) : Rpn
private data class ROp(val v: String) : Rpn
private data class RPost(val v: String) : Rpn

private enum class Prev { NONE, VALUE, FUNC, OPEN, COMMA, OP }

private fun toRpn(toks: List<Tok>): List<Rpn> {
    val out = ArrayList<Rpn>()
    val st = ArrayList<String>()
    var prev = Prev.NONE

    /** -1 means "not an operator": a '(' or a pending function call. */
    fun precOf(t: String): Double = when {
        t == "(" -> -1.0
        t.startsWith("@") -> INFIX
        FN1.containsKey(t) || FN2.containsKey(t) -> -1.0
        else -> PREC.getValue(t)
    }

    fun emit(o: String): Rpn = when {
        o.startsWith("@") -> RFn(o.substring(1))
        FN1.containsKey(o) || FN2.containsKey(o) -> RFn(o)
        else -> ROp(o)
    }

    fun popWhile(p: Double, right: Boolean) {
        while (st.isNotEmpty()) {
            val tp = precOf(st.last())
            if (tp < 0) break
            if (tp > p || (tp == p && !right)) out.add(emit(st.removeAt(st.size - 1)))
            else break
        }
    }

    fun imul() {
        popWhile(PREC.getValue("IMUL"), false)
        st.add("IMUL")
    }

    for (tk in toks) {
        when {
            tk is TNum -> {
                if (prev == Prev.VALUE) imul()
                out.add(RNum(tk.v)); prev = Prev.VALUE
            }

            // a two-argument function between its operands: 10nCr3
            tk is TF2 && prev == Prev.VALUE -> {
                popWhile(INFIX, false)
                st.add("@" + tk.name); prev = Prev.OP
            }

            tk is TF1 || tk is TF2 -> {
                if (prev == Prev.VALUE) imul()
                st.add(if (tk is TF1) tk.name else (tk as TF2).name)
                prev = Prev.FUNC
            }

            tk is TLParen -> {
                if (prev == Prev.VALUE) imul()
                st.add("("); prev = Prev.OPEN
            }

            tk is TRParen -> {
                while (st.isNotEmpty() && st.last() != "(") out.add(emit(st.removeAt(st.size - 1)))
                if (st.isEmpty()) err("Syntax Error")
                st.removeAt(st.size - 1)
                val top = st.lastOrNull()
                if (top != null && (FN1.containsKey(top) || FN2.containsKey(top))) {
                    out.add(RFn(st.removeAt(st.size - 1)))
                }
                prev = Prev.VALUE
            }

            tk is TComma -> {
                while (st.isNotEmpty() && st.last() != "(") out.add(emit(st.removeAt(st.size - 1)))
                if (st.isEmpty()) err("Syntax Error")
                prev = Prev.COMMA
            }

            tk is TOp -> {
                val unary = prev == Prev.NONE || prev == Prev.OPEN ||
                    prev == Prev.COMMA || prev == Prev.OP
                if (unary) {
                    when (tk.v) {
                        "-" -> st.add("u-")
                        "+" -> Unit
                        else -> err("Syntax Error")
                    }
                } else {
                    popWhile(PREC.getValue(tk.v), tk.v in RIGHT)
                    st.add(tk.v)
                }
                prev = Prev.OP
            }

            tk is TPost -> {
                if (prev != Prev.VALUE) err("Syntax Error")
                out.add(RPost(tk.v)); prev = Prev.VALUE
            }
        }
    }

    while (st.isNotEmpty()) {
        val o = st.removeAt(st.size - 1)
        if (o == "(") err("Syntax Error")
        out.add(emit(o))
    }
    if (out.isEmpty()) err("Syntax Error")
    return out
}

/* ---------------- evaluation ---------------- */

private fun evalRpn(rpn: List<Rpn>, env: Env): Double {
    val s = ArrayList<Double>()
    fun pop(): Double {
        if (s.isEmpty()) err("Syntax Error")
        return s.removeAt(s.size - 1)
    }

    for (tk in rpn) {
        when (tk) {
            is RNum -> s.add(tk.v)
            is RPost -> {
                val a = pop()
                s.add(if (tk.v == "!") factorial(a) else a / 100)
            }
            is RFn -> {
                val f1 = FN1[tk.name]
                if (f1 != null) {
                    s.add(f1(pop(), env))
                } else {
                    val b = pop(); val a = pop()
                    s.add(FN2.getValue(tk.name)(a, b))
                }
            }
            is ROp -> when (tk.v) {
                "u-" -> s.add(-pop())
                "+" -> { val b = pop(); val a = pop(); s.add(a + b) }
                "-" -> { val b = pop(); val a = pop(); s.add(a - b) }
                "*", "IMUL" -> { val b = pop(); val a = pop(); s.add(a * b) }
                "/" -> {
                    val b = pop(); val a = pop()
                    if (b == 0.0) err("Divide by 0")
                    s.add(a / b)
                }
                "^" -> {
                    val b = pop(); val a = pop()
                    if (a < 0 && !isInt(b)) err("Math Error")
                    s.add(a.pow(b))
                }
                else -> err("Syntax Error")
            }
        }
    }
    if (s.size != 1) err("Syntax Error")
    val r = s[0]
    if (r.isNaN()) err("Math Error")
    return r
}

/** Evaluate [src] in [env]. Throws [CalcError] with a display-ready message. */
fun evaluate(src: String, env: Env = Env()): Double = evalRpn(toRpn(tokenize(src, env)), env)

/**
 * Append whatever closing brackets are still outstanding. "sin(30" is a
 * complete thought on a watch; spending a keypress on the ")" is not.
 */
fun autoClose(expr: String): String {
    var open = 0
    for (c in expr) {
        if (c == '(') open++
        else if (c == ')') open--
    }
    if (open <= 0) return expr
    return expr + ")".repeat(open)
}
