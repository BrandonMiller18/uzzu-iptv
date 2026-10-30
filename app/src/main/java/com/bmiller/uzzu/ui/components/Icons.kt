package com.bmiller.uzzu.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Line icons from the design frames (24-unit viewBox SVG paths, stroked, square caps, miter joins).
 * Tint them at the call site; the stroke color here is only a placeholder.
 */
object UzzuIcons {
    /** Team logo placeholder. */
    val Shield = icon(1.75f, StrokeCap.Butt, "M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6z")

    /** Channel logo placeholder, channels tile, ungrouped game. */
    val Tv = icon(1.75f, StrokeCap.Square, "M3 6h18v12H3z", "M8 21h8M9 2l3 4 3-4")

    /** Games tile (scoreboard-shaped pictogram; no numbers). */
    val Games = icon(1.5f, StrokeCap.Square, "M2 5h20v14H2z", "M12 5v14", "M9 9h6v6H9z")

    val Refresh = icon(2f, StrokeCap.Square, "M20 11a8 8 0 1 0-2.3 5.7", "M20 4v7h-7")

    val Settings = icon(
        2f, StrokeCap.Square,
        "M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12",
        "M14 4h4v4h-4z", "M8 10h4v4H8z", "M16 16h4v4h-4z",
    )

    /** S6 error mark. */
    val Warning = icon(2f, StrokeCap.Square, "M12 3l10 18H2z", "M12 10v5M12 18v.5")

    /** O1 "Exit to menu". */
    val ChevronLeft = icon(2f, StrokeCap.Square, "M15 6l-6 6 6 6")

    /** O1 group row (rotated 180° while the group list is open). */
    val ChevronDown = icon(2f, StrokeCap.Square, "M6 9l6 6 6-6")

    private fun icon(stroke: Float, cap: StrokeCap, vararg paths: String): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                for (d in paths) {
                    addPath(
                        pathData = PathParser().parsePathString(d).toNodes(),
                        fill = null,
                        stroke = SolidColor(Color.White),
                        strokeLineWidth = stroke,
                        strokeLineCap = cap,
                        strokeLineJoin = StrokeJoin.Miter,
                    )
                }
            }
            .build()
}
