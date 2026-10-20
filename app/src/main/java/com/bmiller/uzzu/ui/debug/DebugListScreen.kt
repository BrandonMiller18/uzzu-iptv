package com.bmiller.uzzu.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.bmiller.uzzu.playlist.Channel
import com.bmiller.uzzu.playlist.FeedSide
import com.bmiller.uzzu.playlist.Game
import com.bmiller.uzzu.playlist.Playlist
import com.bmiller.uzzu.ui.components.KeyHint
import com.bmiller.uzzu.ui.components.MonoTag
import com.bmiller.uzzu.ui.components.UzzuButton
import com.bmiller.uzzu.ui.components.UzzuRowCard
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuType

/** Focus target to restore when coming back from the player. */
data class DebugFocus(val key: String)

/**
 * Temporary M1 list (replaced by S1–S3 in M3): every Live-Games entry with Home/Away buttons and
 * every Live-Channels entry, in one D-pad navigable column.
 */
@Composable
fun DebugListScreen(
    playlist: Playlist?,
    status: String,
    error: String?,
    listState: LazyListState,
    restoreFocus: DebugFocus?,
    onPlayGame: (Game, FeedSide) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onRefresh: () -> Unit,
    onEditUrl: () -> Unit,
) {
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }
    val firstKey = playlist?.let { p ->
        p.games.firstOrNull()?.let { g -> "game:${g.id}:${if (g.homeFeed != null) "H" else "A"}" }
            ?: p.channels.firstOrNull()?.let { "ch:${it.playlistIndex}" }
    } ?: "refresh"

    LaunchedEffect(playlist, restoreFocus) {
        val key = restoreFocus?.key?.takeIf { it in requesters } ?: firstKey
        runCatching { requester(key).requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(UzzuColors.Background)
            .padding(horizontal = Dimens.SafeH, vertical = Dimens.SafeV),
        verticalArrangement = Arrangement.spacedBy(Dimens.S5),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MonoTag("M1 // DEBUG LIST")
                Text("UZZU", style = UzzuType.Display, color = UzzuColors.TextPrimary)
                MonoTag(status)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.S5)) {
                UzzuButton("Refresh", onRefresh, Modifier.focusRequester(requester("refresh")))
                UzzuButton("Playlist URL", onEditUrl)
            }
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(UzzuColors.Outline))
        if (error != null) Text(error, style = UzzuType.Body, color = UzzuColors.Error)

        if (playlist != null) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.GridRowGapRows),
                contentPadding = PaddingValues(vertical = Dimens.S4, horizontal = Dimens.S6),
            ) {
                item(key = "h-games") { MonoTag("// LIVE GAMES · ${playlist.games.size}") }
                itemsIndexed(playlist.games, key = { _, g -> "game:${g.id}" }) { _, game ->
                    GameRow(game, ::requester, onPlayGame)
                }
                item(key = "h-channels") { MonoTag("// LIVE CHANNELS · ${playlist.channels.size}") }
                itemsIndexed(playlist.channels, key = { _, c -> "ch:${c.playlistIndex}" }) { i, ch ->
                    UzzuRowCard(
                        onClick = { onPlayChannel(ch) },
                        modifier = Modifier.width(Dimens.GameCardWidth).focusRequester(requester("ch:${ch.playlistIndex}")),
                    ) {
                        Text("%02d".format(i + 1), style = UzzuType.Mono, color = UzzuColors.TextSecondary)
                        LogoSlot(ch.logoUrl)
                        Text(ch.name.uppercase(), style = UzzuType.BodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            KeyHint("OK", "Play")
            KeyHint("BACK", "Exit")
        }
    }
}

@Composable
private fun GameRow(game: Game, requester: (String) -> FocusRequester, onPlay: (Game, FeedSide) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(Dimens.ChannelRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.S5),
    ) {
        LogoSlot(game.awayLogoUrl)
        LogoSlot(game.homeLogoUrl)
        Text(
            game.title.uppercase(),
            style = UzzuType.BodyLarge,
            color = UzzuColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        UzzuButton(
            "Home",
            onClick = { onPlay(game, FeedSide.HOME) },
            enabled = game.homeFeed != null,
            modifier = Modifier.focusRequester(requester("game:${game.id}:H")),
        )
        UzzuButton(
            "Away",
            onClick = { onPlay(game, FeedSide.AWAY) },
            enabled = game.awayFeed != null,
            modifier = Modifier.focusRequester(requester("game:${game.id}:A")),
        )
    }
}

/** HANDOFF rule 12: fixed 32 dp square, 1 dp Outline border, Background fill. */
@Composable
private fun LogoSlot(url: String?) {
    Box(
        Modifier.size(Dimens.LogoSmall).background(UzzuColors.Background).border(Dimens.RestBorder, UzzuColors.Outline),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(Dimens.LogoSmall - 4.dp),
            )
        }
    }
}
