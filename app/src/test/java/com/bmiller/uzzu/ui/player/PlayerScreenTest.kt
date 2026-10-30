package com.bmiller.uzzu.ui.player

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.espresso.Espresso
import com.bmiller.uzzu.log.AppLogger
import com.bmiller.uzzu.player.FakePlaylistSource
import com.bmiller.uzzu.player.FakeScheduler
import com.bmiller.uzzu.player.FakeStreamPlayer
import com.bmiller.uzzu.player.PlayContext
import com.bmiller.uzzu.player.PlaybackManager
import com.bmiller.uzzu.player.Section
import com.bmiller.uzzu.player.groupKeys
import com.bmiller.uzzu.playlist.FeedSide
import com.bmiller.uzzu.ui.PlayerExit
import com.bmiller.uzzu.ui.UiFixtures
import com.bmiller.uzzu.ui.focusedTag
import com.bmiller.uzzu.ui.key
import com.bmiller.uzzu.ui.theme.UzzuTheme
import com.bmiller.uzzu.ui.Snapshots
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S5 keys, O1 and S6 with the real [PlaybackManager] over a fake player; plus their screenshots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class PlayerScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val tmp = TemporaryFolder()

    private val playlist by lazy { UiFixtures.playlist() }
    private val sched = FakeScheduler()
    private val player = FakeStreamPlayer()
    private val source by lazy { FakePlaylistSource(playlist, sched.now) }
    private lateinit var manager: PlaybackManager
    private var toList = 0
    private var toMenu = 0
    private var polls = 0

    @Before fun setUp() {
        UiFixtures.pinLocale()
        manager = PlaybackManager(player, source, source.state, { "Wi-Fi · Connected" }, AppLogger(tmp.newFolder()), CoroutineScope(Dispatchers.Main.immediate), sched, sched.clock)
    }

    private fun launch(ctx: PlayContext) {
        manager.tune(ctx)
        val model = source.state.value
        compose.setContent {
            UzzuTheme {
                PlayerScreen(
                    controls = manager,
                    model = model,
                    exit = PlayerExit({ toList++ }, { toMenu++ }),
                    startPolling = { polls++; AutoCloseable { polls-- } },
                    // Stand-in for the video so the scrims are visible in screenshots.
                    video = { Box(Modifier.fillMaxSize().background(Color(0xFF2B3A42))) },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun nhl(i: Int = 1, side: FeedSide = FeedSide.HOME): PlayContext {
        val keys = groupKeys(playlist, Section.GAMES, "NHL")
        return PlayContext(Section.GAMES, "NHL", keys, keys[i], side)
    }

    private fun channels(tab: String = "League Networks", i: Int = 1): PlayContext {
        val keys = groupKeys(playlist, Section.CHANNELS, tab)
        return PlayContext(Section.CHANNELS, tab, keys, keys[i])
    }

    private fun groupName() = compose.onNodeWithTag("groupname", useUnmergedTree = true).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }

    private fun listed(section: String) = compose.onAllNodesWithTag("on:live $section", useUnmergedTree = true).fetchSemanticsNodes().size == 1

    private fun overlayShown() = compose.onAllNodesWithTag("overlay").fetchSemanticsNodes().isNotEmpty()

    private fun rawKey(action: Int, code: Int, repeat: Int = 0, flags: Int = 0) {
        compose.runOnUiThread {
            val now = SystemClock.uptimeMillis()
            compose.activity.window.decorView.dispatchKeyEvent(
                KeyEvent(now, now, action, code, repeat, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags, InputDevice.SOURCE_DPAD),
            )
        }
        compose.waitForIdle()
    }

    /** The remote's Back, injected like a real key press (reaches the player's key handler and BackHandlers). */
    private fun back() {
        Espresso.pressBackUnconditionally()
        compose.waitForIdle()
    }

    @Test fun `Select opens the overlay on the playing item, Back closes it, stream keeps playing`() {
        launch(nhl(1))
        assertEquals("player", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(overlayShown())
        assertEquals("item:${nhl(1).currentKey}", compose.focusedTag())
        assertEquals(1, polls)
        assertEquals("NHL", groupName())
        compose.onNodeWithTag("on:live games", useUnmergedTree = true).assertExists()
        back()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertFalse(overlayShown())
        assertEquals(0, polls)
        assertEquals("player", compose.focusedTag())
        assertEquals(1, player.loads.size)
        assertEquals(0, toList)
    }

    @Test fun `overlay - Left-Right do nothing outside the list picker, Up climbs to the pickers, a new group tunes from it`() {
        launch(nhl(1))
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("toggle", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER) // flip to Away, overlay stays
        assertEquals(FeedSide.AWAY, manager.state.value.now!!.context.side)
        assertEquals(playlist.games.first { it.id == nhl(1).currentKey }.awayFeed!!.url, player.loads.last())
        assertTrue(overlayShown())
        compose.onAllNodesWithTag("segment:away:on", useUnmergedTree = true).fetchSemanticsNodes().let { assertEquals(1, it.size) }

        compose.key(KeyEvent.KEYCODE_DPAD_DOWN, 10)
        assertEquals("exit", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("exit", compose.focusedTag())
        assertEquals("NHL", groupName())
        assertTrue(listed("games"))

        // Up from the first item → group row → the listed segment; Up on the picker stays put.
        compose.key(KeyEvent.KEYCODE_DPAD_UP, 20)
        assertEquals("list:games", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("grouprow", compose.focusedTag())

        // Group list: opens on the current group, no wrap, Back closes it without a change.
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("group:NHL", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals("group:MLB", compose.focusedTag())
        back()
        assertTrue(overlayShown())
        assertEquals("grouprow", compose.focusedTag())
        assertEquals("NHL", groupName())

        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.key(KeyEvent.KEYCODE_DPAD_UP, 5)
        assertEquals("group:All", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("group:MLB", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("grouprow", compose.focusedTag())
        assertEquals("MLB", groupName())
        val mlb = groupKeys(playlist, Section.GAMES, "MLB")
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("item:${mlb.first()}", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER) // two feeds: picker over the overlay
        assertEquals("feed:home", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertFalse(overlayShown())
        val now = manager.state.value.now!!.context
        assertEquals("MLB", now.group)
        assertEquals(mlb.first(), now.currentKey)
        assertEquals(FeedSide.HOME, now.side)
    }

    private fun closeOverlay() {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    @Test fun `overlay - from a channel, Select on Live Games lists games, focus stays on it, a game plays after the picker, Back toggles`() {
        val start = channels("League Networks", 1)
        launch(start)
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(listed("channels"))
        assertEquals("LEAGUE NETWORKS", groupName())
        compose.key(KeyEvent.KEYCODE_DPAD_UP, 10)
        assertEquals("list:channels", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("list:games", compose.focusedTag())
        assertTrue(listed("channels")) // focus alone never switches
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(listed("games"))
        assertEquals("list:games", compose.focusedTag())
        assertEquals("ALL", groupName())

        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("group:MLB", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        val mlb = groupKeys(playlist, Section.GAMES, "MLB")
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("item:${mlb.first()}", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER) // two feeds: picker
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("feed:away", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        closeOverlay()
        assertFalse(overlayShown())
        var now = manager.state.value.now!!.context
        assertEquals(Triple(Section.GAMES, "MLB", mlb.first()), Triple(now.section, now.group, now.currentKey))
        assertEquals(FeedSide.AWAY, now.side)

        back()
        now = manager.state.value.now!!.context
        assertEquals(Section.CHANNELS to start.currentKey, now.section to now.currentKey)
        back()
        now = manager.state.value.now!!.context
        assertEquals(Triple(Section.GAMES, mlb.first(), FeedSide.AWAY), Triple(now.section, now.currentKey, now.side))
        assertEquals(0, toList)
    }

    @Test fun `overlay - from a game, Live Channels is Up then Right, a channel plays, Back returns to the same side`() {
        launch(nhl(1, FeedSide.AWAY))
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.key(KeyEvent.KEYCODE_DPAD_UP, 10)
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT) // no wrap
        assertEquals("list:channels", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(listed("channels"))
        assertEquals("ALL", groupName())
        val all = groupKeys(playlist, Section.CHANNELS, "All")
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        assertEquals("item:${all.first()}", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        closeOverlay()
        var now = manager.state.value.now!!.context
        assertEquals(Triple(Section.CHANNELS, "All", all[1]), Triple(now.section, now.group, now.currentKey))
        assertEquals(playlist.channels.first { it.key == all[1] }.url, player.loads.last())

        back()
        now = manager.state.value.now!!.context
        assertEquals(Triple(Section.GAMES, nhl(1).currentKey, FeedSide.AWAY), Triple(now.section, now.currentKey, now.side))
        assertEquals("NHL", now.group)
        back()
        assertEquals(all[1], manager.state.value.now!!.context.currentKey)
        assertEquals(0, toList)
    }

    @Test fun `Exit to menu leaves the player to S1`() {
        launch(channels())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN, 20)
        assertEquals("exit", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, toMenu)
    }

    @Test fun `Up-Down and CH keys step within the group, Back toggles last channel, then returns to the list`() {
        val ctx = channels("League Networks", 0)
        launch(ctx)
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(ctx.itemKeys[1], manager.state.value.now!!.context.currentKey)
        compose.key(KeyEvent.KEYCODE_CHANNEL_UP)
        assertEquals(ctx.itemKeys[2], manager.state.value.now!!.context.currentKey)
        compose.key(KeyEvent.KEYCODE_CHANNEL_DOWN)
        compose.key(KeyEvent.KEYCODE_DPAD_UP)
        compose.key(KeyEvent.KEYCODE_DPAD_UP) // wraps
        assertEquals(ctx.itemKeys.last(), manager.state.value.now!!.context.currentKey)
        back()
        assertEquals(ctx.itemKeys[0], manager.state.value.now!!.context.currentKey)
        assertEquals(0, toList)
        manager.stop()
        back()
        assertEquals(1, toList)
    }

    @Test fun `long-press Back exits to the menu, short Back does not fire after it`() {
        launch(channels())
        rawKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        rawKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, repeat = 1, flags = KeyEvent.FLAG_LONG_PRESS)
        rawKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, flags = KeyEvent.FLAG_CANCELED_LONG_PRESS)
        assertEquals(1, toMenu)
        assertEquals(0, toList)
    }

    @Test fun `Play-Pause pauses and resumes at the live edge`() {
        launch(channels())
        compose.mainClock.advanceTimeBy(4_000) // tune banner gone
        compose.key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        assertTrue(player.paused)
        compose.onNodeWithTag("paused").assertExists()
        compose.key(KeyEvent.KEYCODE_MEDIA_PLAY)
        assertEquals(1, player.liveEdgeResumes)
    }

    private fun failToS6() {
        player.fail(404, host = "uzzu.tv")
        sched.advance(2_000)
        player.fail(404, host = "uzzu.tv")
        sched.advance(5_000)
        player.fail(404, host = "uzzu.tv")
        compose.waitForIdle()
    }

    @Test fun `S6 initial focus is Retry, Retry re-runs the policy, Back returns to the list`() {
        launch(channels())
        failToS6()
        assertNotNull(manager.state.value.error)
        assertEquals("error:retry", compose.focusedTag())
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertNull(manager.state.value.error)
        assertEquals(4, player.loads.size)
        failToS6()
        assertEquals("error:retry", compose.focusedTag())
        back()
        assertEquals(1, toList)
    }

    @Test fun `no score is rendered in the overlay`() {
        launch(nhl(1))
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        val score = Regex("""\b\d{1,3}\s*[-–]\s*\d{1,3}\b""")
        assertTrue(texts.isNotEmpty())
        assertTrue(texts.filter { score.containsMatchIn(it) }.toString(), texts.none { score.containsMatchIn(it) })
    }

    // ---- Screenshots (goldens: see Snapshots) ----

    private fun shot(name: String) = compose.onRoot().captureRoboImage(Snapshots.path(name), Snapshots.options)

    @Test fun shotBannerGame() {
        compose.mainClock.autoAdvance = false
        launch(nhl(1))
        compose.mainClock.advanceTimeBy(600)
        shot("s5_banner_game")
    }

    @Test fun shotBannerChannel() {
        compose.mainClock.autoAdvance = false
        launch(channels("Regional Sports", 2))
        compose.mainClock.advanceTimeBy(600)
        shot("s5_banner_channel")
    }

    @Test fun shotOverlayGames() {
        launch(nhl(1))
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.mainClock.advanceTimeBy(500)
        shot("o1_overlay_games") // O1a
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.mainClock.advanceTimeBy(500)
        shot("o1_overlay_games_toggle_focused")
        compose.key(KeyEvent.KEYCODE_DPAD_UP, 10)
        compose.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.mainClock.advanceTimeBy(500)
        shot("o1_overlay_list_focus") // O1b: Channels focused, Games still listed
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.mainClock.advanceTimeBy(500)
        shot("o1_overlay_switched_channels") // O1d
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.key(KeyEvent.KEYCODE_DPAD_DOWN, 6)
        compose.mainClock.advanceTimeBy(500)
        shot("o1_overlay_group_list") // O1c
    }

    @Test fun shotOverlayChannels() {
        launch(channels("Regional Sports", 2))
        compose.key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.mainClock.advanceTimeBy(500)
        shot("o1_overlay_channels")
    }

    @Test fun shotError() {
        launch(channels())
        failToS6()
        compose.mainClock.advanceTimeBy(500)
        shot("s6_error")
    }

    @Test fun shotErrorAfterRefresh() {
        val away = playlist.games.first { it.awayFeed?.url?.contains("fastly") == true }
        launch(PlayContext(Section.GAMES, "All", groupKeys(playlist, Section.GAMES, "All"), away.id, FeedSide.AWAY))
        player.fail(403, host = "tran03.global.ssl.fastly.net")
        player.fail(403, host = "tran03.global.ssl.fastly.net")
        sched.advance(2_000)
        player.fail(403, host = "tran03.global.ssl.fastly.net")
        sched.advance(5_000)
        player.fail(403, host = "tran03.global.ssl.fastly.net")
        compose.waitForIdle()
        assertEquals("Stream returned 403 after playlist refresh", manager.state.value.error!!.summary)
        assertFalse(manager.state.value.error!!.maskedStream.contains("1792476833"))
        shot("s6_error_fastly")
    }
}
