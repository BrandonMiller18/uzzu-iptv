package com.bmiller.uzzu.ui.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.bmiller.uzzu.player.PlayContext
import com.bmiller.uzzu.player.Section
import com.bmiller.uzzu.playlist.FeedSide
import com.bmiller.uzzu.ui.components.LiveChip
import com.bmiller.uzzu.ui.components.MonoTag
import com.bmiller.uzzu.ui.components.PlayingChip
import com.bmiller.uzzu.ui.components.UzzuCard
import com.bmiller.uzzu.ui.components.UzzuIcons
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuShapes
import com.bmiller.uzzu.ui.theme.UzzuType

/** Overlay panel width: 40% of the 960 dp screen. */
val OverlayWidth = 384.dp

/**
 * O1 (Overlay.dc.html): left 384 dp, OverlayScrim fill, 1 dp Outline right edge. Mono tag, group
 * tabs (display only: Left/Right switch them from anywhere in the panel), the item list with the
 * PLAYING chip and the H/A row under the playing game, and "Exit to menu" pinned at the bottom.
 * Up/Down move focus (items, H/A row, Exit), Select acts, Back closes. Opens on the playing item.
 */
@Composable
fun PlayerOverlay(
    section: Section,
    tabs: List<String>,
    tab: String,
    rows: List<OverlayRow>,
    onTab: (String) -> Unit,
    onSelectItem: (OverlayRow.Item) -> Unit,
    onFlipSide: () -> Unit,
    onExit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val listState = rememberLazyListState()
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }

    var focusedKey by remember { mutableStateOf<String?>(null) }
    suspend fun focusDefault() {
        val key = overlayFocusKey(rows)
        val index = rows.indexOfFirst { it.key == key }
        if (index >= 0) listState.scrollToItem((index - 1).coerceAtLeast(0))
        withFrameNanos { }
        runCatching { requester(key).requestFocus() }
    }
    // On open and on every tab switch: scroll to and focus the playing item (or the first row).
    LaunchedEffect(tab) { focusDefault() }
    // A model update (refresh, ESPN poll) removed the focused row: don't leave the D-pad dead.
    LaunchedEffect(rows) {
        val k = focusedKey ?: return@LaunchedEffect
        if (k != OverlayRow.KEY_EXIT && rows.none { it.key == k }) focusDefault()
    }

    Row(
        modifier
            .width(OverlayWidth)
            .fillMaxHeight()
            .testTag("overlay")
            .onPreviewKeyEvent { e ->
                val code = e.nativeKeyEvent.keyCode
                if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return@onPreviewKeyEvent false
                if (e.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val next = neighbourTab(tabs, tab, if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                    if (next != tab) onTab(next)
                }
                true
            },
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(UzzuColors.OverlayScrim)
                .padding(start = Dimens.SafeH, end = 20.dp, top = Dimens.SafeV, bottom = Dimens.SafeV),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonoTag(if (section == Section.GAMES) "// SWITCH SIGNAL · GAMES" else "// SWITCH SIGNAL · CHANNELS")
            OverlayTabs(tabs, tab)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.S2),
            ) {
                items(rows, key = { it.key }) { row ->
                    val m = Modifier
                        .focusRequester(requester(row.key))
                        .onFocusChanged { if (it.isFocused) focusedKey = row.key }
                        .testTag(row.key)
                    when (row) {
                        is OverlayRow.Item -> OverlayItem(row, { onSelectItem(row) }, m)
                        is OverlayRow.Toggle -> FeedToggleRow(row.side, onFlipSide, m)
                    }
                }
            }
            ExitRow(
                onExit,
                Modifier
                    .focusRequester(requester(OverlayRow.KEY_EXIT))
                    .onFocusChanged { if (it.isFocused) focusedKey = OverlayRow.KEY_EXIT }
                    .testTag(OverlayRow.KEY_EXIT),
            )
        }
        Spacer(Modifier.width(Dimens.RestBorder).fillMaxHeight().background(UzzuColors.Outline))
    }
}

/** Display-only tab row; the selected tab is kept in view. */
@Composable
private fun OverlayTabs(tabs: List<String>, tab: String) {
    val state = rememberLazyListState()
    val selected = tabs.indexOf(tab)
    // Scroll only when the selected tab is not fully in view.
    LaunchedEffect(selected) {
        if (selected < 0) return@LaunchedEffect
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == selected }
        val inView = item != null && item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
        if (!inView) state.animateScrollToItem((selected - 1).coerceAtLeast(0))
    }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(Dimens.S1), userScrollEnabled = false) {
        itemsIndexed(tabs, key = { _, t -> t }) { i, t ->
            val on = i == selected
            Text(
                t.uppercase(),
                style = UzzuType.Label,
                color = if (on) UzzuColors.TextPrimary else UzzuColors.TextSecondary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .testTag("otab:$t")
                    .background(if (on) UzzuColors.Active else Color.Transparent, UzzuShapes.Tab)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** 2-line overlay ListItem: title, then LIVE chip + detail + PLAYING chip. Transparent at rest. */
@Composable
private fun OverlayItem(row: OverlayRow.Item, onClick: () -> Unit, modifier: Modifier) {
    UzzuCard(
        onClick, UzzuShapes.Row, modifier.fillMaxWidth().height(Dimens.OverlayItemHeight),
        container = Color.Transparent, focusedContainer = UzzuColors.Surface, restBorder = Color.Transparent,
    ) {
        // Final: 40% on the text only; the PLAYING chip stays full strength.
        val fade = if (row.final) Modifier.alpha(UzzuColors.FinalContentAlpha) else Modifier
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalArrangement = Arrangement.Center) {
            Text(row.title.uppercase(), style = UzzuType.Label, color = UzzuColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = fade)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.S3)) {
                if (row.live) LiveChip()
                if (row.detail.isNotEmpty()) {
                    Text(row.detail, style = UzzuType.MonoPlain, color = if (row.final) UzzuColors.TextPrimary else UzzuColors.TextSecondary, maxLines = 1, softWrap = false, modifier = fade)
                }
                if (row.playing) PlayingChip()
            }
        }
    }
}

/** `└ FEED  [HOME|AWAY]`: one focusable row; Select flips the side. Active segment = Active fill. */
@Composable
private fun FeedToggleRow(side: FeedSide, onFlip: () -> Unit, modifier: Modifier) {
    UzzuCard(
        onFlip, UzzuShapes.Row, modifier.fillMaxWidth().height(36.dp),
        container = Color.Transparent, focusedContainer = UzzuColors.Surface, restBorder = Color.Transparent,
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 22.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("└ FEED", style = UzzuType.MonoPlain, color = UzzuColors.TextSecondary)
            Row(
                Modifier.background(UzzuColors.Outline, UzzuShapes.Toggle).padding(Dimens.RestBorder),
                horizontalArrangement = Arrangement.spacedBy(Dimens.S1),
            ) {
                Segment("Home", side == FeedSide.HOME)
                Segment("Away", side == FeedSide.AWAY)
            }
        }
    }
}

@Composable
private fun Segment(label: String, active: Boolean) {
    Text(
        label.uppercase(),
        style = UzzuType.Label,
        color = if (active) UzzuColors.TextPrimary else UzzuColors.TextSecondary,
        modifier = Modifier
            .testTag("segment:${label.lowercase()}:${if (active) "on" else "off"}")
            .background(if (active) UzzuColors.Active else UzzuColors.Background)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

@Composable
private fun ExitRow(onExit: () -> Unit, modifier: Modifier) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(Dimens.RestBorder).background(UzzuColors.Outline))
        Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            UzzuCard(
                onExit, UzzuShapes.Row, modifier.fillMaxWidth().height(32.dp),
                container = Color.Transparent, focusedContainer = UzzuColors.Surface, restBorder = Color.Transparent,
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.S4)) {
                    Icon(UzzuIcons.ChevronLeft, contentDescription = null, tint = UzzuColors.TextPrimary, modifier = Modifier.size(14.dp))
                    Text("EXIT TO MENU", style = UzzuType.Label, color = UzzuColors.TextPrimary)
                }
            }
        }
    }
}

/** Context for a tune from the overlay: the tab becomes the current group. */
fun overlayTuneContext(section: Section, tab: String, rows: List<OverlayRow>, id: String, side: FeedSide?) =
    PlayContext(section, tab, rows.filterIsInstance<OverlayRow.Item>().map { it.id }, id, side)
