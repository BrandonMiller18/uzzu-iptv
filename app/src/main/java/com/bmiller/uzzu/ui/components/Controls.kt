package com.bmiller.uzzu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuShapes
import com.bmiller.uzzu.ui.theme.UzzuType

/** HANDOFF "Button": Surface fill, 1 dp Outline at rest, 3 dp Accent + 1.05 scale on focus. */
@Composable
fun UzzuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(Dimens.ControlHeight),
        enabled = enabled,
        shape = ButtonDefaults.shape(shape = UzzuShapes.Button),
        colors = ButtonDefaults.colors(
            containerColor = UzzuColors.Surface,
            contentColor = UzzuColors.TextPrimary,
            focusedContainerColor = UzzuColors.Surface,
            focusedContentColor = UzzuColors.TextPrimary,
            pressedContainerColor = UzzuColors.Surface,
            pressedContentColor = UzzuColors.TextPrimary,
            disabledContainerColor = UzzuColors.Background,
            disabledContentColor = UzzuColors.TextSecondary,
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(Dimens.RestBorder, UzzuColors.Outline), shape = UzzuShapes.Button),
            focusedBorder = Border(BorderStroke(Dimens.FocusBorder, UzzuColors.Accent), shape = UzzuShapes.Button),
            pressedBorder = Border(BorderStroke(Dimens.FocusBorder, UzzuColors.Accent), shape = UzzuShapes.Button),
            disabledBorder = Border(BorderStroke(Dimens.RestBorder, UzzuColors.Outline), shape = UzzuShapes.Button),
        ),
        scale = ButtonDefaults.scale(focusedScale = Dimens.FocusScale),
        contentPadding = PaddingValues(horizontal = Dimens.S6, vertical = 0.dp),
    ) {
        Text(text.uppercase(), style = UzzuType.Label)
    }
}

/** Focusable list row (HANDOFF "Channel row" frame): Row shape, Outline at rest, Accent on focus. */
@Composable
fun UzzuRowCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(Dimens.ChannelRowHeight),
        shape = CardDefaults.shape(shape = UzzuShapes.Row),
        colors = CardDefaults.colors(
            containerColor = UzzuColors.Surface,
            contentColor = UzzuColors.TextPrimary,
            focusedContainerColor = UzzuColors.Surface,
            focusedContentColor = UzzuColors.TextPrimary,
            pressedContainerColor = UzzuColors.Surface,
            pressedContentColor = UzzuColors.TextPrimary,
        ),
        border = CardDefaults.border(
            border = Border(BorderStroke(Dimens.RestBorder, UzzuColors.Outline), shape = UzzuShapes.Row),
            focusedBorder = Border(BorderStroke(Dimens.FocusBorder, UzzuColors.Accent), shape = UzzuShapes.Row),
            pressedBorder = Border(BorderStroke(Dimens.FocusBorder, UzzuColors.Accent), shape = UzzuShapes.Row),
        ),
        scale = CardDefaults.scale(focusedScale = Dimens.FocusScale),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.S6).height(Dimens.ChannelRowHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.S5),
            content = content,
        )
    }
}

/** Mono section tag such as `// LIVE GAMES`. */
@Composable
fun MonoTag(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = UzzuType.Mono, color = UzzuColors.TextSecondary, modifier = modifier)
}

/** Key hint chip: key name in a 1 dp Outline box, then the action. */
@Composable
fun KeyHint(key: String, action: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.S3)) {
        Box(Modifier.border(Dimens.RestBorder, UzzuColors.Outline).padding(horizontal = 5.dp)) {
            Text(key, style = UzzuType.Mono, color = UzzuColors.TextPrimary)
        }
        Text(action.uppercase(), style = UzzuType.Mono, color = UzzuColors.TextSecondary)
    }
}
