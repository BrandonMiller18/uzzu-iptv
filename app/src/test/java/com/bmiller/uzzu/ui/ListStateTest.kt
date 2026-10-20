package com.bmiller.uzzu.ui

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.bmiller.uzzu.data.PlaylistState
import com.bmiller.uzzu.playlist.FeedSide
import com.bmiller.uzzu.playlist.GameStatus
import com.bmiller.uzzu.playlist.League
import com.bmiller.uzzu.playlist.Playlist
import com.bmiller.uzzu.ui.channels.channelTabs
import com.bmiller.uzzu.ui.channels.channelsInTab
import com.bmiller.uzzu.ui.channels.gridKey
import com.bmiller.uzzu.ui.channels.orderedChannels
import com.bmiller.uzzu.ui.games.firstNotFinal
import com.bmiller.uzzu.ui.theme.UzzuTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * QA M3-03/07/08/13: model updates while an item is focused, failed and late loads on S2/S3, the
 * failed-refresh line on S1, and the D-pad paths the first round of tests didn't walk.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class ListStateTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val actions = FakeActions()

    @Before fun setUp() = UiFixtures.pinLocale()

    private fun launch(route: Route, initial: AppState = UiFixtures.state()): Pair<Navigator, MutableState<AppState>> {
        val nav = Navigator(Route.Home)
        if (route != Route.Home) nav.push(route)
        val state = mutableStateOf(initial)
        compose.setContent { UzzuTheme { AppContent(nav, state.value, actions) { } } }
        compose.waitForIdle()
        return nav to state
    }

    private fun loaded(p: Playlist) = UiFixtures.state(p)

    private fun texts(): List<String> =
        compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    // ---- M3-03: model updates while focused ----

    @Test fun `focused tab vanishing on a model update refocuses the selected tab`() {
        val playlist = UiFixtures.playlist()
        val (_, state) = launch(Route.Games, loaded(playlist))
        compose.key(KeyEvent.KEYCODE_DPAD_UP)
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT, 6)
        assertEquals("tab:Ungrouped", compose.focusedTag())
        // The schedule poll moves the Ungrouped game into a league: the tab disappears.
        state.value = loaded(playlist.copy(games = playlist.games.filter { it.league != League.UNGROUPED }))
        compose.waitForIdle()
        assertEquals("tab:All", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("tab:MLB", compose.focusedTag())
    }

    @Test fun `focused card vanishing on a model update refocuses the first card that isn't Final`() {
        val playlist = UiFixtures.playlist()
        val (_, state) = launch(Route.Games, loaded(playlist))
        val focused = compose.focusedTag()!!
        val remaining = playlist.games.filter { "game:${it.id}" != focused }
        state.value = loaded(playlist.copy(games = remaining))
        compose.waitForIdle()
        assertEquals("game:${firstNotFinal(remaining)!!.id}", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_UP, 6)
        assertTrue(compose.focusedTag()!!.startsWith("tab:"))
    }

    @Test fun `focused channel row vanishing on a refresh refocuses the first row`() {
        val playlist = UiFixtures.playlist()
        val (_, state) = launch(Route.Channels, loaded(playlist))
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        val focused = compose.focusedTag()!!
        val remaining = playlist.channels.filter { it.gridKey() != focused }
        state.value = loaded(playlist.copy(channels = remaining))
        compose.waitForIdle()
        assertEquals(orderedChannels(remaining).first().gridKey(), compose.focusedTag())
    }

    @Test fun `a model update that keeps the focused card keeps focus on it`() {
        val playlist = UiFixtures.playlist()
        val (_, state) = launch(Route.Games, loaded(playlist))
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        val focused = compose.focusedTag()
        state.value = loaded(playlist.copy(games = playlist.games.map { it.copy() }))
        compose.waitForIdle()
        assertEquals(focused, compose.focusedTag())
    }

    // ---- M3-07: S2/S3 loading and failed loads ----

    @Test fun `S2 opened while loading focuses the first card once loaded`() {
        val playlist = UiFixtures.playlist()
        val (_, state) = launch(Route.Games, UiFixtures.state().copy(model = PlaylistState.Loading))
        assertTrue(texts().contains("LOADING"))
        state.value = loaded(playlist)
        compose.waitForIdle()
        assertEquals("game:${firstNotFinal(playlist.games)!!.id}", compose.focusedTag())
    }

    @Test fun `S2 shows NO DATA with a focused Retry after a failed load`() = assertFailedLoad(Route.Games, "game:")

    @Test fun `S3 shows NO DATA with a focused Retry after a failed load`() = assertFailedLoad(Route.Channels, "ch:")

    private fun assertFailedLoad(route: Route, itemPrefix: String) {
        val (_, state) = launch(route, UiFixtures.state().copy(model = PlaylistState.Failed("Playlist request returned HTTP 500")))
        assertTrue(texts().contains("// NO DATA"))
        assertTrue(texts().contains("Playlist request returned HTTP 500"))
        assertTrue(texts().contains("RETRY"))
        assertEquals("list:retry", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, actions.refreshes)
        state.value = UiFixtures.state()
        compose.waitForIdle()
        assertTrue(compose.focusedTag()!!.startsWith(itemPrefix))
    }

    // ---- M3-08: failed manual refresh on S1 ----

    @Test fun `S1 shows a failed refresh in the sync line and the reason`() {
        val ok = UiFixtures.state()
        val (_, state) = launch(Route.Home, ok)
        assertEquals(listOf("LAST SYNC 7:10 PM"), texts().filter { it.contains("SYNC") })
        state.value = ok.copy(lastFetchAtMillis = UiFixtures.FETCHED_AT + 60_000, lastFetchResult = "FAILED Playlist request returned HTTP 503")
        compose.waitForIdle()
        assertTrue(texts().contains("SYNC FAILED · LAST OK 7:10 PM"))
        assertTrue(texts().contains("Refresh failed: Playlist request returned HTTP 503"))
        // Counts stay (the old playlist is still shown).
        assertTrue(texts().contains("96 CHANNELS"))
        compose.onNodeWithTag("tile:games").assertExists()
    }

    // ---- M3-13: D-pad paths ----

    @Test fun `single-feed game skips the feed picker and plays its only feed`() {
        val playlist = UiFixtures.playlist()
        val (nav, _) = launch(Route.Games, loaded(playlist))
        compose.key(KeyEvent.KEYCODE_DPAD_UP, 6)
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT, 6)
        assertEquals("tab:Ungrouped", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        val ufc = playlist.games.single { it.league == League.UNGROUPED }
        assertEquals("game:${ufc.id}", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(!exists("feed:home") && !exists("feed:away"))
        assertEquals(Route.Player, nav.current)
        val (item, ctx) = actions.played.single()
        val side = if (ufc.homeFeed != null) FeedSide.HOME else FeedSide.AWAY
        assertEquals(ufc.feed(side)!!.url, item.url)
        assertEquals(side, ctx.side)
    }

    @Test fun `feed picker Down moves Home to Away and Away plays the away feed`() {
        val playlist = UiFixtures.playlist()
        val (nav, _) = launch(Route.Games, loaded(playlist))
        val game = playlist.games.single { "game:${it.id}" == compose.focusedTag() }
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("feed:home", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("feed:away", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(Route.Player, nav.current)
        val (item, ctx) = actions.played.single()
        assertEquals(game.awayFeed!!.url, item.url)
        assertEquals(FeedSide.AWAY, ctx.side)
    }

    @Test fun `every S3 tab is reachable and swaps the grid`() {
        val playlist = UiFixtures.playlist()
        launch(Route.Channels, loaded(playlist))
        val ordered = orderedChannels(playlist.channels)
        val tabs = channelTabs(ordered)
        compose.key(KeyEvent.KEYCODE_DPAD_UP)
        for ((i, tab) in tabs.withIndex()) {
            if (i > 0) compose.key(KeyEvent.KEYCODE_DPAD_RIGHT)
            assertEquals("tab:$tab", compose.focusedTag())
            val first = channelsInTab(ordered, tab).first().gridKey()
            assertTrue("first row of $tab shown", exists(first))
        }
        // Down lands in the last tab's grid.
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue(compose.focusedTag() in channelsInTab(ordered, tabs.last()).map { it.gridKey() })
    }

    @Test fun `Up from the right column lands on the selected tab, not the one above`() {
        val playlist = UiFixtures.playlist()
        launch(Route.Games, loaded(playlist))
        compose.key(KeyEvent.KEYCODE_DPAD_UP, 6)
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT, 2)
        assertEquals("tab:NHL", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        val nhl = playlist.games.filter { it.league == League.NHL }
        assertEquals("game:${nhl[1].id}", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals("tab:NHL", compose.focusedTag())
    }

    @Test fun `S2 initial focus falls back to the first card when every game is Final`() {
        val base = UiFixtures.playlist(extras = emptyList())
        val playlist = base.copy(games = base.games.map { it.copy(status = GameStatus.Final) })
        launch(Route.Games, loaded(playlist))
        assertEquals("game:${playlist.games.first().id}", compose.focusedTag())
    }
}
