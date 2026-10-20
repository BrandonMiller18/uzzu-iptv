package com.bmiller.uzzu.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.bmiller.uzzu.log.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.net.URI

/** What is (or should be) playing. [label] is a human description such as "Ducks @ Rangers · HOME". */
data class PlayItem(val label: String, val url: String)

/**
 * Owns the single ExoPlayer. Created in onStart, released in onStop; every tune reuses the same
 * instance via setMediaItem + prepare. HLS only, through OkHttpDataSource on the shared client.
 */
@OptIn(UnstableApi::class)
class PlayerController(
    private val context: Context,
    private val okHttp: OkHttpClient,
    private val logger: AppLogger,
) {
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    private val _current = MutableStateFlow<PlayItem?>(null)
    val current: StateFlow<PlayItem?> = _current.asStateFlow()

    private var tuneStartedAt = 0L

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            val item = _current.value ?: return
            val name = when (state) {
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> return
            }
            logger.i(TAG, "STATE_$name ${item.label} ${maskedTarget(item.url)} +${elapsed()} ms")
        }

        override fun onRenderedFirstFrame() {
            val item = _current.value ?: return
            logger.i(TAG, "first frame rendered ${item.label} ${maskedTarget(item.url)} +${elapsed()} ms")
        }

        override fun onPlayerError(error: PlaybackException) {
            val item = _current.value
            val http = generateSequence<Throwable>(error) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                .firstOrNull()?.responseCode
            logger.e(
                TAG,
                "playback error ${error.errorCodeName} http=${http ?: "-"} " +
                    "${item?.label ?: "?"} ${item?.url?.let(::maskedTarget) ?: ""}",
                error.cause,
            )
        }
    }

    fun start() {
        if (_player.value != null) return
        val dataSource = OkHttpDataSource.Factory(okHttp)
        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(HlsMediaSource.Factory(dataSource))
            .build()
        exo.addListener(listener)
        exo.playWhenReady = true
        _player.value = exo
        logger.i(TAG, "player created")
        // Rebuilt after onStop: resume whatever was on screen.
        _current.value?.let { prepare(exo, it) }
    }

    fun stop() {
        val exo = _player.value ?: return
        exo.removeListener(listener)
        exo.release()
        _player.value = null
        logger.i(TAG, "player released")
    }

    fun play(item: PlayItem) {
        _current.value = item
        logger.i(TAG, "tune ${item.label} ${maskedTarget(item.url)}")
        _player.value?.let { prepare(it, item) }
    }

    /** Stops playback and forgets the item (Back to the list). The player itself stays alive. */
    fun clear() {
        _current.value = null
        _player.value?.run {
            stop()
            clearMediaItems()
        }
    }

    private fun prepare(exo: ExoPlayer, item: PlayItem) {
        tuneStartedAt = System.currentTimeMillis()
        exo.setMediaItem(
            MediaItem.Builder().setUri(item.url).setMimeType(MimeTypes.APPLICATION_M3U8).build(),
        )
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun elapsed() = System.currentTimeMillis() - tuneStartedAt

    /** Host + path; the logger's masker hides the account id and tokens. */
    private fun maskedTarget(url: String): String =
        runCatching { URI(url).let { "${it.host}${it.rawPath}" } }.getOrDefault(url)

    private companion object {
        const val TAG = "Player"
    }
}
