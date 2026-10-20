package com.bmiller.uzzu

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import com.bmiller.uzzu.data.PlaylistRepository
import com.bmiller.uzzu.debug.FixtureInterceptor
import com.bmiller.uzzu.data.Settings
import com.bmiller.uzzu.log.AppLogger
import com.bmiller.uzzu.player.PlayerController
import com.bmiller.uzzu.playlist.ChannelGrouper
import com.bmiller.uzzu.schedule.EspnScheduleSource
import com.bmiller.uzzu.schedule.LeagueClassifier
import com.bmiller.uzzu.schedule.PlaylistClassifier
import com.bmiller.uzzu.schedule.ScheduleMatcher
import com.bmiller.uzzu.schedule.ScheduleRepository
import com.bmiller.uzzu.schedule.TeamDictionary
import com.bmiller.uzzu.update.PlaceholderUpdateChecker
import com.bmiller.uzzu.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Manual DI: process-wide singletons. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val logger = AppLogger(
        dir = File(app.filesDir, "logs"),
        logcat = { level, tag, msg ->
            val t = "Uzzu/$tag"
            when (level) {
                'E' -> Log.e(t, msg)
                'W' -> Log.w(t, msg)
                else -> Log.i(t, msg)
            }
        },
    )

    /** Shared by the playlist fetch, Coil and Media3 (OkHttpDataSource). */
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .apply { if (BuildConfig.DEBUG) addInterceptor(FixtureInterceptor(app.assets) { fixtureMode }) }
        .build()

    /** Debug builds only: serve the bundled test playlist + ESPN recordings (see [FixtureInterceptor]). */
    @Volatile
    var fixtureMode = false

    val settings = Settings(app.dataStore)

    /** Process-lifetime scope for background work (schedule polling). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun asset(name: String) = app.assets.open(name).bufferedReader().use { it.readText() }

    // Bundled assets, parsed on first use (from the IO dispatcher inside the playlist fetch).
    val teamDictionary by lazy { TeamDictionary.parse(asset("teams.json")) }
    private val channelGrouper by lazy { ChannelGrouper.parse(asset("channel_groups.json")) }
    private val playlistClassifier by lazy { PlaylistClassifier(LeagueClassifier(teamDictionary), channelGrouper) }

    val playlistRepository = PlaylistRepository(okHttp, settings, logger, classify = { playlistClassifier.classify(it) })

    /** Games/channels merged with ESPN start time + status. The UI reads [ScheduleRepository.model]. */
    val scheduleRepository = ScheduleRepository(
        playlistState = playlistRepository.state,
        source = EspnScheduleSource(okHttp),
        matcher = ScheduleMatcher(lazy { teamDictionary }) { logger.w("Schedule", it) },
        logger = logger,
        scope = appScope,
    )

    val player = PlayerController(app, okHttp, logger)

    /** In-app updates (S1 bar, S7 row). M5 replaces the placeholder with the GitHub-release checker. */
    val updates: UpdateChecker = PlaceholderUpdateChecker()
}
