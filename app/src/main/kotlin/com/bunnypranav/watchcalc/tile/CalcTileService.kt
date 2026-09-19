package com.bunnypranav.watchcalc.tile

import android.content.Context
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/**
 * The full-screen (2x2) widget: all three keypad shortcuts on one screen.
 *
 * Built from raw ProtoLayout primitives rather than the Material components,
 * for the same reason the app itself skips Material — the whole thing is three
 * circles and a caption, and this keeps the dependency surface small.
 */
class CalcTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = immediate(
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(
                    tileLayout(this, requestParams.deviceConfiguration)
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

/**
 * Sizes come from the reported screen width so the widget looks the same on a
 * 40 mm and a 44 mm watch, which report quite different dp values.
 */
private fun tileLayout(context: Context, device: DeviceParameters): LayoutElement {
    val screen = device.screenWidthDp
    val button = screen * 0.27f
    val gap = screen * 0.035f

    val row = Row.Builder().setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
    SHORTCUTS.forEachIndexed { index, shortcut ->
        if (index > 0) row.addContent(Spacer.Builder().setWidth(dp(gap)).build())
        row.addContent(circleButton(context, shortcut, button))
    }

    // Column wraps its content and the enclosing Box centres it, rather than
    // the Column expanding and pinning everything to the top.
    val column = Column.Builder()
        .setWidth(wrap())
        .setHeight(wrap())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .addContent(text("WatchCalc", screen * 0.075f, DIM))
        .addContent(Spacer.Builder().setHeight(dp(screen * 0.05f)).build())
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
        .addContent(column)
        .build()
}
