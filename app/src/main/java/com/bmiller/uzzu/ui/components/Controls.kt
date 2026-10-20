package com.bmiller.uzzu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuShapes
import com.bmiller.uzzu.ui.theme.UzzuType

/**
 * HANDOFF "Button": Surface fill (or [container]), 1 dp Outline at rest, 3 dp Accent + 1.05 scale
 * on focus, Button cut shape, uppercase condensed label, optional leading line icon.
 */
@Composable
fun UzzuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    container: Color = UzzuColors.Surface,
    height: Dp = Dimens.ControlHeight,
    horizontalPadding: Dp = 14.dp,
) {
    val rest = Border(BorderStroke(Dimens.RestBorder, UzzuColors.Outline), shape = UzzuShapes.Button)
    val focused = Border(BorderStroke(Dimens.FocusBorder, UzzuColors.Accent), shape = UzzuShapes.Button)
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        enabled = enabled,
        shape = ButtonDefaults.shape(shape = UzzuShapes.Button),
        colors = ButtonDefaults.colors(
            containerColor = container,
            contentColor = UzzuColors.TextPrimary,
            focusedContainerColor = container,
            focusedContentColor = UzzuColors.TextPrimary,
            pressedContainerColor = container,
            pressedContentColor = UzzuColors.TextPrimary,
            disabledContainerColor = UzzuColors.Background,
            disabledContentColor = UzzuColors.TextSecondary,
        ),
        border = ButtonDefaults.border(border = rest, focusedBorder = focused, pressedBorder = focused, disabledBorder = rest),
        scale = ButtonDefaults.scale(focusedScale = Dimens.FocusScale),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 0.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = UzzuColors.TextPrimary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(text.uppercase(), style = UzzuType.Label, maxLines = 1)
    }
}

/** Mono section tag such as `// LIVE GAMES`. */
@Composable
fun MonoTag(text: String, modifier: Modifier = Modifier, color: Color = UzzuColors.TextSecondary) {
    Text(text.uppercase(), style = UzzuType.Mono, color = color, modifier = modifier, maxLines = 1)
}

/** HANDOFF "Not designed": loading is a static TextSecondary mono tag, no spinner. */
@Composable
fun LoadingTag(modifier: Modifier = Modifier) = MonoTag("LOADING", modifier)

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

/** Bottom key-hint strip: pairs of (key, action), 20 dp apart. */
@Composable
fun KeyHints(vararg hints: Pair<String, String>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        for ((key, action) in hints) KeyHint(key, action)
    }
}

/** LIVE marker: Accent fill, Background text, 5 dp square dot before the label. */
@Composable
fun LiveChip(modifier: Modifier = Modifier, inCard: Boolean = false) {
    Row(
        modifier
            .background(UzzuColors.Accent, if (inCard) UzzuShapes.LiveChipInCard else UzzuShapes.Chip)
            .then(if (inCard) Modifier.height(22.dp).padding(start = 11.dp, end = 20.dp) else Modifier.padding(start = 6.dp, end = 7.dp, top = 1.dp, bottom = 1.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(5.dp).background(UzzuColors.OnAccent))
        Text("LIVE", style = UzzuType.Chip, color = UzzuColors.OnAccent)
    }
}

/** PLAYING marker: Active fill behind TextPrimary. */
@Composable
fun PlayingChip(modifier: Modifier = Modifier) {
    Text(
        "PLAYING",
        style = UzzuType.Chip,
        color = UzzuColors.TextPrimary,
        modifier = modifier.background(UzzuColors.Active, UzzuShapes.Chip).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Header block used by every screen: mono tag over a display title, optional trailing content, 1 dp rule below. */
@Composable
fun ScreenHeader(
    tag: String,
    title: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = UzzuType.Display,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MonoTag(tag)
                Text(title.uppercase(), style = titleStyle, color = UzzuColors.TextPrimary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.S5), content = trailing)
        }
        Rule()
    }
}

/** 1 dp Outline rule. */
@Composable
fun Rule(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(1.dp).background(UzzuColors.Outline))
}

/**
 * Moves focus to [requester] once [key] settles. Waits one frame first so the target is attached
 * and placed (a request during the very first frame can be dropped).
 */
@Composable
fun InitialFocus(requester: FocusRequester, key: Any? = Unit) {
    LaunchedEffect(key) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
}
