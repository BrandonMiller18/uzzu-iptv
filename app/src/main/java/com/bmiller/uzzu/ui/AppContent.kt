package com.bmiller.uzzu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bmiller.uzzu.data.PlaylistState
import com.bmiller.uzzu.player.PlayContext
import com.bmiller.uzzu.player.PlayItem
import com.bmiller.uzzu.playlist.GameStatus
import com.bmiller.uzzu.ui.channels.ChannelsScreen
import com.bmiller.uzzu.ui.components.ListMemory
import com.bmiller.uzzu.ui.components.LoadingTag
import com.bmiller.uzzu.ui.games.GamesScreen
import com.bmiller.uzzu.ui.home.HomeCounts
import com.bmiller.uzzu.ui.home.HomeFocus
import com.bmiller.uzzu.ui.home.HomeScreen
import com.bmiller.uzzu.ui.settings.SettingsScreen
import com.bmiller.uzzu.ui.setup.SetupScreen
import com.bmiller.uzzu.ui.setup.maskPlaylistUrlForDisplay
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.update.UpdateState

sealed interface Route {
    data object Starting : Route
    data class Setup(val canGoBack: Boolean) : Route
    data object Home : Route
    data object Games : Route
    data object Channels : Route
    data object Settings : Route
    data object Player : Route
}

/** Back stack of [Route]s. S0 → S1 → S2/S3/S7; S2 → (S4) → player; S3 → player. */
@Stable
class Navigator(start: Route = Route.Starting) {
    private val stack = mutableStateListOf(start)
    val current: Route get() = stack.last()
    val backStack: List<Route> get() = stack.toList()

    fun push(route: Route) {
        stack.add(route)
    }

    /** Returns false when already at the root (the caller exits). */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun reset(route: Route) {
        stack.clear()
        stack.add(route)
    }
}

/** Everything the screens read. Built by MainActivity from the repositories (fakes in tests). */
@Immutable
data class AppState(
    val model: PlaylistState,
    val refreshing: Boolean = false,
    val lastFetchAtMillis: Long? = null,
    val lastFetchResult: String? = null,
    val playlistUrl: String? = null,
    val update: UpdateState = UpdateState.Idle,
    val appVersion: String = "",
    val logPath: String = "",
)

/** Side effects the screens trigger. */
interface AppActions {
    fun refresh()
    suspend fun saveUrl(url: String): String?
    fun play(item: PlayItem, context: PlayContext)
    fun stopPlayback()
    fun startPolling(): AutoCloseable
    fun checkForUpdates()
    fun installUpdate()
    fun dismissUpdate()
    fun exit()
}

fun syncLabel(state: AppState): String = when {
    state.refreshing -> "SYNCING"
    else -> when (val m = state.model) {
        PlaylistState.Idle, PlaylistState.Loading -> "LOADING"
        is PlaylistState.Failed -> "SYNC FAILED"
        is PlaylistState.Loaded -> "LAST SYNC ${formatClock(m.fetchedAtMillis)}"
    }
}

private fun settingsSyncLine(state: AppState): String {
    if (state.refreshing) return "SYNCING"
    val at = state.lastFetchAtMillis ?: return "NEVER SYNCED"
    val result = state.lastFetchResult?.substringBefore(' ')?.takeIf { it.isNotEmpty() } ?: "OK"
    return "LAST SYNC ${formatClock(at)} · $result"
}

private fun updateLine(update: UpdateState): String = when (update) {
    UpdateState.Idle -> "NOT CHECKED YET"
    UpdateState.Checking -> "CHECKING"
    is UpdateState.UpToDate -> "UP TO DATE · CHECKED ${formatClock(update.checkedAtMillis)}"
    is UpdateState.Available -> "VERSION ${update.version} AVAILABLE"
    is UpdateState.Failed -> update.message.uppercase()
}

fun homeCounts(model: PlaylistState): HomeCounts? = (model as? PlaylistState.Loaded)?.playlist?.let { p ->
    HomeCounts(
        liveNow = p.games.count { it.status is GameStatus.Live },
        gamesToday = p.games.size,
        channels = p.channels.size,
        groups = p.channels.mapTo(HashSet()) { it.subGroup }.size,
    )
}

/** Routes the current screen. [player] renders S5 and calls its argument on Back. */
@Composable
fun AppContent(
    nav: Navigator,
    state: AppState,
    actions: AppActions,
    player: @Composable (onBack: () -> Unit) -> Unit,
) {
    val gamesMemory = remember { mutableStateOf(ListMemory()) }
    val channelsMemory = remember { mutableStateOf(ListMemory()) }
    var homeFocus by remember { mutableStateOf(HomeFocus.GAMES) }
    val loaded = state.model as? PlaylistState.Loaded
    val sync = syncLabel(state)
    val back: () -> Unit = { if (!nav.pop()) actions.exit() }

    Box(Modifier.fillMaxSize().background(UzzuColors.Background)) {
        when (val route = nav.current) {
            Route.Starting -> LoadingTag(Modifier.align(Alignment.Center))

            is Route.Setup -> {
                if (route.canGoBack) BackHandler(onBack = back)
                SetupScreen(onSave = { url -> actions.saveUrl(url).also { if (it == null) nav.reset(Route.Home) } })
            }

            Route.Home -> HomeScreen(
                counts = homeCounts(state.model),
                syncLabel = sync,
                error = (state.model as? PlaylistState.Failed)?.message,
                update = state.update,
                onGames = {
                    homeFocus = HomeFocus.GAMES
                    gamesMemory.value = ListMemory()
                    nav.push(Route.Games)
                },
                onChannels = {
                    homeFocus = HomeFocus.CHANNELS
                    channelsMemory.value = ListMemory()
                    nav.push(Route.Channels)
                },
                onRefresh = {
                    homeFocus = HomeFocus.REFRESH
                    actions.refresh()
                },
                onSettings = {
                    homeFocus = HomeFocus.SETTINGS
                    nav.push(Route.Settings)
                },
                onInstallUpdate = actions::installUpdate,
                onDismissUpdate = actions::dismissUpdate,
                onExit = actions::exit,
                initialFocus = homeFocus,
            )

            Route.Games -> GamesScreen(
                games = loaded?.playlist?.games,
                syncLabel = sync,
                memory = gamesMemory.value,
                startPolling = actions::startPolling,
                onPlay = { game, side, context ->
                    val feed = game.feed(side)
                    if (feed != null) {
                        actions.play(PlayItem("${game.title} · ${side.name}", feed.url), context)
                        nav.push(Route.Player)
                    }
                },
                onBack = back,
            )

            Route.Channels -> ChannelsScreen(
                channels = loaded?.playlist?.channels,
                syncLabel = sync,
                memory = channelsMemory.value,
                onPlay = { channel, context ->
                    actions.play(PlayItem(channel.name, channel.url), context)
                    nav.push(Route.Player)
                },
                onBack = back,
            )

            Route.Settings -> SettingsScreen(
                maskedUrl = maskPlaylistUrlForDisplay(state.playlistUrl),
                currentUrl = state.playlistUrl,
                syncLine = settingsSyncLine(state),
                updateLine = updateLine(state.update),
                appVersion = state.appVersion,
                logPath = state.logPath,
                onSaveUrl = actions::saveUrl,
                onRefresh = actions::refresh,
                onCheckUpdates = actions::checkForUpdates,
                onBack = back,
            )

            Route.Player -> player {
                actions.stopPlayback()
                back()
            }
        }
    }
}
