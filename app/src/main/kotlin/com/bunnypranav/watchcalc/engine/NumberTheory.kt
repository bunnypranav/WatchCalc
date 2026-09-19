package com.bunnypranav.watchcalc.engine

import java.math.BigInteger
import kotlin.math.abs

/*
 * The prime-factorisation page, ported from js/engine.js.
 *
 * Trial division by a mod-30 wheel up to 10^5 fully factors anything below
 * 10^10. Past that the leftover is split with Miller-Rabin + Pollard rho.
 * Every loop is bounded, so the watch can never be left spinning.
 */

data class Factorisation(
    val factors: List<Pair<Long, Int>>,
    val d: Long,
    val sigma: Long,
    val phi: Long
)

internal fun gcdLong(x: Long, y: Long): Long {
    var a = abs(x)
    var b = abs(y)
    while (b != 0L) {
        val t = a % b
        a = b
        b = t
    }
    return a
}

private val MR_BASES = longArrayOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37)

/**
 * (a*b) mod m for a, b up to 2^53. The product overflows a Long, so it goes
 * through BigInteger. This is the rare path — trial division has already
 * removed every small factor — so the allocation cost never shows.
 */
private fun mulmod(a: Long, b: Long, m: Long): Long =
    BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).mod(BigInteger.valueOf(m)).toLong()

private fun powmod(a: Long, e: Long, m: Long): Long {
    val mod = BigInteger.valueOf(m)
    var result = BigInteger.ONE
    var base = BigInteger.valueOf(a).mod(mod)
    var exp = e
    while (exp > 0) {
        if (exp and 1L == 1L) result = result.multiply(base).mod(mod)
        base = base.multiply(base).mod(mod)
        exp = exp shr 1
    }
    return result.toLong()
}

/** Deterministic Miller-Rabin: these 12 bases are exact below 3.3*10^24. */
fun isPrime(n: Long): Boolean {
    if (n < 2) return false
    for (b in MR_BASES) {
        if (n == b) return true
        if (n % b == 0L) return false
    }
    if (n < 41L * 41L) return true

    var d = n - 1
    var r = 0
    while (d % 2 == 0L) {
        d /= 2
        r++
    }
    for (b in MR_BASES) {
        var x = powmod(b, d, n)
        if (x == 1L || x == n - 1) continue
        var witness = true
        for (j in 1 until r) {
            x = mulmod(x, x, n)
            if (x == n - 1) {
                witness = false
                break
            }
        }
        if (witness) return false
    }
    return true
}

/** Pollard rho. Only ever called on a known composite; 0 means "gave up". */
private fun pollard(n: Long): Long {
    if (n % 2 == 0L) return 2
    for (c in 1..99) {
        var x = 2L
        var y = 2L
        var d = 1L
        var guard = 0
        while (guard < 2_000_000 && d == 1L) {
            x = (mulmod(x, x, n) + c) % n
            y = (mulmod(y, y, n) + c) % n
            y = (mulmod(y, y, n) + c) % n
            d = gcdLong(abs(x - y), n)
            guard++
        }
        if (d != 1L && d != n) return d
    }
    return 0
}

private val WHEEL = intArrayOf(4, 2, 4, 2, 4, 6, 2, 6)

/** Factorise [x]; null when it is not a whole number in [1, 2^53-1]. */
fun factorize(x: Double): Factorisation? {
    if (!isInt(x) || x < 1 || x > 9007199254740991.0) return null

    val counts = HashMap<Long, Int>()
    fun add(p: Long) {
        counts[p] = (counts[p] ?: 0) + 1
    }

    var m = x.toLong()
    for (p in longArrayOf(2, 3, 5)) while (m % p == 0L) {
        add(p)
        m /= p
    }

    var f = 7L
    var k = 0
    while (f <= 100_000L && f * f <= m) {
        if (m % f == 0L) while (m % f == 0L) {
            add(f)
            m /= f
        }
        f += WHEEL[k]
        k = (k + 1) % 8
    }

    // whatever survived is 1, a prime, or (only for huge n) still composite
    val stack = ArrayList<Long>()
    if (m > 1) stack.add(m)
    while (stack.isNotEmpty()) {
        val v = stack.removeAt(stack.size - 1)
        if (v == 1L) continue
        if (v <= 10_000_000_000L || isPrime(v)) {
            add(v)
            continue
        }
        val g = pollard(v)
        if (g == 0L) {
            add(v)
            continue
        }
        stack.add(g)
        stack.add(v / g)
    }

    val factors = ArrayList<Pair<Long, Int>>(counts.size)
    var d = 1L
    var sigma = 1L
    var phi = 1L
    for (p in counts.keys.sorted()) {
        val e = counts.getValue(p)
        factors.add(p to e)
        d *= (e + 1)
        // accumulate 1 + p + ... + p^e term by term; the closed form
        // (p^(e+1) - 1)/(p - 1) loses precision for a prime near 2^53
        var term = 1L
        var pk = 1L
        for (j in 0 until e) {
            pk *= p
            term += pk
        }
        sigma *= term
        phi *= (pk / p) * (p - 1)
    }
    return Factorisation(factors, d, sigma, phi)
}

/** [[2,3],[3,2],[5,1]] -> "2³×3²×5" */
fun factorString(factors: List<Pair<Long, Int>>): String =
    factors.joinToString("×") { (p, e) -> if (e > 1) "$p${sup(e.toString())}" else "$p" }
