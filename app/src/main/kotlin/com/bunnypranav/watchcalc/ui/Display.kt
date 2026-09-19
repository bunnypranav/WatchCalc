package com.bunnypranav.watchcalc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.bunnypranav.watchcalc.engine.pretty
import com.bunnypranav.watchcalc.state.CalcState

/**
 * One line of right-aligned text that shrinks until it fits [width].
 *
 * The result line is only 73 units wide on a round screen, and a long
 * factorisation like 2×3×5×7×11×13×17×19×23 has to become readable rather
 * than get clipped.
 */
@Composable
private fun FitLine(
    text: String,
    width: Dp,
    height: Dp,
    maxSize: Dp,
    minSize: Dp,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val size = remember(text, width, maxSize, minSize, density) {
        val limit = with(density) { width.toPx() }
        var candidate = maxSize
        val step = maxSize * 0.03f
        var guard = 0
        while (candidate > minSize && guard++ < 60) {
            val laid = measurer.measure(
                AnnotatedString(text),
                TextStyle(
                    fontSize = with(density) { candidate.toSp() },
                    fontWeight = weight
                ),
                maxLines = 1,
                softWrap = false
            )
            if (laid.size.width <= limit) break
            candidate -= step
        }
        candidate
    }

    Box(modifier.width(width).height(height), contentAlignment = Alignment.CenterEnd) {
        BasicText(
            text,
            style = TextStyle(
                color = color,
                fontSize = size.asText(),
                fontWeight = weight,
                textAlign = TextAlign.End
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

/**
 * The top third of the watch face: mode line, the expression, the result, and
 * the exact form / divisor stats underneath. Widths shrink toward the top of
 * the circle where the chord is narrow.
 */
@Composable
fun Display(state: CalcState, u: Dp, onCyclePage: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(u * DISPLAY_HEIGHT)
            .padding(top = u * DISPLAY_PAD_TOP),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .width(u * STATUS_WIDTH)
                .height(u * STATUS_HEIGHT),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val small = TextStyle(color = Dim, fontSize = (u * 3f).asText())
            BasicText(state.angle.name, style = small, maxLines = 1)
            BasicText(
                if (state.mem != 0.0) "M" else "",
                style = small.copy(color = Accent),
                maxLines = 1
            )
            BasicText(
                PAGES[state.page].name,
                style = small.copy(color = Accent, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                modifier = Modifier.clickable(onClick = onCyclePage)
            )
        }

        FitLine(
            text = pretty(state.expr),
            width = u * EXPR_WIDTH,
            height = u * EXPR_HEIGHT,
            maxSize = u * 4.4f,
            minSize = u * 3f,
            color = Dim
        )
        FitLine(
            text = state.result,
            width = u * RESULT_WIDTH,
            height = u * RESULT_HEIGHT,
            maxSize = if (state.resultIsError) u * 5.4f else u * 7.6f,
            minSize = u * 3.2f,
            color = if (state.resultIsError) Warn else Fg,
            weight = FontWeight.Medium
        )
        FitLine(
            text = state.subLine,
            width = u * SUB_WIDTH,
            height = u * SUB_HEIGHT,
            maxSize = u * 3.4f,
            minSize = u * 2.3f,
            color = Accent
        )
    }
}

/** Page indicator dots, sized to sit inside the bottom of the circle. */
@Composable
fun PageDots(count: Int, current: Int, u: Dp) {
    Row(
        modifier = Modifier.height(u * DOTS_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(u * 1.8f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until count) {
            Box(
                Modifier
                    .width(u * 1.6f)
                    .height(u * 1.6f)
                    .clip(CircleShape)
                    .background(if (i == current) Accent else DotOff)
            )
        }
    }
}
