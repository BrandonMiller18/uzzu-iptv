package com.bmiller.uzzu.ui.theme

import androidx.compose.ui.unit.dp

// Canvas px ÷ 2.
object Dimens {
    // Overscan-safe area (Android TV layout guide: 48 dp / 27 dp on a 960×540 dp screen).
    val SafeH = 48.dp
    val SafeV = 27.dp

    // Spacing scale (px 4 8 12 16 24 32 48 64).
    val S1 = 2.dp
    val S2 = 4.dp
    val S3 = 6.dp
    val S4 = 8.dp
    val S5 = 12.dp
    val S6 = 16.dp
    val S7 = 24.dp
    val S8 = 32.dp

    // Focus.
    const val FocusScale = 1.05f
    val FocusBorder = 3.dp
    val RestBorder = 1.dp

    // Logos.
    val LogoSmall = 32.dp
    val LogoLarge = 48.dp

    // Components.
    val TileWidth = 390.dp
    val TileHeight = 220.dp
    val GameCardWidth = 420.dp
    val GameCardHeight = 94.dp
    val ChannelRowHeight = 52.dp
    val OverlayItemHeight = 48.dp
    val ControlHeight = 28.dp       // tabs, buttons
    val GridColumnGap = 24.dp
    val GridRowGapCards = 16.dp
    val GridRowGapRows = 12.dp
    val OverlayWidthFraction = 0.4f // 384 dp at 960 dp wide

    // Motion.
    const val FadeMillis = 150
    const val TuneBannerMillis = 3000
}
