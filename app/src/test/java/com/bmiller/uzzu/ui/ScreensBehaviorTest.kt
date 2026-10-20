package com.bmiller.uzzu.ui

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.espresso.Espresso
import androidx.compose.ui.test.performTextReplacement
import com.bmiller.uzzu.player.Section
import com.bmiller.uzzu.playlist.GameStatus
import com.bmiller.uzzu.playlist.League
import com.bmiller.uzzu.ui.channels.gridKey
import com.bmiller.uzzu.ui.channels.orderedChannels
import com.bmiller.uzzu.ui.components.InitialFocus
import com.bmiller.uzzu.ui.games.firstNotFinal
import com.bmiller.uzzu.ui.theme.UzzuTheme
import com.bmiller.uzzu.playlist.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** M3 acceptance: D-pad reachability, initial focus, Back targets, hidden zero-game tabs, no scores. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class ScreensBehaviorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val actions = FakeActions()

    @Before fun setUp() = UiFixtures.pinLocale()

    private fun launch(route: Route, playlist: Playlist = UiFixtures.playlist(), start: List<Route> = emptyList()): Navigator {
        val nav = Navigator(Route.Home)
        (start + route).forEach { if (it != Route.Home) nav.push(it) }
        compose.setContent {
            UzzuTheme {
                AppContent(nav, UiFixtures.state(playlist), actions) { exit ->
                    val r = remember { FocusRequester() }
                    Box(Modifier.fillMaxSize().testTag("player").focusRequester(r).focusable())
                    BackHandler(onBack = exit.toList)
                    InitialFocus(r)
                }
            }
        }
        compose.waitForIdle()
        return nav
    }

    /** The remote's Back key, delivered to the focused window (dialogs included). */
    private fun back() {
        Espresso.pressBackUnconditionally()
        compose.waitForIdle()
    }

    private fun tags(prefix: String): List<String> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.TestTag), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            .filter { it.startsWith(prefix) }

    // ---- initial focus (HANDOFF screen table) ----

    @Test fun `S1 initial focus is the Live Games tile`() {
        launch(Route.Home)
        compose.onNodeWithTag("tile:games").assertIsFocused()
    }

    @Test fun `S2 initial focus is the first card that isn't Final`() {
        // NHL not live: Ducks, Avalanche, Sharks are Final and sort first; Guardians @ Rays (MLB, live) is next.
        val playlist = UiFixtures.playlist(liveIn = setOf(League.MLB, League.NFL))
        assertEquals(GameStatus.Final, playlist.games.first().status)
        launch(Route.Games, playlist)
        val expected = firstNotFinal(playlist.games)!!
        assertEquals("Cleveland Guardians @ Tampa Bay Rays", expected.title)
        assertEquals("game:${expected.id}", compose.focusedTag())
    }

    @Test fun `S3 initial focus is the first channel row`() {
        val playlist = UiFixtures.playlist()
        launch(Route.Channels, playlist)
        assertEquals(orderedChannels(playlist.channels).first().gridKey(), compose.focusedTag())
    }

    @Test fun `S4 initial focus is the Home feed and Back closes it`() {
        val nav = launch(Route.Games)
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("feed:home", compose.focusedTag())
        back()
        assertTrue(compose.onAllNodesWithTag("feed:home", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertEquals(Route.Games, nav.current)
        assertTrue(actions.played.isEmpty())
    }

    @Test fun `S7 initial focus is Playlist URL`() {
        launch(Route.Settings)
        compose.onNodeWithTag("settings:url").assertIsFocused()
    }

    // ---- Back targets ----

    @Test fun `Back on S1 exits the app`() {
        launch(Route.Home)
        back()
        assertEquals(1, actions.exits)
    }

    @Test fun `Back on S2, S3 and S7 returns to S1`() {
        val nav = launch(Route.Games)
        back()
        assertEquals(Route.Home, nav.current)
        compose.onNodeWithTag("tile:games").assertIsFocused()
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(Route.Channels, nav.current)
        back()
        assertEquals(Route.Home, nav.current)
        // Returning lands on the tile the user left from.
        compose.onNodeWithTag("tile:channels").assertIsFocused()
        compose.key(KeyEvent.KEYCODE_DPAD_UP)
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("settings", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(Route.Settings, nav.current)
        back()
        assertEquals(Route.Home, nav.current)
    }

    @Test fun `Select on a channel plays it with its group, Back returns to the same row`() {
        val playlist = UiFixtures.playlist()
        val nav = launch(Route.Channels, playlist)
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        val focused = compose.focusedTag()!!
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(Route.Player, nav.current)
        val (item, ctx) = actions.played.single()
        val ordered = orderedChannels(playlist.channels)
        val channel = ordered.single { it.gridKey() == focused }
        assertEquals(channel.url, item.url)
        assertEquals(Section.CHANNELS, ctx.section)
        assertEquals("All", ctx.group)
        assertEquals(ordered.map { it.key }, ctx.itemKeys)
        assertEquals(channel.key, ctx.currentKey)

        back()
        assertEquals(Route.Channels, nav.current)
        assertEquals(1, actions.stops)
        assertEquals(focused, compose.focusedTag())
    }

    @Test fun `game Select opens the picker, Home plays the home feed in the tab's group, Back returns to S2`() {
        val playlist = UiFixtures.playlist()
        val nav = launch(Route.Games, playlist)
        // Move to the NHL tab and pick its first game.
        compose.key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals("tab:All", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT, 2)
        assertEquals("tab:NHL", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        val nhl = playlist.games.filter { it.league == League.NHL }
        assertEquals("game:${nhl.first().id}", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER) // Home feed
        assertEquals(Route.Player, nav.current)
        val (item, ctx) = actions.played.single()
        assertEquals(nhl.first().homeFeed!!.url, item.url)
        assertEquals(Section.GAMES, ctx.section)
        assertEquals("NHL", ctx.group)
        assertEquals(nhl.map { it.id }, ctx.itemKeys)
        assertEquals(com.bmiller.uzzu.playlist.FeedSide.HOME, ctx.side)

        back()
        assertEquals(Route.Games, nav.current)
        assertEquals("game:${nhl.first().id}", compose.focusedTag())
    }

    @Test fun `S2 holds a polling handle only while visible`() {
        val nav = launch(Route.Games)
        assertEquals(1, actions.pollers)
        back()
        assertEquals(Route.Home, nav.current)
        assertEquals(0, actions.pollers)
    }

    // ---- tabs ----

    @Test fun `zero-game league tabs are hidden`() {
        launch(Route.Games, UiFixtures.playlist(extras = emptyList()))
        val shown = tags("tab:")
        assertEquals(listOf("tab:All", "tab:MLB", "tab:NHL", "tab:NFL"), shown)
    }

    @Test fun `every league tab shows when it has games, Ungrouped last`() {
        launch(Route.Games)
        assertEquals(listOf("tab:All", "tab:MLB", "tab:NHL", "tab:NBA", "tab:NFL", "tab:NCAAF", "tab:Ungrouped"), tags("tab:"))
    }

    @Test fun `empty channel sub-groups are hidden`() {
        val playlist = UiFixtures.playlist()
        launch(Route.Channels, playlist)
        val groups = playlist.channels.map { it.subGroup }.toSet()
        assertEquals(listOf("tab:All") + com.bmiller.uzzu.playlist.ChannelGroup.entries.filter { it in groups }.map { "tab:${it.label}" }, tags("tab:"))
        assertFalse("tab:Other" in tags("tab:"))
    }

    // ---- D-pad reachability (no traps) ----

    /** Walks column 0 down, column 1 up; every item must take focus, and Up from the top reaches the tabs. */
    private fun assertGridReachable(expected: List<String>) {
        val visited = LinkedHashSet<String>()
        compose.focusedTag()?.let(visited::add)
        repeat(expected.size) {
            compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.focusedTag()?.let(visited::add)
        }
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.focusedTag()?.let(visited::add)
        repeat(expected.size) {
            compose.key(KeyEvent.KEYCODE_DPAD_UP)
            compose.focusedTag()?.let(visited::add)
        }
        assertEquals(expected.toSet(), visited.filter { !it.startsWith("tab:") }.toSet())
        assertTrue("Up from the grid reaches the tab row", compose.focusedTag()!!.startsWith("tab:"))
    }

    @Test fun `every channel row is reachable by D-pad`() {
        val playlist = UiFixtures.playlist()
        launch(Route.Channels, playlist)
        assertEquals(96, playlist.channels.size)
        assertGridReachable(orderedChannels(playlist.channels).map { it.gridKey() })
    }

    @Test fun `every game card is reachable by D-pad`() {
        val playlist = UiFixtures.playlist()
        launch(Route.Games, playlist)
        // Start from the first card.
        compose.key(KeyEvent.KEYCODE_DPAD_UP, 6)
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertGridReachable(playlist.games.map { "game:${it.id}" })
    }

    @Test fun `every tab is reachable and selecting it swaps the grid`() {
        val playlist = UiFixtures.playlist()
        launch(Route.Games, playlist)
        compose.key(KeyEvent.KEYCODE_DPAD_UP)
        val seen = mutableListOf(compose.focusedTag())
        repeat(6) {
            compose.key(KeyEvent.KEYCODE_DPAD_RIGHT)
            seen += compose.focusedTag()
        }
        assertEquals(listOf("tab:All", "tab:MLB", "tab:NHL", "tab:NBA", "tab:NFL", "tab:NCAAF", "tab:Ungrouped"), seen)
        val ungrouped = playlist.games.filter { it.league == League.UNGROUPED }.map { "game:${it.id}" }
        assertEquals(ungrouped, tags("game:"))
    }

    @Test fun `S1 tiles and header buttons are reachable`() {
        launch(Route.Home)
        val seen = mutableListOf(compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT); seen += compose.focusedTag()
        compose.key(KeyEvent.KEYCODE_DPAD_UP); seen += compose.focusedTag()
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT); seen += compose.focusedTag()
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN); seen += compose.focusedTag()
        assertEquals(listOf("tile:games", "tile:channels", "refresh", "settings", "tile:channels"), seen)
    }

    @Test fun `S7 rows are reachable`() {
        launch(Route.Settings)
        val seen = mutableListOf(compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN); seen += compose.focusedTag()
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN); seen += compose.focusedTag()
        assertEquals(listOf("settings:url", "settings:refresh", "settings:updates"), seen)
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, actions.updateChecks)
    }

    @Test fun `S7 URL dialog validates, saves and closes`() {
        launch(Route.Settings)
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("url:field").assertIsFocused()
        compose.onNodeWithTag("url:field").performTextReplacement("not a url")
        // Robolectric key events have no InputDevice, so the text field won't hand D-pad focus on
        // (it does on the device); press Save through its semantics action instead.
        compose.onNodeWithTag("url:save").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("url:error", useUnmergedTree = true).assertExists()
        assertTrue(actions.saved.isEmpty())
        compose.onNodeWithTag("url:field").performTextReplacement("https://example.test/list.m3u")
        compose.onNodeWithTag("url:save").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(listOf("https://example.test/list.m3u"), actions.saved)
        assertTrue(compose.onAllNodesWithTag("url:field").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("settings:url").assertIsFocused()
    }

    // ---- no scores ----

    @Test fun `no score text is rendered on S1, S2 or S4`() {
        // ESPN-like data: the recorded responses (with real score fields) + synthetic live games.
        val playlist = UiFixtures.playlist()
        val nav = launch(Route.Games, playlist)
        val texts = mutableListOf<String>()
        fun collect() {
            texts += compose.onAllNodes(androidx.compose.ui.test.hasText("", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        }
        // Every tab of S2.
        compose.key(KeyEvent.KEYCODE_DPAD_UP)
        repeat(7) { collect(); compose.key(KeyEvent.KEYCODE_DPAD_RIGHT) }
        compose.key(KeyEvent.KEYCODE_DPAD_LEFT, 7)
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER) // S4
        collect()
        back()
        back()
        assertEquals(Route.Home, nav.current)
        collect()

        assertTrue(texts.any { it == "2ND 8:14" } && texts.any { it == "TOP 5TH" } && texts.any { it == "FINAL" })
        // Scores look like "3-2" / "24 – 17" or a bare number; clock times and periods ("8:14", "Q3 4:12") are fine.
        val dashPair = Regex("""\d+\s*[-–—]\s*\d+""")
        val offenders = texts.filter { dashPair.containsMatchIn(it) || (it.isNotBlank() && it.trim().all(Char::isDigit)) }
        assertEquals(emptyList<String>(), offenders)
    }
}
