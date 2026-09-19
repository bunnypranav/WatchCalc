package com.bunnypranav.watchcalc.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/*
 * Output formatting, ported from js/engine.js.
 *
 * The JS original leans on two browser behaviours that have to be reproduced
 * deliberately here, because Kotlin has no equivalent:
 *
 *   Number.prototype.toPrecision(p)  -> BigDecimal(x).round(MathContext(p))
 *   String(someNumber)               -> jsNumToString() below
 *
 * Getting these subtly wrong is invisible until a result reads "0.5000000000"
 * instead of "0.5", so the unit tests pin every case against vectors produced
 * by the JS engine itself.
 */

private val SUP_DIGITS = charArrayOf('⁰', '¹', '²', '³', '⁴', '⁵', '⁶', '⁷', '⁸', '⁹')

/** "-19" -> "⁻¹⁹".  A leading "+" is dropped, matching the JS SUP table. */
fun sup(s: String): String = buildString(s.length) {
    for (c in s) when {
        c in '0'..'9' -> append(SUP_DIGITS[c - '0'])
        c == '-' -> append('⁻')
        c == '+' -> Unit
        else -> append(c)
    }
}

/** Strip trailing zeros after a decimal point, then use a real minus sign. */
private fun trimNum(input: String): String {
    var s = input
    if (s.contains('.')) {
        s = s.trimEnd('0')
        if (s.endsWith('.')) s = s.dropLast(1)
    }
    // JS String.replace with a string argument replaces the first match only
    return s.replaceFirst("-", "−")
}

/**
 * What JS `String(n)` would print for a value already rounded to <= 12
 * significant digits: plain decimal, unless the exponent falls outside
 * [-6, 21) in which case JS switches to exponential notation.
 */
private fun jsNumToString(v: BigDecimal): String {
    val b = v.stripTrailingZeros()
    if (b.signum() == 0) return "0"
    val exp10 = b.precision() - b.scale() - 1
    if (exp10 >= -6 && exp10 < 21) return b.toPlainString()
    val digits = b.unscaledValue().abs().toString()
    val mantissa =
        if (digits.length == 1) digits else digits.substring(0, 1) + "." + digits.substring(1)
    val sign = if (b.signum() < 0) "-" else ""
    return sign + mantissa + "e" + (if (exp10 < 0) "-" else "+") + abs(exp10)
}

/**
 * JS `x.toExponential(digits)`, split into mantissa and a JS-style exponent
 * (no '+' padding, no leading zeros — Java's %e gives "e+08", JS gives "e+8").
 */
private fun toExponentialParts(x: Double, digits: Int): Pair<String, String> {
    val s = String.format(Locale.ROOT, "%." + digits + "e", x)
    val i = s.indexOf('e')
    val mantissa = s.substring(0, i)
    var expPart = s.substring(i + 1)
    val negative = expPart.startsWith("-")
    expPart = expPart.trimStart('+', '-').trimStart('0')
    if (expPart.isEmpty()) expPart = "0"
    return mantissa to (if (negative) "-$expPart" else "+$expPart")
}

/** The main result formatter. [sig] is the significant-figure setting (4..12). */
fun fmt(x: Double, sig: Int = 10): String {
    if (x.isNaN()) return "Math Error"
    if (x.isInfinite()) return if (x > 0) "∞" else "−∞"
    if (x == 0.0) return "0"

    val ax = abs(x)
    if (ax >= 1e13 || ax < 1e-9) {
        val (mantissa, exponent) = toExponentialParts(x, sig - 1)
        return trimNum(mantissa) + "×10" + sup(exponent)
    }

    val rounded = BigDecimal(x).round(MathContext(sig, RoundingMode.HALF_UP))
    val s = jsNumToString(rounded)
    if (s.contains('e')) {
        val p = s.split("e")
        return trimNum(p[0]) + "×10" + sup(p[1])
    }
    return trimNum(s)
}

/* ---------------- exact / surd forms ---------------- */

/** Numerator/denominator of a continued-fraction approximation. */
private class Rat(val n: Double, val d: Double)

/**
 * Best rational approximation of [x] with denominator <= [maxDen], or null.
 * Kept in Double arithmetic rather than Long so it tracks the JS original
 * step for step.
 */
private fun cfrac(x0: Double, maxDen: Double = 4096.0, tol: Double = 1e-11): Rat? {
    if (!x0.isFinite()) return null
    val neg = x0 < 0
    val x = abs(x0)
    if (x > 1e7) return null

    var h1 = 1.0
    var h0 = 0.0
    var k1 = 0.0
    var k0 = 1.0
    var b = x
    for (i in 0 until 32) {
        val a = floor(b)
        val h2 = a * h1 + h0
        val k2 = a * k1 + k0
        if (k2 > maxDen) break
        h0 = h1; h1 = h2
        k0 = k1; k1 = k2
        if (abs(h1 / k1 - x) <= tol * maxOf(1.0, x)) {
            return Rat(if (neg) -h1 else h1, k1)
        }
        val f = b - a
        if (f < 1e-13) break
        b = 1.0 / f
    }
    return null
}

/** n -> (a, b) with n == a*a*b, b square-free. */
private fun simpRad(n: Long): Pair<Long, Long> {
    var a = 1L
    var b = n
    var f = 2L
    while (f * f <= b) {
        while (b % (f * f) == 0L) {
            b /= f * f
            a *= f
        }
        f++
    }
    return a to b
}

/**
 * A closed form for [x] when one is recognisable: a fraction, a rational
 * multiple of pi, or a surd. Null when the decimal is the best we can say.
 */
fun exact(x: Double): String? {
    if (!x.isFinite() || x == 0.0 || abs(x) > 1e6) return null

    val r = cfrac(x, 2000.0)
    if (r != null) {
        if (r.d == 1.0) return null                       // a plain integer, already shown
        if (abs(r.n) < 100000) {
            val sign = if (r.n < 0) "−" else ""
            return sign + abs(r.n).toLong() + "/" + r.d.toLong()
        }
    }

    val rp = cfrac(x / Math.PI, 400.0)
    if (rp != null && abs(rp.n) < 400) {
        val num = if (abs(rp.n) == 1.0) "" else abs(rp.n).toLong().toString()
        val sign = if (rp.n < 0) "−" else ""
        val den = if (rp.d == 1.0) "" else "/" + rp.d.toLong()
        return "$sign$num" + "π" + den
    }

    val rs = cfrac(x * x, 400.0)
    if (rs != null && rs.n > 0 && rs.n < 100000) {
        val (a0, b) = simpRad(rs.n.toLong() * rs.d.toLong())
        if (b > 1) {
            var a = a0
            var d = rs.d.toLong()
            val g = gcdLong(a, d)
            a /= g
            d /= g
            val sign = if (x < 0) "−" else ""
            val head = if (a == 1L) "" else a.toString()
            val tail = if (d == 1L) "" else "/$d"
            return "$sign$head√$b$tail"
        }
    }
    return null
}

/* ---------------- expression pretty-printing ---------------- */

private val PRETTY: List<Pair<Regex, String>> = listOf(
    Regex("asinh\\(") to "sinh⁻¹(",
    Regex("acosh\\(") to "cosh⁻¹(",
    Regex("atanh\\(") to "tanh⁻¹(",
    Regex("asin\\(") to "sin⁻¹(",
    Regex("acos\\(") to "cos⁻¹(",
    Regex("atan\\(") to "tan⁻¹(",
    Regex("asec\\(") to "sec⁻¹(",
    Regex("acsc\\(") to "cosec⁻¹(",
    Regex("acot\\(") to "cot⁻¹(",
    Regex("csc\\(") to "cosec(",
    Regex("nCr") to "C",
    Regex("nPr") to "P",
    Regex("root") to "ʸ√",
    Regex("logb") to "log_",
    Regex("mod") to " mod ",
    Regex("gcd") to " gcd ",
    Regex("lcm") to " lcm ",
    Regex("sqrt\\(") to "√(",
    Regex("cbrt\\(") to "³√(",
    Regex("\\*") to "×",
    Regex("/") to "÷",
    Regex("-") to "−"
)

/** Turn the internal expression string into something readable on a watch. */
fun pretty(src: String): String {
    var s = src
    for ((re, to) in PRETTY) s = re.replace(s, to)
    return s
}
