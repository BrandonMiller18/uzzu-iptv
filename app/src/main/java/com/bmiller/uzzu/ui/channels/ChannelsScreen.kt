package com.bmiller.uzzu.ui.channels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bmiller.uzzu.player.PlayContext
import com.bmiller.uzzu.player.Section
import com.bmiller.uzzu.playlist.Channel
import com.bmiller.uzzu.playlist.ChannelGroup
import com.bmiller.uzzu.ui.components.ChannelRow
import com.bmiller.uzzu.ui.components.FocusGrid
import com.bmiller.uzzu.ui.components.KeyHints
import com.bmiller.uzzu.ui.components.ListMemory
import com.bmiller.uzzu.ui.components.LoadingTag
import com.bmiller.uzzu.ui.components.MonoTag
import com.bmiller.uzzu.ui.components.ScreenHeader
import com.bmiller.uzzu.ui.components.UzzuTabRow
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors

const val TAB_ALL_CHANNELS = "All"

/** Channels in display order: sub-group order, then playlist order (= All tab = concatenated group tabs). */
fun orderedChannels(channels: List<Channel>): List<Channel> =
    channels.sortedWith(compareBy({ it.subGroup.ordinal }, { it.playlistIndex }))

/** All, then each non-empty sub-group in PLAN order. */
fun channelTabs(channels: List<Channel>): List<String> {
    if (channels.isEmpty()) return emptyList()
    val present = channels.mapTo(HashSet()) { it.subGroup }
    return listOf(TAB_ALL_CHANNELS) + ChannelGroup.entries.filter { it in present }.map { it.label }
}

fun channelsInTab(ordered: List<Channel>, tab: String): List<Channel> =
    if (tab == TAB_ALL_CHANNELS) ordered else ordered.filter { it.subGroup.label == tab }

/** Grid key: playlist index is unique even when two entries share a name / tvg-id. */
fun Channel.gridKey(): String = "ch:$playlistIndex"

/**
 * S3 Channels list (Channels.dc.html): sub-group tabs, 2-column grid of channel rows with a 2-digit
 * index (position within the tab). Initial focus: first row. Select plays. Back → S1.
 */
@Composable
fun ChannelsScreen(
    channels: List<Channel>?,
    syncLabel: String,
    memory: ListMemory,
    onPlay: (Channel, PlayContext) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val ordered = remember(channels) { orderedChannels(channels.orEmpty()) }
    val tabs = remember(ordered) { channelTabs(ordered) }
    val tab = memory.tab?.takeIf { it in tabs } ?: TAB_ALL_CHANNELS

    Column(
        Modifier.fillMaxSize().background(UzzuColors.Background).padding(vertical = Dimens.SafeV),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("02 // CHANNELS", "Live Channels", Modifier.padding(horizontal = Dimens.SafeH)) { MonoTag(syncLabel) }
        when {
            channels == null -> LoadingTag(Modifier.padding(horizontal = Dimens.SafeH))
            tabs.isEmpty() -> MonoTag("// NO CHANNELS IN THE PLAYLIST", Modifier.padding(horizontal = Dimens.SafeH))
            else -> {
                val items = remember(ordered, tab) { channelsInTab(ordered, tab) }
                UzzuTabRow(tabs, tabs.indexOf(tab), onSelect = { memory.tab = tabs[it] }, Modifier.padding(horizontal = Dimens.SafeH), gap = Dimens.S1, horizontalPadding = 7.dp)
                FocusGrid(
                    items = items,
                    key = { it.gridKey() },
                    initialFocusKey = remember { memory.focusKey?.takeIf { k -> items.any { it.gridKey() == k } } ?: items.firstOrNull()?.gridKey() },
                    resetKey = tab,
                    memory = memory,
                    rowGap = Dimens.GridRowGapRows,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { ch, index, m ->
                    ChannelRow(
                        index = index + 1,
                        name = ch.name,
                        logoUrl = ch.logoUrl,
                        onClick = { onPlay(ch, PlayContext(Section.CHANNELS, tab, items.map { it.key }, ch.key)) },
                        modifier = m.testTag(ch.gridKey()),
                    )
                }
            }
        }
        if (channels.isNullOrEmpty()) Box(Modifier.weight(1f))
        KeyHints("OK" to "Play channel", "BACK" to "Home", modifier = Modifier.padding(horizontal = Dimens.SafeH))
    }
}
