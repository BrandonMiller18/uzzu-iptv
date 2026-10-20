package com.bmiller.uzzu.data

import com.bmiller.uzzu.log.AppLogger
import com.bmiller.uzzu.log.SecretMasker
import com.bmiller.uzzu.playlist.M3uParser
import com.bmiller.uzzu.playlist.Playlist
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

sealed interface PlaylistState {
    data object Idle : PlaylistState
    data object Loading : PlaylistState
    data class Loaded(val playlist: Playlist, val fetchedAtMillis: Long) : PlaylistState
    data class Failed(val message: String) : PlaylistState
}

class PlaylistException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Fetches and parses the playlist and holds the in-memory model for the process lifetime. */
class PlaylistRepository(
    private val client: OkHttpClient,
    private val settings: Settings,
    private val logger: AppLogger,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Offline enrichment after parsing (league per game, sub-group per channel). */
    private val classify: (Playlist) -> Playlist = { it },
) {
    private val _state = MutableStateFlow<PlaylistState>(PlaylistState.Idle)
    val state: StateFlow<PlaylistState> = _state.asStateFlow()

    /** Last successfully loaded playlist in this process, if any. */
    val current: Playlist? get() = (_state.value as? PlaylistState.Loaded)?.playlist

    /** Re-fetches the saved playlist URL. Keeps the previous model visible on failure. */
    suspend fun refresh(): Result<Playlist> {
        val url = settings.playlistUrl() ?: return Result.failure(PlaylistException("No playlist URL saved"))
        val previous = _state.value
        if (previous !is PlaylistState.Loaded) _state.value = PlaylistState.Loading
        val result = runCatching { fetch(url) }
        val now = clock()
        result.onSuccess { p ->
            _state.value = PlaylistState.Loaded(p, now)
            settings.recordFetch(now, "OK ${p.games.size} games, ${p.channels.size} channels")
        }.onFailure { t ->
            val msg = SecretMasker.mask(t.message ?: t.javaClass.simpleName)
            if (previous !is PlaylistState.Loaded) _state.value = PlaylistState.Failed(msg)
            settings.recordFetch(now, "FAILED $msg")
        }
        return result
    }

    /** First-run / edit: validates [url] by fetching and parsing it, then saves it and adopts the result. */
    suspend fun saveAndLoad(url: String): Playlist {
        val playlist = fetch(url)
        val now = clock()
        settings.setPlaylistUrl(url)
        settings.recordFetch(now, "OK ${playlist.games.size} games, ${playlist.channels.size} channels")
        _state.value = PlaylistState.Loaded(playlist, now)
        logger.i(TAG, "playlist URL saved")
        return playlist
    }

    /** Fetches and parses [url] without touching state. */
    suspend fun fetch(url: String): Playlist = withContext(io) {
        val httpUrl = url.trim().toHttpUrlOrNull() ?: throw PlaylistException("Not a valid http(s) URL")
        val started = clock()
        logger.i(TAG, "fetching playlist $httpUrl")
        val text = try {
            client.newCall(Request.Builder().url(httpUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw PlaylistException("Playlist request returned HTTP ${resp.code}")
                resp.body.string()
            }
        } catch (e: PlaylistException) {
            logger.e(TAG, "playlist fetch failed: ${e.message}")
            throw e
        } catch (e: IOException) {
            logger.e(TAG, "playlist fetch failed", e)
            throw PlaylistException("Network error: ${e.message ?: e.javaClass.simpleName}", e)
        }
        if (!text.trimStart('\uFEFF', ' ', '\r', '\n', '\t').startsWith("#EXTM3U", ignoreCase = true)) {
            logger.e(TAG, "playlist response is not an M3U file (${text.length} chars)")
            throw PlaylistException("Response is not an M3U playlist")
        }
        val playlist = classify(M3uParser { logger.w(TAG, it) }.parse(text))
        if (playlist.games.isEmpty() && playlist.channels.isEmpty()) {
            throw PlaylistException("Playlist has no Live-Games or Live-Channels entries")
        }
        logger.i(
            TAG,
            "playlist fetched and parsed in ${clock() - started} ms: ${playlist.games.size} games, " +
                "${playlist.channels.size} channels, stats=${playlist.stats}, " +
                "leagues=${playlist.games.groupingBy { it.league }.eachCount()}, " +
                "groups=${playlist.channels.groupingBy { it.subGroup }.eachCount()}",
        )
        playlist
    }

    private companion object {
        const val TAG = "Playlist"
    }
}
