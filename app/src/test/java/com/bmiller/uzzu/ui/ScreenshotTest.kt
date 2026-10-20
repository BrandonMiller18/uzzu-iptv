package com.bmiller.uzzu.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.bmiller.uzzu.data.PlaylistState
import com.bmiller.uzzu.playlist.League
import com.bmiller.uzzu.ui.components.ChannelRow
import com.bmiller.uzzu.ui.components.GameCard
import com.bmiller.uzzu.ui.components.GameCardData
import com.bmiller.uzzu.ui.components.GameCardState
import com.bmiller.uzzu.ui.components.InitialFocus
import com.bmiller.uzzu.ui.components.KeyHints
import com.bmiller.uzzu.ui.components.LiveChip
import com.bmiller.uzzu.ui.components.LoadingTag
import com.bmiller.uzzu.ui.components.MonoTag
import com.bmiller.uzzu.ui.components.PlayingChip
import com.bmiller.uzzu.ui.components.UzzuButton
import com.bmiller.uzzu.ui.components.UzzuIcons
import com.bmiller.uzzu.ui.components.UzzuTabRow
import com.bmiller.uzzu.ui.feed.FeedPickerContent
import com.bmiller.uzzu.ui.settings.UrlEditDialogContent
import com.bmiller.uzzu.ui.setup.SetupScreen
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuTheme
import com.bmiller.uzzu.update.UpdateState
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM screenshots via Robolectric + Roborazzi at the Fire TV's 960x540 dp xhdpi screen.
 * `./gradlew recordRoborazziDebug` writes PNGs to app/build/outputs/roborazzi/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun setUp() = UiFixtures.pinLocale()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    private fun app(route: Route, state: AppState = UiFixtures.state(), name: String) {
        compose.setContent { UzzuTheme { AppContent(Navigator(route), state, FakeActions()) { } } }
        compose.waitForIdle()
        shot(name)
    }

    @Test fun s0Setup() {
        compose.setContent { UzzuTheme { SetupScreen(onSave = { null }) } }
        shot("s0_setup")
    }

    @Test fun s1Home() = app(Route.Home, name = "s1_home")

    @Test fun s1HomeUpdateAvailable() =
        app(Route.Home, UiFixtures.state().copy(update = UpdateState.Available("1.1.0", "1.0.0")), "s1_home_update")

    @Test fun s1HomeLoading() = app(Route.Home, UiFixtures.state().copy(model = PlaylistState.Loading), "s1_home_loading")

    @Test fun s2Games() = app(Route.Games, name = "s2_games")

    @Test fun s2GamesFixtureOnly() =
        app(Route.Games, UiFixtures.state(UiFixtures.playlist(extras = emptyList(), liveIn = setOf(League.MLB, League.NFL))), "s2_games_fixture")

    @Test fun s2GamesEmpty() =
        app(Route.Games, UiFixtures.state(UiFixtures.playlist().let { it.copy(games = emptyList()) }), "s2_games_empty")

    @Test fun s3Channels() = app(Route.Channels, name = "s3_channels")

    @Test fun s7Settings() = app(Route.Settings, name = "s7_settings")

    /** S4 over S2, captured as the real Dialog window. */
    @Test fun s4FeedPicker() {
        val playlist = UiFixtures.playlist()
        compose.setContent { UzzuTheme { AppContent(Navigator(Route.Games), UiFixtures.state(playlist), FakeActions()) { } } }
        compose.waitForIdle()
        compose.key(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        captureScreenRoboImage("build/outputs/roborazzi/s4_feed_picker.png")
    }

    /** S4 content alone (deterministic, no window composition). */
    @Test fun s4FeedPickerContent() {
        val game = UiFixtures.playlist().games.first { it.title == "Washington Commanders @ San Francisco 49ers" }
        compose.setContent { UzzuTheme { Box(Modifier.fillMaxSize().background(UzzuColors.Background)) { FeedPickerContent(game) { } } } }
        shot("s4_feed_picker_content")
    }

    @Test fun s7UrlEditDialog() {
        compose.setContent {
            UzzuTheme { Box(Modifier.fillMaxSize().background(UzzuColors.Background)) { UrlEditDialogContent(com.bmiller.uzzu.ui.setup.maskPlaylistUrlForDisplay(UiFixtures.state().playlistUrl), { null }, {}) } }
        }
        shot("s7_url_edit")
    }

    private val cards = listOf(
        GameCardData(GameCardState.Scheduled, "Washington Commanders", "San Francisco 49ers", null, null, "NFL", "7:15 PM"),
        GameCardData(GameCardState.Live, "Anaheim Ducks", "New York Rangers", null, null, "NHL", "2ND 8:14"),
        GameCardData(GameCardState.Final, "Pittsburgh Penguins", "Utah Mammoth", null, null, "NHL", "FINAL"),
        GameCardData(GameCardState.Ungrouped, "Special Event Feed", "", null, null, "", ""),
    )

    /** Each GameCard state, unfocused (left) and focused (right). */
    @Test fun gameCardStates() {
        var current by mutableStateOf(cards.first())
        compose.setContent { UzzuTheme { Box(Modifier.fillMaxSize().background(UzzuColors.Background)) { Pair { m -> GameCard(current, {}, m) } } } }
        cards.forEach { data ->
            current = data
            compose.waitForIdle()
            shot("component_gamecard_${data.state.name.lowercase()}")
        }
    }

    @Test fun componentSheet() {
        compose.setContent {
            UzzuTheme {
                Column(Modifier.fillMaxSize().background(UzzuColors.Background).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Pair { m -> ChannelRow(1, "FanDuel Sports Network Wisconsin Extra", null, {}, m) }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        ChannelRow(2, "NFL Network", null, {}, playing = true)
                        LiveChip()
                        PlayingChip()
                        LoadingTag()
                        MonoTag("NHL")
                    }
                    UzzuTabRow(listOf("All", "MLB", "NHL", "Ungrouped"), 1, {})
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        UzzuButton("Refresh", {}, icon = UzzuIcons.Refresh)
                        UzzuButton("Retry", {})
                    }
                    KeyHints("OK" to "Open", "BACK" to "Exit app")
                }
            }
        }
        compose.waitForIdle()
        shot("component_sheet")
    }

    @Test fun tabFocused() {
        compose.setContent {
            UzzuTheme {
                var selected by remember { mutableStateOf(0) }
                val below = remember { FocusRequester() }
                Column(Modifier.fillMaxSize().background(UzzuColors.Background).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    UzzuTabRow(listOf("All", "MLB", "NHL", "NFL", "Ungrouped"), selected, { selected = it })
                    UzzuButton("Below", {}, Modifier.focusRequester(below))
                }
                InitialFocus(below)
            }
        }
        compose.waitForIdle()
        compose.key(android.view.KeyEvent.KEYCODE_DPAD_UP)
        compose.key(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, 2)
        shot("component_tab_focused")
    }

    /** Renders [item] twice side by side; the right copy is focused. */
    @Composable
    private fun Pair(item: @Composable (Modifier) -> Unit) {
        val r = remember { FocusRequester() }
        Row(Modifier.background(UzzuColors.Background).padding(horizontal = Dimens.SafeH - 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(Dimens.GridColumnGap - 24.dp)) {
            Box(Modifier.padding(12.dp)) { item(Modifier) }
            Box(Modifier.padding(12.dp)) { item(Modifier.focusRequester(r)) }
        }
        InitialFocus(r)
    }
}
