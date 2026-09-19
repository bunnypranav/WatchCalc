package com.bunnypranav.watchcalc.state

import android.content.Context
import com.bunnypranav.watchcalc.engine.Angle

/** One history row: the expression as typed, and its formatted result. */
data class HistEntry(val expr: String, val result: String)

/**
 * SharedPreferences-backed persistence. Everything is saved, including the
 * in-progress expression and current page: Wear OS kills background apps
 * aggressively, and coming back to a half-typed expression is the difference
 * between a calculator and a toy.
 */
class Prefs(context: Context) : Store {

    private val sp = context.getSharedPreferences("watchcalc", Context.MODE_PRIVATE)

    companion object {
        private val FIELD_SEP = Char(1).toString()
        private val ENTRY_SEP = Char(2).toString()
    }

    override var expr: String
        get() = sp.getString("expr", "") ?: ""
        set(v) = sp.edit().putString("expr", v).apply()

    override var page: Int
        get() = sp.getInt("page", 0)
        set(v) = sp.edit().putInt("page", v).apply()

    override var angle: Angle
        get() = runCatching { Angle.valueOf(sp.getString("angle", "DEG")!!) }
            .getOrDefault(Angle.DEG)
        set(v) = sp.edit().putString("angle", v.name).apply()

    // Ans and M go in as strings: SharedPreferences has no double, and a
    // float would quietly truncate a 15-digit result.
    override var ans: Double
        get() = sp.getString("ans", null)?.toDoubleOrNull() ?: 0.0
        set(v) = sp.edit().putString("ans", v.toString()).apply()

    override var mem: Double
        get() = sp.getString("mem", null)?.toDoubleOrNull() ?: 0.0
        set(v) = sp.edit().putString("mem", v.toString()).apply()

    override var sig: Int
        get() = sp.getInt("sig", 10)
        set(v) = sp.edit().putInt("sig", v).apply()

    override var history: List<HistEntry>
        get() {
            val raw = sp.getString("hist", "") ?: ""
            if (raw.isEmpty()) return emptyList()
            return raw.split(ENTRY_SEP).mapNotNull {
                val parts = it.split(FIELD_SEP)
                if (parts.size == 2) HistEntry(parts[0], parts[1]) else null
            }
        }
        set(v) {
            val raw = v.take(MAX_HISTORY)
                .joinToString(ENTRY_SEP) { it.expr + FIELD_SEP + it.result }
            sp.edit().putString("hist", raw).apply()
        }
}
