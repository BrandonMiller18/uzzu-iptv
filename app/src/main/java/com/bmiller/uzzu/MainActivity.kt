package com.bmiller.uzzu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bmiller.uzzu.data.PlaylistState
import com.bmiller.uzzu.log.SecretMasker
import com.bmiller.uzzu.player.PlayContext
import com.bmiller.uzzu.player.PlayItem
import com.bmiller.uzzu.ui.AppActions
import com.bmiller.uzzu.ui.AppContent
import com.bmiller.uzzu.ui.AppState
import com.bmiller.uzzu.ui.Navigator
import com.bmiller.uzzu.ui.Route
import com.bmiller.uzzu.ui.player.PlayerScreen
import com.bmiller.uzzu.ui.theme.UzzuTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

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
        val nav = remember { Navigator(Route.Starting) }
        val scope = rememberCoroutineScope()
        var refreshing by remember { mutableStateOf(false) }
        // Playlist merged with league/start time/status (ESPN, polled while S2 is visible).
        val model by container.scheduleRepository.model.collectAsStateWithLifecycle()
        val url by container.settings.playlistUrl.collectAsStateWithLifecycle(null)
        val lastFetchAt by container.settings.lastFetchAt.collectAsStateWithLifecycle(null)
        val lastFetchResult by container.settings.lastFetchResult.collectAsStateWithLifecycle(null)
        val update by container.updates.state.collectAsStateWithLifecycle()

        val actions = remember {
            object : AppActions {
                override fun refresh() {
                    if (refreshing) return
                    refreshing = true
                    scope.launch {
                        try {
                            refreshPlaylistThenSchedule()
                        } finally {
                            refreshing = false
                        }
                    }
                }

                override suspend fun saveUrl(url: String): String? =
                    runCatching { container.playlistRepository.saveAndLoad(url) }.fold(
                        onSuccess = {
                            scope.launch { container.scheduleRepository.refresh() }
                            null
                        },
                        onFailure = { SecretMasker.mask(it.message ?: "Could not load the playlist.") },
                    )

                override fun play(item: PlayItem, context: PlayContext) = container.player.play(item, context)
                override fun stopPlayback() = container.player.clear()
                override fun startPolling(): AutoCloseable = container.scheduleRepository.startPolling()
                override fun checkForUpdates() {
                    scope.launch { container.updates.check(force = true) }
                }
                override fun installUpdate() = container.updates.install()
                override fun dismissUpdate() = container.updates.dismiss()
                override fun exit() = finish()
            }
        }

        LaunchedEffect(Unit) {
            if (BuildConfig.DEBUG) ingestDebugPlaylistUrl()
            if (container.settings.playlistUrl() == null) {
                nav.reset(Route.Setup(canGoBack = false))
            } else {
                // Every launch, before S1 shows counts (S1 shows LOADING meanwhile).
                nav.reset(Route.Home)
                refreshPlaylistThenSchedule()
            }
            launch { container.updates.check(force = false) }
        }

        AppContent(
            nav = nav,
            state = AppState(
                model = model,
                refreshing = refreshing,
                lastFetchAtMillis = lastFetchAt,
                lastFetchResult = lastFetchResult,
                playlistUrl = url,
                update = update,
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                logPath = container.logger.file.absolutePath,
            ),
            actions = actions,
            player = { onBack -> PlayerScreen(container.player, onBack = onBack) },
        )
    }

    /**
     * Playlist first (it gates S1 and competes for bandwidth), then one schedule fetch so S1's live
     * count is current. Continuous polling only runs while S2 is visible.
     */
    private suspend fun CoroutineScope.refreshPlaylistThenSchedule() {
        container.playlistRepository.refresh()
        if (container.playlistRepository.state.first() is PlaylistState.Loaded) {
            launch { container.scheduleRepository.refresh() }
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
