package com.bunnypranav.watchcalc.tile

import android.content.Context
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/**
 * All three keypad shortcuts in a **half-height (2x1)** widget: one pill
 * holding three circular buttons, in the shape One UI's own stacked widgets
 * use. Two of these fit on a single widget screen alongside something else.
 *
 * The full-screen version of the same thing is [CalcTileService].
 */
class CompactTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = immediate(
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(
                    compactLayout(this, requestParams.deviceConfiguration)
                )
            )
            .build()
    )

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = immediate(
        ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
    )
}

/** The pill behind the buttons — a shade up from the background so the
 *  widget reads as one object when stacked next to another. */
private const val PILL_BG = 0xFF13213A.toInt()
private const val BUTTON_BG = 0xFF0B0D12.toInt()

private fun compactLayout(context: Context, device: DeviceParameters): LayoutElement {
    val screen = device.screenWidthDp
    // One UI rounds the corners of the widget slot itself, so a pill much
    // wider than this gets its own corners clipped by the container.
    val pillW = screen * 0.84f
    val pillH = screen * 0.30f
    val button = screen * 0.22f
    val gap = screen * 0.03f

    val row = Row.Builder().setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
    SHORTCUTS.forEachIndexed { index, shortcut ->
        if (index > 0) row.addContent(Spacer.Builder().setWidth(dp(gap)).build())
        row.addContent(
            Box.Builder()
                .setWidth(dp(button))
                .setHeight(dp(button))
                .setModifiers(modifiers(context, shortcut, button * 0.48f, BUTTON_BG))
                .addContent(text(shortcut.label, button * 0.30f, ACCENT))
                .build()
        )
    }

    val pill = Box.Builder()
        .setWidth(dp(pillW))
        .setHeight(dp(pillH))
        .setModifiers(
            Modifiers.Builder()
                .setBackground(
                    Background.Builder()
                        .setColor(argb(PILL_BG))
                        .setCorner(Corner.Builder().setRadius(dp(pillH / 2f)).build())
                        .build()
                )
                .build()
        )
        .addContent(row.build())
        .build()

    return Box.Builder()
        .setWidth(expand())
        .setHeight(expand())
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setModifiers(
            Modifiers.Builder()
                .setBackground(Background.Builder().setColor(argb(INK)).build())
                .build()
        )
        .addContent(pill)
        .build()
}
