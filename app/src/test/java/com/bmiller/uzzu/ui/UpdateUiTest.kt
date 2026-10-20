package com.bmiller.uzzu.ui

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.bmiller.uzzu.ui.home.HomeScreen
import com.bmiller.uzzu.ui.theme.UzzuTheme
import com.bmiller.uzzu.update.UpdateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** M5: the S1 update bar (reachable by D-pad, focus survives Later/Install) and the S7 result line. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class UpdateUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var update by mutableStateOf<UpdateState>(UpdateState.Available("1.0.0", "0.9.0"))
    private var installs = 0

    @Before fun setUp() = UiFixtures.pinLocale()

    private fun home() {
        compose.setContent {
            UzzuTheme {
                HomeScreen(
                    counts = homeCounts(UiFixtures.state().model),
                    syncLabel = "LAST SYNC 7:10 PM",
                    error = null,
                    update = update,
                    onGames = {}, onChannels = {}, onRefresh = {}, onSettings = {},
                    // Mirror GitHubUpdateChecker: Install swaps the buttons for progress, Later hides the bar.
                    onInstallUpdate = {
                        installs++
                        update = (update as UpdateState.Available).copy(progress = "DOWNLOADING 0.0 / 9.9 MB · 0%")
                    },
                    onDismissUpdate = { update = (update as UpdateState.Available).copy(dismissed = true) },
                    onExit = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun `bar shows only when an update is available and not dismissed`() {
        update = UpdateState.UpToDate(0)
        home()
        assertTrue(!exists("update:install"))
        update = UpdateState.Available("1.0.0", "0.9.0")
        compose.waitForIdle()
        assertTrue(exists("update:install"))
        compose.onNodeWithText("UPDATE AVAILABLE: VERSION 1.0.0").assertExists()
        compose.onNodeWithText("You're on 0.9.0. Playback stops while it installs.").assertExists()
        update = UpdateState.Available("1.0.0", "0.9.0", dismissed = true)
        compose.waitForIdle()
        assertTrue(!exists("update:install"))
    }

    @Test fun `Later via D-pad hides the bar and focus lands on Live Games`() {
        home()
        compose.onNodeWithTag("tile:games").assertIsFocused()
        compose.key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals("update:install", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("update:later", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)

        assertTrue(!exists("update:later"))
        compose.onNodeWithTag("tile:games").assertIsFocused()
        // The D-pad still works.
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("tile:channels").assertIsFocused()
    }

    @Test fun `Install via D-pad shows progress and focus lands on Live Games`() {
        home()
        compose.key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals("update:install", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals(1, installs)
        assertTrue(!exists("update:install"))
        compose.onNodeWithTag("update:progress", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("tile:games").assertIsFocused()
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("tile:channels").assertIsFocused()
    }

    @Test fun `failed download shows the reason and offers Install again`() {
        update = UpdateState.Available("1.0.0", "0.9.0", error = "Update failed: size mismatch (5 of 9 bytes)")
        home()
        compose.onNodeWithText("Update failed: size mismatch (5 of 9 bytes)").assertExists()
        assertTrue(exists("update:install"))
    }

    // ---- S7 "Check for updates" result line ----

    @Test fun `S7 shows up to date, update available and error results`() {
        val nav = Navigator(Route.Home).apply { push(Route.Settings) }
        var s by mutableStateOf<UpdateState>(UpdateState.UpToDate(UiFixtures.FETCHED_AT))
        val actions = FakeActions()
        compose.setContent { UzzuTheme { AppContent(nav, UiFixtures.state().copy(update = s), actions) {} } }
        compose.waitForIdle()
        compose.onNodeWithText("UP TO DATE · CHECKED 7:10 PM").assertExists()

        compose.key(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        assertEquals("settings:updates", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, actions.updateChecks)

        s = UpdateState.Available("1.0.0", "0.9.0")
        compose.waitForIdle()
        compose.onNodeWithText("VERSION 1.0.0 AVAILABLE").assertExists()

        s = UpdateState.Failed("Update check failed: HTTP 503")
        compose.waitForIdle()
        compose.onNodeWithText("UPDATE CHECK FAILED: HTTP 503").assertExists()
    }
}
