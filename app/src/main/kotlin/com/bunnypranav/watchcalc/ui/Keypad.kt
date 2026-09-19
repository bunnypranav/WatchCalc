package com.bunnypranav.watchcalc.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp

private fun KeyStyle.background() = when (this) {
    KeyStyle.Digit -> KeyBg
    KeyStyle.Op -> OpBg
    KeyStyle.Fn -> FnBg
    KeyStyle.Primary -> EqBg
}

private fun KeyStyle.foreground() = when (this) {
    KeyStyle.Fn -> Accent
    KeyStyle.Primary -> Fg
    else -> Fg
}

/* Label sizes as a fraction of the screen unit. Sized to fill the key face:
   a 5-column row gives each key ~16-18u of width and 14u of height, and the
   longest label at each tier ("cosec" at Tiny) still clears its edges. */
private fun LabelSize.scale(style: KeyStyle) = when {
    style == KeyStyle.Primary && this == LabelSize.Normal -> 7.2f
    this == LabelSize.Normal -> 6.3f
    this == LabelSize.Small -> 4.6f
    else -> 3.8f
}

/**
 * One key. Tap fires the primary label; long-press fires the small grey
 * secondary in the corner, which is how the inverse trig functions and AC,
 * `^`, `%`, `,`, `E`, Ans, MC all fit into 18 slots per page.
 */
@Composable
private fun KeyButton(
    key: Key,
    u: Dp,
    modifier: Modifier,
    onPress: (Key, Boolean) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "keyScale")

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(u * KEY_RADIUS))
            .background(if (pressed) PressedBg else key.style.background())
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onPress(key, false) },
                onLongClick = key.secondary?.let { { onPress(key, true) } }
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            key.label,
            style = TextStyle(
                color = key.style.foreground(),
                fontSize = (u * key.size.scale(key.style)).asText(),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            softWrap = false
        )
        key.secondary?.let { hint ->
            BasicText(
                hint.label,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = u * 0.6f, end = u * 1f),
                style = TextStyle(color = HintFg, fontSize = (u * 2.5f).asText()),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * One page of keys. Every row is clamped to the circle's chord at its own
 * narrowest edge (see ROW_WIDTH), which is what keeps all 18 keys reachable
 * on a round display.
 */
@Composable
fun Keypad(page: Page, u: Dp, onPress: (Key, Boolean) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(u * ROW_GAP)
    ) {
        page.rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .width(u * ROW_WIDTH[index])
                    .height(u * ROW_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(u * KEY_GAP)
            ) {
                row.forEach { key ->
                    KeyButton(
                        key = key,
                        u = u,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onPress = onPress
                    )
                }
            }
        }
    }
}
