package com.bmiller.uzzu

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import com.bmiller.uzzu.data.PlaylistRepository
import com.bmiller.uzzu.data.Settings
import com.bmiller.uzzu.log.AppLogger
import com.bmiller.uzzu.player.PlayerController
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
        .build()

    val settings = Settings(app.dataStore)

    val playlistRepository = PlaylistRepository(okHttp, settings, logger)

    val player = PlayerController(app, okHttp, logger)
}
