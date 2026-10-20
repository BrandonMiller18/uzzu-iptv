package com.bmiller.uzzu.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** tv-material3 theme mapped onto the Uzzu tokens. Components use [UzzuColors] directly. */
@Composable
fun UzzuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = UzzuColors.Background,
            onBackground = UzzuColors.TextPrimary,
            surface = UzzuColors.Surface,
            onSurface = UzzuColors.TextPrimary,
            onSurfaceVariant = UzzuColors.TextSecondary,
            primary = UzzuColors.Active,
            onPrimary = UzzuColors.TextPrimary,
            border = UzzuColors.Accent,
            borderVariant = UzzuColors.Outline,
            error = UzzuColors.Error,
        ),
        content = content,
    )
}
