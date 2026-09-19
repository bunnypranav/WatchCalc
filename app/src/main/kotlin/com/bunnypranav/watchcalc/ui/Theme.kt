package com.bunnypranav.watchcalc.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/* Palette lifted straight from css/app.css. True black because the panel is
   OLED — black pixels are off pixels, and it blends into the physical bezel. */
val Ink = Color(0xFF000000)
val Fg = Color(0xFFF3F5F9)
val Dim = Color(0xFF7C8697)
val KeyBg = Color(0xFF16181D)
val OpBg = Color(0xFF23262E)
val FnBg = Color(0xFF141B26)
val EqBg = Color(0xFF1D4ED8)
val Accent = Color(0xFF60A5FA)
val Warn = Color(0xFFF87171)
val PressedBg = Color(0xFF3B4250)
val HintFg = Color(0xFF67707F)
val DotOff = Color(0xFF333A46)
val SheetItemBg = Color(0xFF16181D)

/*
 * The round-screen geometry, in the same units as the web app: one unit is
 * 1% of the smaller screen side. Each key row is only as wide as the circle's
 * chord at that row's narrowest edge, so nothing hides under the bezel.
 *
 *   row | vertical band | chord available | width used
 *    1  |    30-44 %    |     91.7 %      |    90 %
 *    2  |    45-59 %    |     98.4 %      |    96 %
 *    3  |    60-74 %    |     87.7 %      |    85 %
 *    4  |    75-89 %    |     62.6 %      |    60 %
 */
val ROW_WIDTH = floatArrayOf(90f, 96f, 85f, 60f)
const val ROW_HEIGHT = 14f
const val ROW_GAP = 1f
const val KEY_GAP = 1.4f
const val KEY_RADIUS = 3.2f

const val DISPLAY_HEIGHT = 30f
const val DISPLAY_PAD_TOP = 7f
const val STATUS_WIDTH = 49f
const val STATUS_HEIGHT = 4f
const val EXPR_WIDTH = 60f
const val EXPR_HEIGHT = 6f
const val RESULT_WIDTH = 73f
const val RESULT_HEIGHT = 9.5f
const val SUB_WIDTH = 86f
const val SUB_HEIGHT = 3.5f
const val PAD_HEIGHT = 59f
const val DOTS_HEIGHT = 4f

/**
 * Convert a layout Dp into an equivalent TextUnit.
 *
 * Text is sized off the same geometric unit as the keys rather than off the
 * user's font-scale setting: the layout is a circle packed to within 3 px, so
 * a system font scale of 1.3 would push keys under the bezel.
 */
@Composable
fun Dp.asText(): TextUnit = with(LocalDensity.current) { this@asText.toSp() }
