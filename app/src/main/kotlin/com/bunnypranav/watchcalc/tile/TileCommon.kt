package com.bunnypranav.watchcalc.tile

import android.content.Context
import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Semantics
import androidx.wear.protolayout.TypeBuilders.StringProp
import com.bunnypranav.watchcalc.MainActivity
import com.bunnypranav.watchcalc.ui.PAGES
import com.google.common.util.concurrent.ListenableFuture

/*
 * Shared pieces for the widgets. There are two shapes:
 *
 *   CalcTileService   a full-screen (2x2) widget with all three shortcuts
 *   PageTileService   a half-height (2x1) widget holding one shortcut, so
 *                     One UI can stack two of them on a single screen
 *
 * Both are ordinary Wear OS TileServices; "2x1" is a Samsung extension
 * declared as manifest meta-data, which is how the stock Reminder and
 * Calendar widgets do it.
 */

internal const val RESOURCES_VERSION = "1"

/** Palette shared with the app (see ui/Theme.kt). */
internal const val INK = 0xFF000000.toInt()
internal const val FN_BG = 0xFF141B26.toInt()
internal const val ACCENT = 0xFF60A5FA.toInt()
internal const val DIM = 0xFF7C8697.toInt()

/**
 * Which keypads the widgets offer. The visible label is read from [PAGES]
 * rather than repeated here, so reordering or renaming a page cannot leave a
 * widget quietly pointing at the wrong one.
 */
internal val SHORTCUTS = listOf(
    Shortcut(page = 0, description = "Numbers keypad"),
    Shortcut(page = 1, description = "Roots and logarithms keypad"),
    Shortcut(page = 2, description = "Trigonometry keypad")
)

internal data class Shortcut(val page: Int, val description: String) {
    val label: String get() = PAGES[page].name
}

internal fun <T> immediate(value: T): ListenableFuture<T> =
    ResolvableFuture.create<T>().apply { set(value) }

/** Launch the calculator straight onto [page]. */
internal fun openPage(context: Context, page: Int): ActionBuilders.Action =
    ActionBuilders.LaunchAction.Builder()
        .setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder()
                .setPackageName(context.packageName)
                .setClassName(MainActivity::class.java.name)
                .addKeyToExtraMapping(
                    MainActivity.EXTRA_PAGE,
                    ActionBuilders.AndroidIntExtra.Builder().setValue(page).build()
                )
                .build()
        )
        .build()

internal fun modifiers(
    context: Context,
    shortcut: Shortcut,
    cornerRadiusDp: Float,
    background: Int = FN_BG
): Modifiers = Modifiers.Builder()
    .setBackground(
        Background.Builder()
            .setColor(argb(background))
            .setCorner(Corner.Builder().setRadius(dp(cornerRadiusDp)).build())
            .build()
    )
    .setClickable(
        Clickable.Builder()
            .setId("page-${shortcut.page}")
            .setOnClick(openPage(context, shortcut.page))
            .build()
    )
    .setSemantics(
        Semantics.Builder()
            .setContentDescription(StringProp.Builder(shortcut.description).build())
            .build()
    )
    .build()

internal fun text(value: String, sizeSp: Float, colour: Int): LayoutElement =
    Text.Builder()
        .setText(value)
        .setFontStyle(
            FontStyle.Builder()
                .setSize(androidx.wear.protolayout.DimensionBuilders.sp(sizeSp))
                .setColor(argb(colour))
                .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                .build()
        )
        .build()

/** The circular shortcut used by the full-screen widget. */
internal fun circleButton(context: Context, shortcut: Shortcut, size: Float): LayoutElement =
    Box.Builder()
        .setWidth(dp(size))
        .setHeight(dp(size))
        // a radius of exactly half the side makes the renderer notch the
        // corners; back it off a hair and the circles come out clean
        .setModifiers(modifiers(context, shortcut, size * 0.48f))
        .addContent(text(shortcut.label, size * 0.32f, ACCENT))
        .build()
