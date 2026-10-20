package com.bmiller.uzzu.ui.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.Text
import com.bmiller.uzzu.player.PlayContext
import com.bmiller.uzzu.player.Section
import com.bmiller.uzzu.playlist.FeedSide
import com.bmiller.uzzu.playlist.Game
import com.bmiller.uzzu.playlist.GameStatus
import com.bmiller.uzzu.playlist.League
import com.bmiller.uzzu.ui.components.FocusGrid
import com.bmiller.uzzu.ui.components.GameCard
import com.bmiller.uzzu.ui.components.KeyHints
import com.bmiller.uzzu.ui.components.ListMemory
import com.bmiller.uzzu.ui.components.LoadingTag
import com.bmiller.uzzu.ui.components.MonoTag
import com.bmiller.uzzu.ui.components.ScreenHeader
import com.bmiller.uzzu.ui.components.UzzuTabRow
import com.bmiller.uzzu.ui.feed.FeedPicker
import com.bmiller.uzzu.ui.feed.needsFeedPicker
import com.bmiller.uzzu.ui.feed.singleFeedSide
import com.bmiller.uzzu.ui.label
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuType
import com.bmiller.uzzu.ui.toCardData

const val TAB_ALL = "All"

/** Tabs for [games]: All, then each league in PLAN order that has at least one game. None when empty. */
fun gameTabs(games: List<Game>): List<String> {
    if (games.isEmpty()) return emptyList()
    val present = games.mapTo(HashSet()) { it.league }
    return listOf(TAB_ALL) + League.entries.filter { it in present }.map { it.label }
}

fun gamesInTab(games: List<Game>, tab: String): List<Game> =
    if (tab == TAB_ALL) games else games.filter { it.league.label == tab }

/** HANDOFF: initial focus is the first card that isn't Final. */
fun firstNotFinal(games: List<Game>): Game? = games.firstOrNull { it.status != GameStatus.Final } ?: games.firstOrNull()

/**
 * S2 Games list (Games.dc.html). [games] is already sorted by the model (start time, Final in
 * place, Ungrouped last). Schedule polling runs while this screen is started. Select opens the
 * feed picker (skipped for single-feed games). Back → S1.
 */
@Composable
fun GamesScreen(
    games: List<Game>?,
    syncLabel: String,
    memory: ListMemory,
    startPolling: () -> AutoCloseable,
    onPlay: (Game, FeedSide, PlayContext) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    LifecycleStartEffect(Unit) {
        val polling = startPolling()
        onStopOrDispose { polling.close() }
    }
    var picking by remember { mutableStateOf<Game?>(null) }
    val tabs = remember(games) { gameTabs(games.orEmpty()) }
    val tab = memory.tab?.takeIf { it in tabs } ?: TAB_ALL

    Column(
        Modifier.fillMaxSize().background(UzzuColors.Background).padding(vertical = Dimens.SafeV),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("01 // GAMES", "Live Games", Modifier.padding(horizontal = Dimens.SafeH)) { MonoTag(syncLabel) }

        when {
            games == null -> LoadingTag(Modifier.padding(horizontal = Dimens.SafeH))
            tabs.isEmpty() -> Column(Modifier.padding(horizontal = Dimens.SafeH), verticalArrangement = Arrangement.spacedBy(Dimens.S2)) {
                MonoTag("// NO GAMES")
                Text("There are no games in the playlist right now. Games usually appear a few hours before start.", style = UzzuType.Body, color = UzzuColors.TextSecondary)
            }
            else -> {
                val items = remember(games, tab) { gamesInTab(games, tab) }
                val cards = remember(items) { items.associate { it.id to it.toCardData() } }
                UzzuTabRow(tabs, tabs.indexOf(tab), onSelect = { memory.tab = tabs[it] }, Modifier.padding(horizontal = Dimens.SafeH), gap = Dimens.S2)
                FocusGrid(
                    items = items,
                    key = { it.id },
                    initialFocusKey = remember { memory.focusKey?.takeIf { k -> items.any { it.id == k } } ?: firstNotFinal(items)?.id },
                    resetKey = tab,
                    memory = memory,
                    rowGap = Dimens.GridRowGapCards,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { game, _, m ->
                    GameCard(cards.getValue(game.id), onClick = {
                        if (game.needsFeedPicker()) {
                            picking = game
                        } else {
                            val side = game.singleFeedSide()
                            onPlay(game, side, playContext(tab, items, game, side))
                        }
                    }, modifier = m.testTag("game:${game.id}"))
                }
            }
        }
        if (games.isNullOrEmpty()) Box(Modifier.weight(1f))
        KeyHints("OK" to "Choose feed", "BACK" to "Home", modifier = Modifier.padding(horizontal = Dimens.SafeH))
    }

    picking?.let { game ->
        FeedPicker(
            game = game,
            onPick = { side ->
                picking = null
                onPlay(game, side, playContext(tab, gamesInTab(games.orEmpty(), tab), game, side))
            },
            onDismiss = { picking = null },
        )
    }
}

private fun playContext(tab: String, items: List<Game>, game: Game, side: FeedSide) =
    PlayContext(Section.GAMES, tab, items.map { it.id }, game.id, side)
