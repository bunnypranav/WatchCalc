package com.bunnypranav.watchcalc.tile

import android.content.Context
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/**
 * A half-height (2x1) widget carrying one keypad shortcut.
 *
 * One UI stacks two of these on a single widget screen, so the calculator can
 * sit next to something else rather than taking a whole screen to itself.
 * Each subclass is registered separately in the manifest, which is what makes
 * them individually addable from the widget picker.
 */
abstract class PageTileService : TileService() {

    internal abstract val shortcut: Shortcut

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = immediate(
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(
                    pageLayout(this, requestParams.deviceConfiguration, shortcut)
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
 * One wide rounded button filling the widget: the keypad name, with the app
 * name underneath so it is identifiable when stacked among other widgets.
 *
 * Sizes come from the reported screen width, which is stable whether the
 * system hands this a half-height or a full-height area; the content is
 * centred either way.
 */
private fun pageLayout(
    context: Context,
    device: DeviceParameters,
    shortcut: Shortcut
): LayoutElement {
    val screen = device.screenWidthDp
    val pill = Box.Builder()
        .setWidth(dp(screen * 0.80f))
        .setHeight(dp(screen * 0.30f))
        .setModifiers(modifiers(context, shortcut, screen * 0.15f))
        .addContent(
            Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(text(shortcut.label, screen * 0.105f, ACCENT))
                .addContent(text("WatchCalc", screen * 0.05f, DIM))
                .build()
        )
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

/* One service per keypad. They exist only so the manifest can register three
   separately addable widgets; all the behaviour is in the base class. */

class NumbersTileService : PageTileService() {
    override val shortcut = SHORTCUTS[0]
}

class LogTileService : PageTileService() {
    override val shortcut = SHORTCUTS[1]
}

class TrigTileService : PageTileService() {
    override val shortcut = SHORTCUTS[2]
}
