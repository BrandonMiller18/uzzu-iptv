package com.bmiller.uzzu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bmiller.uzzu.data.PlaylistState
import com.bmiller.uzzu.player.PlayItem
import com.bmiller.uzzu.playlist.FeedSide
import com.bmiller.uzzu.ui.components.MonoTag
import com.bmiller.uzzu.ui.debug.DebugFocus
import com.bmiller.uzzu.ui.debug.DebugListScreen
import com.bmiller.uzzu.ui.player.PlayerScreen
import com.bmiller.uzzu.ui.setup.SetupScreen
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuTheme
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

private sealed interface Screen {
    data object Starting : Screen
    data class Setup(val canGoBack: Boolean) : Screen
    data object List : Screen
    data object Player : Screen
}

class MainActivity : ComponentActivity() {
    private val container get() = (application as UzzuApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UzzuTheme { App() } }
    }

    override fun onStart() {
        super.onStart()
        container.player.start()
    }

    override fun onStop() {
        container.player.stop()
        super.onStop()
    }

    @Composable
    private fun App() {
        val repo = container.playlistRepository
        var screen by remember { mutableStateOf<Screen>(Screen.Starting) }
        var focus by remember { mutableStateOf<DebugFocus?>(null) }
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val state by repo.state.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            if (BuildConfig.DEBUG) ingestDebugPlaylistUrl()
            if (container.settings.playlistUrl() == null) {
                screen = Screen.Setup(canGoBack = false)
            } else {
                screen = Screen.List
                repo.refresh() // every launch, before showing data
            }
        }

        Box(Modifier.fillMaxSize().background(UzzuColors.Background)) {
            when (val s = screen) {
                Screen.Starting -> MonoTag("LOADING", Modifier.align(Alignment.Center))

                is Screen.Setup -> {
                    if (s.canGoBack) BackHandler { screen = Screen.List }
                    SetupScreen(onSave = { url ->
                        runCatching { repo.saveAndLoad(url) }.fold(
                            onSuccess = { screen = Screen.List; null },
                            onFailure = { it.message ?: "Could not load the playlist." },
                        )
                    })
                }

                Screen.List -> {
                    val loaded = state as? PlaylistState.Loaded
                    val status = when (val st = state) {
                        PlaylistState.Idle, PlaylistState.Loading -> "LOADING"
                        is PlaylistState.Failed -> "LOAD FAILED"
                        is PlaylistState.Loaded ->
                            "LAST SYNC ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(st.fetchedAtMillis))}"
                    }
                    DebugListScreen(
                        playlist = loaded?.playlist,
                        status = status,
                        error = (state as? PlaylistState.Failed)?.message,
                        listState = listState,
                        restoreFocus = focus,
                        onPlayGame = { game, side ->
                            val feed = game.feed(side) ?: return@DebugListScreen
                            focus = DebugFocus("game:${game.id}:${if (side == FeedSide.HOME) "H" else "A"}")
                            container.player.play(PlayItem("${game.title} · ${side.name}", feed.url))
                            screen = Screen.Player
                        },
                        onPlayChannel = { ch ->
                            focus = DebugFocus("ch:${ch.playlistIndex}")
                            container.player.play(PlayItem(ch.name, ch.url))
                            screen = Screen.Player
                        },
                        onRefresh = { scope.launch { repo.refresh() } },
                        onEditUrl = { screen = Screen.Setup(canGoBack = true) },
                    )
                }

                Screen.Player -> PlayerScreen(container.player, onBack = {
                    container.player.clear()
                    screen = Screen.List
                })
            }
        }
    }

    /**
     * Debug builds only: adopt files/debug_playlist_url.txt (pushed over adb) as the playlist URL
     * when none is saved yet, then delete the file. Never logs the URL itself.
     */
    private suspend fun ingestDebugPlaylistUrl() {
        val f = File(filesDir, "debug_playlist_url.txt")
        if (!f.exists()) return
        val url = runCatching { f.readText().trim() }.getOrNull()
        if (container.settings.playlistUrl() == null && !url.isNullOrEmpty()) {
            container.settings.setPlaylistUrl(url)
            container.logger.i("Setup", "debug playlist URL ingested from file")
        } else {
            container.logger.i("Setup", "debug playlist URL file ignored (URL already set or file empty)")
        }
        f.delete()
    }
}
