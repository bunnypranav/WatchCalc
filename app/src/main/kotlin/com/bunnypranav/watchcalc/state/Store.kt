package com.bunnypranav.watchcalc.state

import com.bunnypranav.watchcalc.engine.Angle

/**
 * Everything the calculator persists. Behind an interface so [CalcState] has
 * no Android dependency and its behaviour can be unit-tested on the JVM.
 */
interface Store {
    var expr: String
    var page: Int
    var angle: Angle
    var ans: Double
    var mem: Double
    var sig: Int
    var history: List<HistEntry>
}

/** In-memory store, used by tests and as a safe default. */
class MemoryStore : Store {
    override var expr: String = ""
    override var page: Int = 0
    override var angle: Angle = Angle.DEG
    override var ans: Double = 0.0
    override var mem: Double = 0.0
    override var sig: Int = 10
    override var history: List<HistEntry> = emptyList()
}
