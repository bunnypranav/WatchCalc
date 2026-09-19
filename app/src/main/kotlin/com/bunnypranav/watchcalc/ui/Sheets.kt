package com.bunnypranav.watchcalc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.bunnypranav.watchcalc.engine.CONST_LIST
import com.bunnypranav.watchcalc.engine.fmt
import com.bunnypranav.watchcalc.engine.pretty
import com.bunnypranav.watchcalc.state.HistEntry

/*
 * The web version had to hand-roll a curved list, narrowing each row to the
 * circle's chord at its scroll position. ScalingLazyColumn does exactly that
 * natively, so here it comes for free.
 */

@Composable
private fun SheetFrame(
    title: String,
    u: Dp,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        content()
        // Scrims so rows scrolling past the title and the close pill fade out
        // instead of colliding with them.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(u * 20f)
                .background(Brush.verticalGradient(0f to Ink, 0.72f to Ink, 1f to Transparent))
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(u * 20f)
                .background(Brush.verticalGradient(0f to Transparent, 0.45f to Ink, 1f to Ink))
        )
        // title sits over the narrow top of the circle
        BasicText(
            title,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = u * 8f),
            style = TextStyle(
                color = Dim,
                fontSize = (u * 4f).asText(),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = u * 4f)
                .width(u * 34f)
                .height(u * 12f)
                .clip(RoundedCornerShape(u * 6f))
                .background(OpBg)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                "close",
                style = TextStyle(color = Fg, fontSize = (u * 4.6f).asText())
            )
        }
    }
}

@Composable
private fun rowShape(u: Dp) = RoundedCornerShape(u * KEY_RADIUS)

/** Tap a constant to drop its symbol into the expression. */
@Composable
fun ConstantsSheet(u: Dp, onPick: (String) -> Unit, onClose: () -> Unit) {
    SheetFrame("CONSTANTS", u, onClose) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberScalingLazyListState(),
            contentPadding = PaddingValues(
                horizontal = u * 6f,
                vertical = u * 26f
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CONST_LIST.forEach { c ->
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(rowShape(u))
                            .background(SheetItemBg)
                            .clickable { onPick(c.symbol) }
                            .padding(horizontal = u * 3f, vertical = u * 2.6f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText(
                                c.symbol,
                                style = TextStyle(color = Accent, fontSize = (u * 5f).asText()),
                                maxLines = 1
                            )
                            BasicText(
                                "  " + fmt(c.value, 12),
                                style = TextStyle(color = Fg, fontSize = (u * 3.6f).asText()),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
                        BasicText(
                            c.label,
                            style = TextStyle(color = Dim, fontSize = (u * 3f).asText()),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** Tap a past calculation to paste it back in as one operand. */
@Composable
fun HistorySheet(
    u: Dp,
    history: List<HistEntry>,
    onPick: (HistEntry) -> Unit,
    onClose: () -> Unit
) {
    SheetFrame("HISTORY", u, onClose) {
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText(
                    "no history yet",
                    style = TextStyle(color = Dim, fontSize = (u * 3.6f).asText())
                )
            }
            return@SheetFrame
        }
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberScalingLazyListState(),
            contentPadding = PaddingValues(
                horizontal = u * 6f,
                vertical = u * 26f
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(u * 1.6f)
        ) {
            history.forEach { entry ->
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(rowShape(u))
                            .background(SheetItemBg)
                            .clickable { onPick(entry) }
                            .padding(horizontal = u * 3f, vertical = u * 2.6f)
                    ) {
                        BasicText(
                            pretty(entry.expr),
                            style = TextStyle(color = Dim, fontSize = (u * 3.4f).asText()),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        BasicText(
                            entry.result,
                            modifier = Modifier.fillMaxWidth(),
                            style = TextStyle(
                                color = Fg,
                                fontSize = (u * 5f).asText(),
                                textAlign = TextAlign.End
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }
}
