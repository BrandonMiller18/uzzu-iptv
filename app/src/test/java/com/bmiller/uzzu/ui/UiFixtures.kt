package com.bmiller.uzzu.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.bmiller.uzzu.data.PlaylistState
import com.bmiller.uzzu.player.PlayContext
import com.bmiller.uzzu.player.PlayItem
import com.bmiller.uzzu.playlist.League
import com.bmiller.uzzu.playlist.Playlist
import com.bmiller.uzzu.schedule.FakeScheduleSource
import com.bmiller.uzzu.schedule.Fixtures
import com.bmiller.uzzu.schedule.ScheduleMatcher
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.util.Locale
import java.util.TimeZone

/** UI test data: the scrubbed playlist merged with the recorded (and synthetic live) ESPN JSON. */
object UiFixtures {
    private val yesterday = LocalDate.of(2026, 10, 19)
    private val today = LocalDate.of(2026, 10, 20)

    /** Extra Live-Games entries so every league tab and the Ungrouped card appear. */
    val allLeagueExtras = listOf(
        "Boston Celtics @ Detroit Pistons-H",
        "Boston Celtics @ Detroit Pistons-A",
        "Middle Tennessee Blue Raiders @ Florida International Panthers-H",
        "Middle Tennessee Blue Raiders @ Florida International Panthers-A",
        "UFC 300 Main Card",
    )

    fun pinLocale() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        Locale.setDefault(Locale.US)
    }

    /**
     * [liveIn]: leagues whose synthetic "in progress" file is used for 2026-10-19 (Ducks @ Rangers
     * 2ND 8:14, Guardians @ Rays TOP 5TH, Commanders @ 49ers Q3 4:12); others are Final.
     */
    fun playlist(
        extras: List<String> = allLeagueExtras,
        liveIn: Set<League> = setOf(League.NHL, League.MLB, League.NFL),
    ): Playlist {
        val base = Fixtures.playlist(extras)
        val source = FakeScheduleSource(suffixFor = { l, d -> if (d == yesterday && l in liveIn) "_in" else "" })
        val events = runBlocking {
            source.leagues.associateWith { l -> (source.fetch(l, yesterday) + source.fetch(l, today)).distinctBy { it.id } }
        }
        val games = ScheduleMatcher(Fixtures.teams).apply(base.games, events)
        // No network in tests: drop logo URLs so Coil shows the placeholders.
        return base.copy(
            games = games.map { g -> g.copy(homeFeed = g.homeFeed?.copy(logoUrl = null), awayFeed = g.awayFeed?.copy(logoUrl = null)) },
            channels = base.channels.map { it.copy(logoUrl = null) },
        )
    }

    /** "LAST SYNC 7:10 PM" in New York. */
    val FETCHED_AT: Long = java.time.Instant.parse("2026-10-19T23:10:00Z").toEpochMilli()

    fun state(playlist: Playlist = playlist()) = AppState(
        model = PlaylistState.Loaded(playlist, FETCHED_AT),
        lastFetchAtMillis = FETCHED_AT,
        lastFetchResult = "OK 9 games, 96 channels",
        playlistUrl = "https://uzzu.example/get.php?username=someone%40example.com&password=hunter22&type=m3u_plus",
        appVersion = "0.1.0 (100)",
        logPath = "/data/user/0/com.bmiller.uzzu/files/logs/app.log",
    )
}

/** Records what the screens asked for. */
class FakeActions : AppActions {
    val played = mutableListOf<Pair<PlayItem, PlayContext>>()
    var refreshes = 0
    var stops = 0
    var exits = 0
    var pollers = 0
    var updateChecks = 0
    var saveResult: String? = null
    val saved = mutableListOf<String>()

    override fun refresh() { refreshes++ }
    override suspend fun saveUrl(url: String): String? { saved += url; return saveResult }
    override fun play(item: PlayItem, context: PlayContext) { played += item to context }
    override fun stopPlayback() { stops++ }
    override fun startPolling(): AutoCloseable { pollers++; return AutoCloseable { pollers-- } }
    override fun checkForUpdates() { updateChecks++ }
    override fun installUpdate() = Unit
    override fun dismissUpdate() = Unit
    override fun exit() { exits++ }
}

/**
 * Sends a real key down/up to the topmost window (the activity, or a Dialog above it), like the
 * remote does.
 */
fun AndroidComposeTestRule<*, out ComponentActivity>.key(code: Int, times: Int = 1) {
    repeat(times) {
        runOnUiThread {
            val view = topWindowView() ?: activity.window.decorView
            view.dispatchKeyEvent(remoteKey(KeyEvent.ACTION_DOWN, code))
            view.dispatchKeyEvent(remoteKey(KeyEvent.ACTION_UP, code))
        }
        waitForIdle()
    }
}

/** Like the Fire TV remote: source DPAD (text fields only hand focus on for D-pad-sourced keys). */
private fun remoteKey(action: Int, code: Int): KeyEvent {
    val now = SystemClock.uptimeMillis()
    return KeyEvent(now, now, action, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_DPAD)
}

@Suppress("UNCHECKED_CAST")
private fun topWindowView(): View? = runCatching {
    val wmg = Class.forName("android.view.WindowManagerGlobal")
    val instance = wmg.getMethod("getInstance").invoke(null)
    val field = wmg.getDeclaredField("mViews").apply { isAccessible = true }
    (field.get(instance) as List<View>).lastOrNull { it.isAttachedToWindow && it.visibility == View.VISIBLE }
}.getOrNull()

/** testTag of the focused node (searching the unmerged tree, including dialogs). */
fun ComposeTestRule.focusedTag(): String? = runCatching {
    onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().lastOrNull { it.config.getOrNull(SemanticsProperties.TestTag) != null }?.let { it.config.getOrNull(SemanticsProperties.TestTag) }
}.getOrNull()

fun SemanticsNodeInteraction.tag(): String? = fetchSemanticsNode().config.getOrNull(SemanticsProperties.TestTag)
