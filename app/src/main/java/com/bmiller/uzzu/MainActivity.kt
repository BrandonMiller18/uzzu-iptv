package com.bmiller.uzzu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat
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
import com.bmiller.uzzu.ui.player.VideoSurface
import com.bmiller.uzzu.ui.theme.UzzuTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val container get() = (application as UzzuApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("uzzu.fixture", false)) {
            container.fixtureMode = true
            container.logger.i("Setup", "fixture mode: test playlist + recorded ESPN data")
        }
        if (BuildConfig.DEBUG && intent.getBooleanExtra("uzzu.suppress_errors", false)) {
            container.debugSuppressErrors = true
            container.logger.i("Setup", "debug: error screen suppressed")
        }
        if (BuildConfig.DEBUG) registerDebugReceiver()
        setContent { UzzuTheme { App() } }
    }

    override fun onStart() {
        super.onStart()
        container.foreground = true
        container.player.start()
        container.playback.onForeground()
    }

    override fun onStop() {
        container.foreground = false
        container.playback.onBackground()
        container.player.release()
        container.logger.flush(timeoutMillis = 500)
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

                override fun play(item: PlayItem, context: PlayContext) = container.playback.tune(context)
                override fun stopPlayback() = container.playback.stop()
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
            player = { exit ->
                PlayerScreen(
                    controls = container.playback,
                    model = model,
                    exit = exit,
                    startPolling = container.scheduleRepository::startPolling,
                    video = { VideoSurface(container.player) },
                    logKey = { container.logger.i("Keys", it) },
                )
            },
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
     * Debug builds only: `adb shell am broadcast -a com.bmiller.uzzu.DEBUG_BREAK_NEXT_TUNE` makes the
     * next tuned URL (and its retries) point at a missing path, to force S6 with a real HTTP 404.
     */
    private fun registerDebugReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                container.debugBreakNextTune = true
                container.logger.i("Debug", "next tune will be broken (forced 404)")
            }
        }
        ContextCompat.registerReceiver(this, receiver, IntentFilter(ACTION_BREAK_NEXT_TUNE), ContextCompat.RECEIVER_EXPORTED)
        debugReceiver = receiver
    }

    private var debugReceiver: BroadcastReceiver? = null

    override fun onDestroy() {
        debugReceiver?.let(::unregisterReceiver)
        debugReceiver = null
        super.onDestroy()
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

private const val ACTION_BREAK_NEXT_TUNE = "com.bmiller.uzzu.DEBUG_BREAK_NEXT_TUNE"
