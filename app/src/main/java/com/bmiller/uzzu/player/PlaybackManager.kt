package com.bmiller.uzzu.player

import com.bmiller.uzzu.data.PlaylistSource
import com.bmiller.uzzu.data.PlaylistState
import com.bmiller.uzzu.log.AppLogger
import com.bmiller.uzzu.log.SecretMasker
import com.bmiller.uzzu.playlist.FeedSide
import com.bmiller.uzzu.playlist.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The playing item: where it came from ([context], incl. current group and side) and how it resolved. */
data class NowPlaying(val context: PlayContext, val resolved: Resolved)

data class PlaybackState(
    val now: NowPlaying? = null,
    /** PLAN "last channel" slot: exactly one previous item, including its feed side. */
    val last: PlayContext? = null,
    /** Set when the retry policy is exhausted (S6). */
    val error: ErrorReport? = null,
    val paused: Boolean = false,
    /** Increments on every tune (the tune banner shows for 3 s each time). */
    val tuneCount: Int = 0,
)

/** What the player screen can ask for. Implemented by [PlaybackManager]. */
interface PlayerControls {
    val state: StateFlow<PlaybackState>
    fun tune(context: PlayContext)
    /** CH up (-1) / down (+1) within the current group. */
    fun step(delta: Int)
    /** Back: toggle to the last channel. False when the slot is empty (caller returns to the list). */
    fun toggleLast(): Boolean
    fun flipSide()
    fun togglePause()
    /** S6 Retry: re-runs the full policy on the same item. */
    fun retry()
    /** Leaving the player: stop, forget the item and the last-channel slot. */
    fun stop()
    /** O1 opened: silent playlist refresh when older than 10 min. Never interrupts playback. */
    fun overlayOpened()
}

/**
 * Owns the player session: resolves items by key + side at tune time, the last-channel slot, CH
 * up/down, the retry policy and S6 state. Main thread only.
 *
 * Merge rule (PLAN): a playlist refresh never touches the playing stream; the new URL is picked up
 * on the next tune (or a retry), because every tune resolves against the current playlist.
 */
class PlaybackManager(
    private val player: StreamPlayer,
    private val playlist: PlaylistSource,
    /** Playlist merged with the schedule (sorted games): the source of group order. */
    private val model: StateFlow<PlaylistState>,
    private val network: () -> String,
    private val logger: AppLogger,
    private val scope: CoroutineScope,
    private val scheduler: Scheduler,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Debug only: never show S6 (keeps the overlay usable for screenshots with fake fixture URLs). */
    private val suppressErrors: () -> Boolean = { false },
) : PlayerControls {
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val policy = RetryPolicy(clock, scheduler, object : RetryPolicy.Effects {
        override fun load(url: String) = player.load(url)
        override fun playlistAgeMillis(): Long? = playlistAge()
        override fun refreshAndResolve(onResult: (RefreshOutcome) -> Unit) {
            val ctx = _state.value.now?.context ?: return onResult(RefreshOutcome.ItemGone)
            scope.launch {
                val outcome = playlist.refresh().fold(
                    onSuccess = { p -> resolve(p, ctx, strictSide = true)?.let { RefreshOutcome.NewUrl(it.url) } ?: RefreshOutcome.ItemGone },
                    onFailure = { RefreshOutcome.Failed(SecretMasker.mask(it.message ?: it.javaClass.simpleName)) },
                )
                onResult(outcome)
            }
        }

        override fun exhausted(result: Exhausted) = onExhausted(result)
        override fun log(level: Char, msg: String) = logger.log(level, TAG, msg, null)
    })

    init {
        player.listener = object : StreamPlayer.Listener {
            override fun onBuffering() = policy.onBuffering()
            override fun onReady() = policy.onReady()
            override fun onFirstFrame() = policy.onFirstFrame()
            override fun onError(error: StreamError) = policy.onError(error)
        }
    }

    private fun current(): Playlist? = (playlist.state.value as? PlaylistState.Loaded)?.playlist
    private fun groups(): Playlist? = (model.value as? PlaylistState.Loaded)?.playlist ?: current()

    private fun playlistAge(): Long? = (playlist.state.value as? PlaylistState.Loaded)?.let { clock() - it.fetchedAtMillis }

    override fun tune(context: PlayContext) {
        val p = current() ?: return logger.w(TAG, "tune ignored: no playlist")
        val keys = groups()?.let { groupKeys(it, context.section, context.group) }?.takeIf { context.currentKey in it } ?: context.itemKeys
        val ctx = context.copy(itemKeys = keys)
        val r = resolve(p, ctx) ?: return logger.w(TAG, "tune ignored: ${ctx.currentKey} not in the playlist")
        val resolvedCtx = ctx.copy(side = r.side)
        _state.update { s ->
            val prev = s.now?.context
            val last = if (prev != null && !prev.sameItem(resolvedCtx)) prev else s.last
            PlaybackState(now = NowPlaying(resolvedCtx, r), last = last, tuneCount = s.tuneCount + 1)
        }
        logger.i(TAG, "tune ${r.label} [${ctx.section.name.lowercase()}/${ctx.group}]")
        policy.start(r.url)
    }

    override fun step(delta: Int) {
        val s = _state.value
        if (s.error != null) return
        val ctx = s.now?.context ?: return
        val target = groups()?.let { stepTarget(it, ctx, delta) } ?: return
        tune(target)
    }

    override fun toggleLast(): Boolean {
        val last = _state.value.last ?: return false
        logger.i(TAG, "back: toggle to last channel")
        tune(last)
        return true
    }

    override fun flipSide() {
        val ctx = _state.value.now?.context ?: return
        if (ctx.section != Section.GAMES) return
        val game = current()?.games?.firstOrNull { it.id == ctx.currentKey } ?: return
        val other = if (ctx.side == FeedSide.HOME) FeedSide.AWAY else FeedSide.HOME
        if (game.hasSide(other)) tune(ctx.copy(side = other, chosenSide = other))
    }

    override fun togglePause() {
        val s = _state.value
        if (s.now == null || s.error != null) return
        if (s.paused) {
            logger.i(TAG, "resume at live edge")
            player.resumeAtLiveEdge()
        } else {
            logger.i(TAG, "pause")
            player.pause()
        }
        _state.update { it.copy(paused = !s.paused) }
    }

    override fun retry() {
        val s = _state.value
        val ctx = s.now?.context ?: return
        val r = current()?.let { resolve(it, ctx) } ?: s.now.resolved
        logger.i(TAG, "retry from error screen: ${r.label}")
        _state.update { it.copy(now = NowPlaying(ctx.copy(side = r.side), r), error = null, paused = false, tuneCount = it.tuneCount + 1) }
        policy.start(r.url)
    }

    override fun stop() {
        policy.cancel()
        player.stop()
        if (_state.value.now != null) logger.i(TAG, "playback stopped")
        _state.value = PlaybackState(tuneCount = _state.value.tuneCount)
    }

    override fun overlayOpened() {
        val age = playlistAge() ?: return
        if (age <= STALE_MILLIS) return
        logger.i(TAG, "overlay opened, playlist is ${age / 60_000} min old: silent refresh")
        scope.launch { playlist.refresh() }
    }

    private fun onExhausted(result: Exhausted) {
        val now = _state.value.now ?: return
        if (suppressErrors()) {
            logger.w(TAG, "debug: error screen suppressed")
            return
        }
        player.stop()
        val report = ErrorReport.build(
            item = now.resolved,
            failingUrl = result.attempts.lastOrNull()?.url ?: now.resolved.url,
            result = result,
            playlistFetchedAt = (playlist.state.value as? PlaylistState.Loaded)?.fetchedAtMillis,
            lastRefresh = playlist.lastRefresh,
            network = network(),
            nowMillis = clock(),
        )
        logger.e(TAG, "error screen: ${report.summary} | ${report.lines.joinToString(" | ") { "${it.label}=${it.value}" }}")
        _state.update { it.copy(error = report, paused = false) }
    }

    private companion object {
        const val TAG = "Playback"
        const val STALE_MILLIS = 10 * 60_000L
    }
}

/** Same item and side (a re-tune of what is playing does not overwrite the last-channel slot). */
fun PlayContext.sameItem(other: PlayContext) = section == other.section && currentKey == other.currentKey && side == other.side
