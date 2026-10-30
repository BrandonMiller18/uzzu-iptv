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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
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
import kotlinx.coroutines.launch

/** Overlay panel width: 40% of the 960 dp screen. */
val OverlayWidth = 384.dp

private const val KEY_GROUP_ROW = "grouprow"
private fun listKey(section: Section) = "list:${section.name.lowercase()}"
private fun groupKey(name: String) = "group:$name"
private fun Section.label() = if (this == Section.GAMES) "Live Games" else "Live Channels"

/**
 * O1 (Overlay.dc.html, frames a–d): left 384 dp, OverlayScrim fill, 1 dp Outline right edge. One
 * column, top to bottom: mono tag, the list picker (Live Games | Live Channels), the group row, the
 * item list (PLAYING chip, H/A row under the playing game) and "Exit to menu" pinned at the bottom.
 * Select on the group row swaps the items for the group list (modal; Back closes it).
 *
 * Every focus move that matters is explicit (handled in the panel's key preview), because Compose's
 * 2D search on the Fire TV does not follow Robolectric: Up from the first item → group row → the
 * listed segment; Down from the group row → first item; Left/Right only move inside the list picker
 * and never change the list or group. Opens on the playing item. After a list change focus stays on
 * the segment, after a group change it goes back to the group row; the items scroll to the top.
 */
@Composable
fun PlayerOverlay(
    section: Section,
    groups: List<OverlayGroup>,
    tab: String,
    rows: List<OverlayRow>,
    onSection: (Section) -> Unit,
    onGroup: (String) -> Unit,
    onSelectItem: (OverlayRow.Item) -> Unit,
    onFlipSide: () -> Unit,
    onExit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }
    fun focus(key: String) = runCatching { requester(key).requestFocus() }

    var focusedKey by remember { mutableStateOf<String?>(null) }
    var groupOpen by remember { mutableStateOf(false) }

    // Registered after the panel's BackHandler, so it wins while the group list is open.
    fun closeGroups() {
        // Focus moves before the focused option leaves composition, or the D-pad goes dead.
        focus(KEY_GROUP_ROW)
        groupOpen = false
    }
    BackHandler(enabled = groupOpen, onBack = ::closeGroups)

    // Open: scroll to and focus the playing item (or the first row). Later list/group changes only
    // scroll the items back to the top; focus stays where the change was made.
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(section, tab) {
        if (opened) {
            listState.scrollToItem(0)
            return@LaunchedEffect
        }
        opened = true
        val key = overlayFocusKey(rows)
        val index = rows.indexOfFirst { it.key == key }
        if (index >= 0) listState.scrollToItem((index - 1).coerceAtLeast(0))
        withFrameNanos { }
        focus(key)
    }
    // A model update (refresh, ESPN poll) removed the focused row or group: don't leave the D-pad dead.
    LaunchedEffect(rows, groups) {
        val k = focusedKey ?: return@LaunchedEffect
        val gone = when {
            k.startsWith("item:") || k == OverlayRow.KEY_TOGGLE -> rows.none { it.key == k }
            k.startsWith("group:") -> groups.none { groupKey(it.name) == k }
            else -> false
        }
        if (gone) closeGroups()
    }

    fun enterItems() {
        val first = rows.firstOrNull()?.key
        if (first == null) {
            focus(OverlayRow.KEY_EXIT)
            return
        }
        scope.launch {
            listState.scrollToItem(0)
            withFrameNanos { }
            focus(first)
        }
    }

    // Key up follows its key down: a consumed down must not leak its up to the newly focused row.
    val handledDown = remember { BooleanArray(1) }

    /** Explicit D-pad moves; true = consumed. Keys not listed fall through to the default search. */
    fun onDpad(code: Int): Boolean {
        val k = focusedKey ?: return false
        val up = code == KeyEvent.KEYCODE_DPAD_UP
        val down = code == KeyEvent.KEYCODE_DPAD_DOWN
        val left = code == KeyEvent.KEYCODE_DPAD_LEFT
        val right = code == KeyEvent.KEYCODE_DPAD_RIGHT
        when {
            k.startsWith("list:") -> when {
                left -> focus(listKey(Section.GAMES))
                right -> focus(listKey(Section.CHANNELS))
                down -> focus(KEY_GROUP_ROW)
            }
            k == KEY_GROUP_ROW -> when {
                up -> focus(listKey(section))
                down -> enterItems()
            }
            k.startsWith("group:") -> {
                // Modal list: Up/Down stay inside it, no wrap.
                val i = groups.indexOfFirst { groupKey(it.name) == k }
                val next = when {
                    up -> i - 1
                    down -> i + 1
                    else -> i
                }
                groups.getOrNull(next)?.let { focus(groupKey(it.name)) }
            }
            else -> {
                if (left || right) return true
                if (up && k == rows.firstOrNull()?.key) focus(KEY_GROUP_ROW) else return false
            }
        }
        return true
    }

    Row(
        modifier
            .width(OverlayWidth)
            .fillMaxHeight()
            .testTag("overlay")
            .onPreviewKeyEvent { e ->
                val code = e.nativeKeyEvent.keyCode
                if (code !in DPAD_KEYS) return@onPreviewKeyEvent false
                // Decide on key down; swallow the matching key up only when the down was handled.
                if (e.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) onDpad(code).also { handledDown[0] = it } else handledDown[0]
            },
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(UzzuColors.OverlayScrim)
                .padding(start = Dimens.SafeH, end = 20.dp, top = Dimens.SafeV, bottom = Dimens.SafeV),
        ) {
            MonoTag("// SWITCH SIGNAL")
            Spacer(Modifier.height(Dimens.S4))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.S2)) {
                for (s in Section.entries) {
                    val key = listKey(s)
                    ListSegment(
                        s.label(),
                        active = s == section,
                        onClick = { onSection(s) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(requester(key))
                            .onFocusChanged { if (it.isFocused) focusedKey = key }
                            .testTag(key),
                    )
                }
            }
            Spacer(Modifier.height(Dimens.S3))
            GroupRow(
                name = tab,
                count = groups.firstOrNull { it.name == tab }?.count,
                open = groupOpen,
                onClick = {
                    if (groupOpen) return@GroupRow closeGroups()
                    groupOpen = true
                    scope.launch {
                        withFrameNanos { }
                        focus(groupKey(tab))
                    }
                },
                modifier = Modifier
                    .focusRequester(requester(KEY_GROUP_ROW))
                    .onFocusChanged { if (it.isFocused) focusedKey = KEY_GROUP_ROW }
                    .testTag(KEY_GROUP_ROW),
            )
            Spacer(Modifier.height(Dimens.S5))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (groupOpen) {
                    GroupList(section, groups, tab) { g ->
                        val key = groupKey(g.name)
                        GroupOption(
                            g,
                            current = g.name == tab,
                            onClick = {
                                closeGroups()
                                onGroup(g.name)
                            },
                            modifier = Modifier
                                .focusRequester(requester(key))
                                .onFocusChanged { if (it.isFocused) focusedKey = key }
                                .testTag(key),
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
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
                }
            }
            Spacer(Modifier.height(Dimens.S4))
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

private val DPAD_KEYS = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)

/** One half of the list picker. Pink = the list on screen; focus draws on top (it never switches on focus). */
@Composable
private fun ListSegment(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val fill = if (active) UzzuColors.Active else UzzuColors.Surface
    UzzuCard(
        onClick, UzzuShapes.Tab, modifier.height(32.dp),
        container = fill, restBorder = if (active) UzzuColors.Active else UzzuColors.Outline,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label.uppercase(),
                style = UzzuType.Title,
                color = if (active) UzzuColors.TextPrimary else UzzuColors.TextSecondary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag("${if (active) "on" else "off"}:${label.lowercase()}"),
            )
        }
    }
}

/** `GROUP  NFL  12  ⌄`: names the listed group; Select opens the group list. */
@Composable
private fun GroupRow(name: String, count: Int?, open: Boolean, onClick: () -> Unit, modifier: Modifier) {
    UzzuCard(onClick, UzzuShapes.Row, modifier.fillMaxWidth().height(36.dp)) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("GROUP", style = UzzuType.Mono, color = UzzuColors.TextSecondary, maxLines = 1)
            Text(
                name.uppercase(),
                style = UzzuType.Title,
                color = UzzuColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).testTag("groupname"),
            )
            if (count != null) Text("$count", style = UzzuType.MonoPlain, color = UzzuColors.TextSecondary, maxLines = 1)
            Icon(
                UzzuIcons.ChevronDown,
                contentDescription = null,
                tint = UzzuColors.TextPrimary,
                modifier = Modifier.size(14.dp).then(if (open) Modifier.rotate(180f) else Modifier),
            )
        }
    }
}

/** O1c: every non-empty group of the listed section, in place of the items. */
@Composable
private fun GroupList(section: Section, groups: List<OverlayGroup>, tab: String, option: @Composable (OverlayGroup) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        MonoTag("// PICK GROUP · ${section.label()}", Modifier.padding(horizontal = 10.dp))
        Spacer(Modifier.height(Dimens.S2))
        Column(
            // Padding keeps the 1.05 focus scale inside the scroll clip.
            Modifier.fillMaxSize().testTag("grouplist:$tab").verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.S2),
        ) {
            for (g in groups) option(g)
        }
    }
}

/** One group: name + item count. Current = Active fill. Transparent at rest, Surface when focused. */
@Composable
private fun GroupOption(group: OverlayGroup, current: Boolean, onClick: () -> Unit, modifier: Modifier) {
    UzzuCard(
        onClick, UzzuShapes.Row, modifier.fillMaxWidth().height(32.dp),
        container = if (current) UzzuColors.Active else Color.Transparent,
        focusedContainer = if (current) UzzuColors.Active else UzzuColors.Surface,
        restBorder = Color.Transparent,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.S4),
        ) {
            Text(group.name.uppercase(), style = UzzuType.BodyLarge, color = UzzuColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${group.count}", style = UzzuType.MonoPlain, color = if (current) UzzuColors.TextPrimary else UzzuColors.TextSecondary, maxLines = 1)
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
