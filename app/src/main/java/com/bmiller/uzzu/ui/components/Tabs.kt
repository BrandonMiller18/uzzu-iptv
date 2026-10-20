package com.bmiller.uzzu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuShapes
import com.bmiller.uzzu.ui.theme.UzzuType

/**
 * HANDOFF "Tab": tv-material3 TabRow/Tab without the pill indicator. Selected = Active fill +
 * TextPrimary; unselected = transparent + TextSecondary; focused = selected look + 3 dp Accent
 * border + 1.05 scale. Focusing a tab selects it (TV convention), so focused implies selected.
 */
@Composable
fun UzzuTabRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = Dimens.S2,
    horizontalPadding: Dp = 11.dp,
) {
    // Entering the row from below lands on the selected tab, not the one geometrically above.
    val selectedRequester = remember { FocusRequester() }
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.focusRestorer(selectedRequester),
        containerColor = Color.Transparent,
        contentColor = UzzuColors.TextSecondary,
        separator = { Spacer(Modifier.width(gap)) },
        indicator = { _, _ -> },
    ) {
        labels.forEachIndexed { i, label ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val selected = i == selectedIndex
            Tab(
                selected = selected,
                onFocus = { if (!selected) onSelect(i) },
                onClick = { if (!selected) onSelect(i) },
                modifier = Modifier
                    .then(if (selected) Modifier.focusRequester(selectedRequester) else Modifier)
                    .testTag("tab:$label")
                    .graphicsLayer {
                        val s = if (focused) Dimens.FocusScale else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .then(if (focused) Modifier.border(Dimens.FocusBorder, UzzuColors.Accent, UzzuShapes.Tab) else Modifier)
                    .background(if (selected || focused) UzzuColors.Active else Color.Transparent, UzzuShapes.Tab),
                colors = TabDefaults.pillIndicatorTabColors(
                    contentColor = UzzuColors.TextSecondary,
                    inactiveContentColor = UzzuColors.TextSecondary,
                    selectedContentColor = UzzuColors.TextPrimary,
                    focusedContentColor = UzzuColors.TextPrimary,
                    focusedSelectedContentColor = UzzuColors.TextPrimary,
                ),
                interactionSource = interaction,
            ) {
                Text(label.uppercase(), style = UzzuType.Label, maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 6.dp))
            }
        }
    }
}
