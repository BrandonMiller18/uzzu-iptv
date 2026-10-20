package com.bmiller.uzzu.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.bmiller.uzzu.log.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.net.URI

/** What the lists asked to play. [label] is a human description such as "Ducks @ Rangers · HOME". */
data class PlayItem(val label: String, val url: String)

/**
 * Owns the single ExoPlayer. Created in onStart, released in onStop; every tune reuses the same
 * instance via setMediaItem + prepare. HLS only, through OkHttpDataSource on the shared client.
 * Player events go to [listener] ([PlaybackManager] → [RetryPolicy]).
 */
@OptIn(UnstableApi::class)
class PlayerController(
    private val context: Context,
    private val okHttp: OkHttpClient,
    private val logger: AppLogger,
    /** Debug only: when set, rewrites the next loaded URL (forced-404 hook). */
    private val debugRewrite: ((String) -> String?)? = null,
) : StreamPlayer {
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    override var listener: StreamPlayer.Listener? = null

    private var currentUrl: String? = null
    private var loadedAt = 0L

    private val exoListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            val url = currentUrl ?: return
            val name = when (state) {
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> return
            }
            logger.i(TAG, "STATE_$name ${maskedTarget(url)} +${elapsed()} ms")
            if (state == Player.STATE_BUFFERING) listener?.onBuffering()
            if (state == Player.STATE_READY) listener?.onReady()
        }

        override fun onRenderedFirstFrame() {
            val url = currentUrl ?: return
            logger.i(TAG, "first frame rendered ${maskedTarget(url)} +${elapsed()} ms")
            listener?.onFirstFrame()
        }

        override fun onPlayerError(error: PlaybackException) {
            val bad = generateSequence<Throwable>(error) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                .firstOrNull()
            val failingHost = bad?.dataSpec?.uri?.host
                ?: generateSequence<Throwable>(error) { it.cause }.filterIsInstance<HttpDataSource.HttpDataSourceException>().firstOrNull()?.dataSpec?.uri?.host
            logger.e(
                TAG,
                "playback error ${error.errorCodeName} http=${bad?.responseCode ?: "-"} host=${failingHost ?: "-"} " +
                    "${currentUrl?.let(::maskedTarget) ?: ""} +${elapsed()} ms",
                error.cause,
            )
            listener?.onError(StreamError(bad?.responseCode, error.errorCodeName, failingHost))
        }
    }

    fun start() {
        if (_player.value != null) return
        val dataSource = OkHttpDataSource.Factory(okHttp)
        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(HlsMediaSource.Factory(dataSource).setLoadErrorHandlingPolicy(NoRetryOn4xx))
            .build()
        exo.addListener(exoListener)
        exo.playWhenReady = true
        _player.value = exo
        logger.i(TAG, "player created")
        // Rebuilt after onStop: resume whatever was on screen.
        currentUrl?.let { prepare(exo, it) }
    }

    fun release() {
        val exo = _player.value ?: return
        exo.removeListener(exoListener)
        exo.release()
        _player.value = null
        logger.i(TAG, "player released")
    }

    override fun load(url: String) {
        val target = debugRewrite?.invoke(url) ?: url
        currentUrl = target
        logger.i(TAG, "load ${maskedTarget(target)}")
        _player.value?.let { prepare(it, target) }
    }

    override fun pause() {
        _player.value?.pause()
    }

    override fun resumeAtLiveEdge() {
        _player.value?.run {
            seekToDefaultPosition()
            play()
        }
    }

    override fun stop() {
        currentUrl = null
        _player.value?.run {
            stop()
            clearMediaItems()
        }
    }

    private fun prepare(exo: ExoPlayer, url: String) {
        loadedAt = System.currentTimeMillis()
        exo.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build())
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun elapsed() = System.currentTimeMillis() - loadedAt

    /** Host + path; the logger's masker hides the account id and tokens. */
    private fun maskedTarget(url: String): String =
        runCatching { URI(url).let { "${it.host}${it.rawPath}" } }.getOrDefault(url)

    private companion object {
        const val TAG = "Player"
    }
}

/**
 * Media3 defaults, except HTTP 4xx fail at once instead of being retried 3x inside the player:
 * [RetryPolicy] owns retries (and a 403 on an expired token needs a playlist refresh, not a retry).
 */
@OptIn(UnstableApi::class)
private object NoRetryOn4xx : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val e = info.exception
        if (e is HttpDataSource.InvalidResponseCodeException && e.responseCode in 400..499) return C.TIME_UNSET
        return super.getRetryDelayMsFor(info)
    }
}

/** [Scheduler] on the main looper. */
class MainScheduler : Scheduler {
    private val handler = Handler(Looper.getMainLooper())
    override fun schedule(delayMillis: Long, block: () -> Unit): Cancellable {
        val r = Runnable(block)
        handler.postDelayed(r, delayMillis)
        return Cancellable { handler.removeCallbacks(r) }
    }
}
