package com.bmiller.uzzu.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.unit.dp

// All corners are cut, never rounded. Canvas px ÷ 2.
object UzzuShapes {
    // Cards cut top-right and bottom-left.
    val Tile = CutCornerShape(topEnd = 20.dp, bottomStart = 20.dp)    // S1 home tiles (40 px)
    val Card = CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)    // game card (24 px)
    val Dialog = CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp)  // feed picker (32 px)
    val Panel = CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)   // error diagnostics block
    val Row = CutCornerShape(topEnd = 8.dp, bottomStart = 8.dp)       // channel row, overlay item (16 px)
    val SettingsRow = CutCornerShape(topEnd = 9.dp, bottomStart = 9.dp)
    val Banner = CutCornerShape(topEnd = 12.dp)                       // tune banner

    // Interactive controls cut top-left and bottom-right.
    val Button = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp)    // (14 px)
    val Tab = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp)       // (10 px)
    val Toggle = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp)

    // Chips.
    val LiveChipInCard = CutCornerShape(topEnd = 11.dp, bottomStart = 7.dp) // sits flush in the card's notch
    val Chip = CutCornerShape(topEnd = 4.dp)                                 // LIVE / PLAYING inline
}
