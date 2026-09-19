package com.bunnypranav.watchcalc

import com.bunnypranav.watchcalc.engine.Angle
import com.bunnypranav.watchcalc.engine.CalcError
import com.bunnypranav.watchcalc.engine.Env
import com.bunnypranav.watchcalc.engine.autoClose
import com.bunnypranav.watchcalc.engine.evaluate
import com.bunnypranav.watchcalc.engine.exact
import com.bunnypranav.watchcalc.engine.factorString
import com.bunnypranav.watchcalc.engine.factorize
import com.bunnypranav.watchcalc.engine.fmt
import com.bunnypranav.watchcalc.engine.pretty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Kotlin engine has to agree with the JS engine that shipped on the web,
 * character for character, on every vector in src/test/resources. Those files
 * were produced by running js/engine.js itself, so any divergence introduced
 * by the port shows up here rather than on the watch.
 */
class GoldenTest {

    private fun load(name: String): List<List<String>> {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
            ?: error("missing golden file $name")
        return stream.reader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }
            // Kotlin's split keeps trailing empty fields, unlike Java's
            .map { it.split("\t") }
    }

    @Test
    fun evaluationMatchesTheJsEngine() {
        val rows = load("golden_eval.tsv")
        assertTrue("expected a substantial suite", rows.size > 400)

        val failures = ArrayList<String>()
        for (row in rows) {
            val (mode, expr, kind) = Triple(row[0], row[1], row[2])
            val wantValue = row[3]
            val wantExact = row.getOrElse(4) { "" }

            val env = Env(Angle.valueOf(mode), ans = 7.0, mem = 3.0)
            var gotKind: String
            var gotValue: String
            var gotExact = ""
            try {
                val v = evaluate(expr, env)
                gotKind = "OK"
                gotValue = fmt(v, 10)
                gotExact = exact(v) ?: ""
            } catch (e: CalcError) {
                gotKind = "ERR"
                gotValue = e.message ?: ""
            }

            if (gotKind != kind || gotValue != wantValue || gotExact != wantExact) {
                failures += "[$mode] \"$expr\"\n" +
                    "     want $kind / $wantValue / $wantExact\n" +
                    "     got  $gotKind / $gotValue / $gotExact"
            }
        }
        assertTrue(
            "${failures.size} of ${rows.size} vectors diverged:\n" +
                failures.take(25).joinToString("\n"),
            failures.isEmpty()
        )
    }

    @Test
    fun significantFigureSettingsMatch() {
        val failures = ArrayList<String>()
        for (row in load("golden_fmt.tsv")) {
            val sig = row[0].toInt()
            val expr = row[1]
            val want = row[2]
            val got = fmt(evaluate(expr, Env()), sig)
            if (got != want) failures += "fmt(\"$expr\", $sig) want $want got $got"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun factorisationMatches() {
        val failures = ArrayList<String>()
        for (row in load("golden_factor.tsv")) {
            val n = row[0].toDouble()
            val r = factorize(n)
            if (row[1] == "NULL") {
                if (r != null) failures += "$n should not factorise"
                continue
            }
            if (r == null) {
                failures += "$n unexpectedly returned null"
                continue
            }
            val got = listOf(factorString(r.factors), "${r.d}", "${r.sigma}", "${r.phi}")
            val want = listOf(row[1], row[2], row[3], row[4])
            if (got != want) failures += "$n want $want got $got"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun prettyPrintingMatches() {
        val failures = ArrayList<String>()
        for (row in load("golden_pretty.tsv")) {
            val got = pretty(row[0])
            if (got != row[1]) failures += "pretty(\"${row[0]}\") want ${row[1]} got $got"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun bracketsCloseThemselves() {
        assertEquals("sin(30)", autoClose("sin(30"))
        assertEquals("sqrt(2+ln(5))", autoClose("sqrt(2+ln(5"))
        assertEquals("2*(3+4)", autoClose("2*(3+4)"))
        assertEquals("2+3", autoClose("2+3"))
        assertEquals("(1+2))", autoClose("(1+2))"))
        assertEquals(0.5, evaluate(autoClose("sin(30"), Env(Angle.DEG)), 1e-12)
    }

    /** Reachable via `1E400 gcd 2`; a naive port spins forever here. */
    @Test
    fun nonFiniteGcdTerminates() {
        assertEquals(2.0, evaluate("1E400gcd2", Env()), 0.0)
        assertTrue(evaluate("1E400lcm2", Env()).isInfinite())
    }

    @Test
    fun factorisationIsBounded() {
        val start = System.nanoTime()
        // largest prime below 2^53, then a hard semiprime
        assertEquals(1, factorize(9007199254740881.0)!!.factors.size)
        assertEquals("65537²", factorString(factorize(4295098369.0)!!.factors))
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took ${ms}ms, expected well under a second", ms < 1000)
    }
}
