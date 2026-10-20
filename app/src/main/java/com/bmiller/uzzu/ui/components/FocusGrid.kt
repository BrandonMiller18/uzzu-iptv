package com.bmiller.uzzu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bmiller.uzzu.ui.theme.Dimens

/**
 * What a list screen remembers while it is on the back stack (selected tab, last focused item), so
 * Back from the player lands on the item that was played. Not observable on purpose for
 * [focusKey]: writing it on every focus move must not recompose anything.
 */
@Stable
class ListMemory {
    var tab: String? by mutableStateOf(null)
    var focusKey: String? = null
}

/** Room for the 1.05 focus scale of a 420 dp item (10.5 dp per side) inside the grid's clip. */
private val ScaleRoom = 12.dp

/** Room above/below for the 1.05 scale of a 94 dp card (2.4 dp), so the tab-row gap stays 14 dp. */
private val FocusRoomV = 3.dp

/**
 * Two-column grid of fixed-size focusable items with stable keys (HANDOFF: 24 dp column gap). On
 * first composition it scrolls to and focuses [initialFocusKey]. When [resetKey] (the tab) changes
 * it scrolls back to the top without moving focus. The focused item's key goes to [memory].
 */
@Composable
fun <T> FocusGrid(
    items: List<T>,
    key: (T) -> String,
    initialFocusKey: String?,
    resetKey: Any?,
    memory: ListMemory,
    rowGap: Dp,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, index: Int, modifier: Modifier) -> Unit,
) {
    val state = rememberLazyGridState()
    val requesters = remember { HashMap<String, FocusRequester>() }

    LaunchedEffect(Unit) {
        val target = initialFocusKey ?: return@LaunchedEffect
        val index = items.indexOfFirst { key(it) == target }
        if (index < 0) return@LaunchedEffect
        state.scrollToItem(index)
        withFrameNanos { }
        runCatching { requesters[target]?.requestFocus() }
    }
    var lastReset by remember { mutableStateOf(resetKey) }
    LaunchedEffect(resetKey) {
        if (resetKey != lastReset) {
            lastReset = resetKey
            state.scrollToItem(0)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = state,
        // Pulled up by the focus room so the first row sits 14 dp under the tab row (Games/Channels frames).
        modifier = modifier.offset(y = -FocusRoomV).padding(horizontal = Dimens.SafeH - ScaleRoom),
        contentPadding = PaddingValues(horizontal = ScaleRoom, vertical = FocusRoomV),
        horizontalArrangement = Arrangement.spacedBy(Dimens.GridColumnGap),
        verticalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            val k = key(item)
            val requester = remember(k) { requesters.getOrPut(k) { FocusRequester() } }
            itemContent(
                item,
                index,
                Modifier.focusRequester(requester).onFocusChanged { if (it.isFocused) memory.focusKey = k },
            )
        }
    }
}
