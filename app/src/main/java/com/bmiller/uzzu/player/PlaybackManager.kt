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
    /** O1: the playing item picked from another tab. Only the CH up/down group changes; no reload. */
    fun setGroup(context: PlayContext)
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
    /** Activity onStop (the player is released): stop the retry policy, keep the item. */
    fun onBackground() {}
    /** Activity onStart (the player is rebuilt): re-resolve the item and load it again. */
    fun onForeground() {}
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
        override fun load(url: String) {
            // Every load plays (PlayerController.prepare sets playWhenReady), so it ends a pause.
            if (_state.value.paused) _state.update { it.copy(paused = false) }
            player.load(url)
        }
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
        tuneTo(context)
    }

    /** False when the item can't be resolved against the current playlist (nothing changes). */
    private fun tuneTo(context: PlayContext): Boolean {
        val p = current() ?: return false.also { logger.w(TAG, "tune ignored: no playlist") }
        val ctx = context.copy(itemKeys = groupKeysFor(context))
        val r = resolve(p, ctx) ?: return false.also { logger.w(TAG, "tune ignored: ${ctx.currentKey} not in the playlist") }
        val resolvedCtx = ctx.copy(side = r.side)
        _state.update { s ->
            val prev = s.now?.context
            val last = if (prev != null && !prev.sameItem(resolvedCtx)) prev else s.last
            PlaybackState(now = NowPlaying(resolvedCtx, r), last = last, tuneCount = s.tuneCount + 1)
        }
        logger.i(TAG, "tune ${r.label} [${ctx.section.name.lowercase()}/${ctx.group}]")
        policy.start(r.url)
        return true
    }

    private fun groupKeysFor(context: PlayContext): List<String> =
        groups()?.let { groupKeys(it, context.section, context.group) }?.takeIf { context.currentKey in it } ?: context.itemKeys

    override fun setGroup(context: PlayContext) {
        val now = _state.value.now ?: return
        if (!now.context.sameItem(context)) return tune(context)
        if (now.context.group == context.group) return
        logger.i(TAG, "group -> ${context.group} (same item, no reload)")
        val ctx = now.context.copy(group = context.group, itemKeys = groupKeysFor(context))
        _state.update { it.copy(now = it.now?.copy(context = ctx)) }
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
        if (tuneTo(last)) return true
        // The previous item left the playlist (a game ended, refresh): drop the slot so Back
        // falls through to the list instead of doing nothing on every press.
        logger.w(TAG, "last channel is gone: clearing the slot")
        _state.update { it.copy(last = null) }
        return false
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
            policy.onResume()
            player.resumeAtLiveEdge()
        } else {
            logger.i(TAG, "pause")
            policy.onPause()
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

    override fun onBackground() {
        if (_state.value.now == null) return
        logger.i(TAG, "background: retry policy stopped")
        policy.cancel()
    }

    override fun onForeground() {
        val s = _state.value
        val now = s.now ?: return
        if (s.error != null) return // S6 stays up; Retry reloads
        // Re-resolve: the stream URL (token) may have changed while stopped.
        val r = current()?.let { resolve(it, now.context) } ?: now.resolved
        logger.i(TAG, "foreground: reloading ${r.label}")
        _state.update { it.copy(now = NowPlaying(now.context.copy(side = r.side), r), paused = false) }
        policy.start(r.url)
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
