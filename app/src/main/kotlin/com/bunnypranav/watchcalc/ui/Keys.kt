package com.bunnypranav.watchcalc.ui

/*
 * The keypads, transcribed from js/ui.js. Keys are data, not code: adding one
 * means adding a row entry. Keep rows at 5/5/5/3 or the chord widths in
 * Theme.kt stop holding.
 */

enum class Action {
    Back, Clear, Paren, Equals, Drg, Sig,
    MemPlus, MemMinus, MemClear, Constants, History, Factor
}

enum class KeyStyle { Digit, Op, Fn, Primary }

/** How large the label is drawn; long labels like "cosec" need the small ones. */
enum class LabelSize { Normal, Small, Tiny }

data class Key(
    val label: String,
    val insert: String? = null,
    val action: Action? = null,
    val secondary: Key? = null,
    val style: KeyStyle = KeyStyle.Digit,
    val size: LabelSize = LabelSize.Normal
)

data class Page(
    val name: String,
    val rows: List<List<Key>>,
    /** A standalone page keeps its own digits instead of hopping back to 123. */
    val standalone: Boolean = false
)

private fun d(label: String) = Key(label, insert = label)

private val BACK = Key(
    "⌫", action = Action.Back,
    secondary = Key("AC", action = Action.Clear), style = KeyStyle.Op
)
private val PAREN = Key(
    "( )", action = Action.Paren,
    secondary = Key(",", insert = ","), style = KeyStyle.Op
)
private val EQUALS = Key("=", action = Action.Equals, style = KeyStyle.Primary)

val PAGES: List<Page> = listOf(
    Page(
        "123", listOf(
            listOf(
                d("7"), d("8"), d("9"),
                Key("÷", "/", secondary = Key("%", "%"), style = KeyStyle.Op),
                BACK
            ),
            listOf(
                d("4"), d("5"), d("6"),
                Key("×", "*", secondary = Key("^", "^"), style = KeyStyle.Op),
                PAREN
            ),
            listOf(
                d("1"), d("2"), d("3"),
                Key("−", "-", secondary = Key("Ans", "Ans"), style = KeyStyle.Op),
                Key("+", "+", style = KeyStyle.Op)
            ),
            listOf(
                d("0"),
                Key(".", ".", secondary = Key("E", "E")),
                EQUALS
            )
        )
    ),

    Page(
        "log", listOf(
            listOf(
                Key("√", "sqrt(", style = KeyStyle.Fn),
                Key("x²", "^2", style = KeyStyle.Fn),
                Key("x³", "^3", style = KeyStyle.Fn),
                Key("xʸ", "^", style = KeyStyle.Fn),
                BACK
            ),
            listOf(
                Key("³√", "cbrt(", style = KeyStyle.Fn),
                Key("ln", "ln(", style = KeyStyle.Fn),
                Key(
                    "log", "log(", secondary = Key("log₂", "log2("),
                    style = KeyStyle.Fn, size = LabelSize.Small
                ),
                Key("10ˣ", "10^(", style = KeyStyle.Fn, size = LabelSize.Small),
                PAREN
            ),
            listOf(
                Key("eˣ", "exp(", style = KeyStyle.Fn),
                Key("x⁻¹", "^(-1)", style = KeyStyle.Fn, size = LabelSize.Small),
                Key("|x|", "abs(", style = KeyStyle.Fn),
                Key("logᵇ", "logb", style = KeyStyle.Fn, size = LabelSize.Tiny),
                Key("ʸ√", "root", style = KeyStyle.Fn)
            ),
            listOf(
                Key("π", "π", style = KeyStyle.Fn),
                Key("e", "e", style = KeyStyle.Fn),
                EQUALS
            )
        )
    ),

    Page(
        "trig", listOf(
            listOf(
                Key("sin", "sin(", secondary = Key("sin⁻¹", "asin("), style = KeyStyle.Fn, size = LabelSize.Small),
                Key("cos", "cos(", secondary = Key("cos⁻¹", "acos("), style = KeyStyle.Fn, size = LabelSize.Small),
                Key("tan", "tan(", secondary = Key("tan⁻¹", "atan("), style = KeyStyle.Fn, size = LabelSize.Small),
                Key("DRG", action = Action.Drg, style = KeyStyle.Fn, size = LabelSize.Small),
                BACK
            ),
            listOf(
                Key("sec", "sec(", secondary = Key("sec⁻¹", "asec("), style = KeyStyle.Fn, size = LabelSize.Small),
                Key("cosec", "csc(", secondary = Key("cosec⁻¹", "acsc("), style = KeyStyle.Fn, size = LabelSize.Tiny),
                Key("cot", "cot(", secondary = Key("cot⁻¹", "acot("), style = KeyStyle.Fn, size = LabelSize.Small),
                Key("π", "π", style = KeyStyle.Fn),
                PAREN
            ),
            listOf(
                Key("sinh", "sinh(", secondary = Key("sinh⁻¹", "asinh("), style = KeyStyle.Fn, size = LabelSize.Tiny),
                Key("cosh", "cosh(", secondary = Key("cosh⁻¹", "acosh("), style = KeyStyle.Fn, size = LabelSize.Tiny),
                Key("tanh", "tanh(", secondary = Key("tanh⁻¹", "atanh("), style = KeyStyle.Fn, size = LabelSize.Tiny),
                Key("D→R", "rad(", style = KeyStyle.Fn, size = LabelSize.Tiny),
                Key("R→D", "deg(", style = KeyStyle.Fn, size = LabelSize.Tiny)
            ),
            listOf(
                Key("Ans", "Ans", style = KeyStyle.Fn, size = LabelSize.Small),
                Key("xʸ", "^", style = KeyStyle.Op),
                EQUALS
            )
        )
    ),

    Page(
        "more", listOf(
            listOf(
                Key("nCr", "nCr", style = KeyStyle.Fn, size = LabelSize.Tiny),
                Key("nPr", "nPr", style = KeyStyle.Fn, size = LabelSize.Tiny),
                Key("n!", "!", style = KeyStyle.Fn),
                Key("%", "%", style = KeyStyle.Fn),
                BACK
            ),
            listOf(
                Key("mod", "mod", style = KeyStyle.Fn, size = LabelSize.Tiny),
                Key("gcd", "gcd", style = KeyStyle.Fn, size = LabelSize.Tiny),
                Key("lcm", "lcm", style = KeyStyle.Fn, size = LabelSize.Tiny),
                Key("E", "E", style = KeyStyle.Fn),
                PAREN
            ),
            listOf(
                Key("CONST", action = Action.Constants, style = KeyStyle.Fn, size = LabelSize.Tiny),
                Key("HIST", action = Action.History, style = KeyStyle.Fn, size = LabelSize.Tiny),
                Key("DRG", action = Action.Drg, style = KeyStyle.Fn, size = LabelSize.Tiny),
                Key(
                    "M+", action = Action.MemPlus,
                    secondary = Key("M−", action = Action.MemMinus),
                    style = KeyStyle.Fn, size = LabelSize.Small
                ),
                Key(
                    "MR", "M", secondary = Key("MC", action = Action.MemClear),
                    style = KeyStyle.Fn, size = LabelSize.Small
                )
            ),
            listOf(
                Key("Ans", "Ans", style = KeyStyle.Fn, size = LabelSize.Small),
                Key("SIG", action = Action.Sig, style = KeyStyle.Fn, size = LabelSize.Small),
                EQUALS
            )
        )
    ),

    // Its own number pad and a full-width FACTOR bar. Standalone, so typing a
    // digit here does not bounce you back to the main keypad.
    Page(
        "prime", standalone = true, rows = listOf(
            listOf(
                d("7"), d("8"), d("9"),
                Key("00", "00", style = KeyStyle.Op),
                BACK
            ),
            listOf(
                d("4"), d("5"), d("6"),
                Key("000", "000", style = KeyStyle.Op, size = LabelSize.Small),
                Key("AC", action = Action.Clear, style = KeyStyle.Op, size = LabelSize.Small)
            ),
            listOf(
                d("1"), d("2"), d("3"), d("0"),
                Key("Ans", "Ans", style = KeyStyle.Fn, size = LabelSize.Small)
            ),
            listOf(
                Key("FACTOR", action = Action.Factor, style = KeyStyle.Primary, size = LabelSize.Small)
            )
        )
    )
)
