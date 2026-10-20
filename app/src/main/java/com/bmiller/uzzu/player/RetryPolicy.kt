package com.bmiller.uzzu.player

import java.net.URI

/** One load of a stream URL and how it ended ([errorCodeName] null while running or after success). */
data class Attempt(
    val atMillis: Long,
    val url: String,
    val httpStatus: Int? = null,
    val errorCodeName: String? = null,
    val viaRefresh: Boolean = false,
)

sealed interface RefreshOutcome {
    data class NewUrl(val url: String) : RefreshOutcome
    /** The refreshed playlist no longer has this game/side or channel. */
    data object ItemGone : RefreshOutcome
    data class Failed(val message: String) : RefreshOutcome
}

/** Why the policy gave up (input for S6). */
data class Exhausted(
    val attempts: List<Attempt>,
    val lastError: StreamError,
    val refreshed: Boolean,
    val refreshOutcome: RefreshOutcome?,
)

/**
 * PLAN "Retry policy" for one tune session:
 * 1. On an error, retry the same URL up to [backoffMillis].size times (2 s, then 5 s).
 * 2. If the error is HTTP 401/403/404/410 on a fastly.net host, or on any host while the playlist
 *    is older than [staleAfterMillis], first re-fetch the playlist, re-resolve the same item/side
 *    and retry once with the new URL (immediately). Only one refresh per session.
 * 3. No rendered frame for [stallMillis] while buffering counts as an error.
 * 4. When nothing is left, [Effects.exhausted] (S6).
 * A session that has been playing for [healthyResetMillis] gets a fresh budget on its next error.
 */
class RetryPolicy(
    private val clock: () -> Long,
    private val scheduler: Scheduler,
    private val effects: Effects,
    private val backoffMillis: List<Long> = listOf(2_000L, 5_000L),
    private val staleAfterMillis: Long = 10 * 60_000L,
    private val stallMillis: Long = 15_000L,
    private val healthyResetMillis: Long = 30_000L,
) {
    interface Effects {
        fun load(url: String)
        fun playlistAgeMillis(): Long?
        fun refreshAndResolve(onResult: (RefreshOutcome) -> Unit)
        fun exhausted(result: Exhausted)
        fun log(level: Char, msg: String)
    }

    private var url: String? = null
    private val _attempts = mutableListOf<Attempt>()
    val attempts: List<Attempt> get() = _attempts.toList()
    private var sameUrlRetries = 0
    private var refreshed = false
    private var refreshOutcome: RefreshOutcome? = null
    private var pending: Cancellable? = null
    private var stallTimer: Cancellable? = null
    private var playingSince: Long? = null
    private var lastErrorWasStall = false
    /** The user paused: buffering then is not a stall. */
    private var userPaused = false
    /** Bumped on every start/cancel so late callbacks (refresh results) from an old session are dropped. */
    private var session = 0
    var active = false
        private set

    /** New tune: reset the budget and load [url]. */
    fun start(url: String) {
        cancel()
        active = true
        this.url = url
        load(url, viaRefresh = false)
    }

    /** Stop everything (leaving the player, S6, new tune). */
    fun cancel() {
        session++
        active = false
        pending?.cancel()
        pending = null
        stopStallTimer()
        _attempts.clear()
        sameUrlRetries = 0
        refreshed = false
        refreshOutcome = null
        playingSince = null
        lastErrorWasStall = false
        userPaused = false
    }

    fun onPause() {
        userPaused = true
        stopStallTimer()
    }

    fun onResume() {
        userPaused = false
    }

    fun onBuffering() {
        // A pending retry reloads anyway; a paused player buffering is not stalled.
        if (!active || stallTimer != null || pending != null || userPaused) return
        val s = session
        stallTimer = scheduler.schedule(stallMillis) {
            stallTimer = null
            if (s == session && active) {
                effects.log('W', "stall: no frame for ${stallMillis / 1000} s while buffering")
                onError(StreamError(null, StreamError.STALL))
            }
        }
    }

    fun onReady() = stopStallTimer()

    fun onFirstFrame() {
        stopStallTimer()
        if (lastErrorWasStall && pending != null) {
            // A late segment arrived during the stall backoff: the stream recovered, don't reload it.
            effects.log('I', "frame rendered during stall backoff: retry cancelled")
            pending?.cancel()
            pending = null
        }
        lastErrorWasStall = false
        if (playingSince == null) playingSince = clock()
    }

    fun onError(error: StreamError) {
        if (!active || pending != null) return
        stopStallTimer()
        val now = clock()
        playingSince?.let { since ->
            if (now - since >= healthyResetMillis) {
                effects.log('I', "error after ${(now - since) / 1000} s of playback: fresh retry budget")
                val last = _attempts.lastOrNull()
                _attempts.clear()
                last?.let { _attempts += it }
                sameUrlRetries = 0
                refreshed = false
                refreshOutcome = null
            }
        }
        playingSince = null
        lastErrorWasStall = error.errorCodeName == StreamError.STALL
        _attempts.lastOrNull()?.let { _attempts[_attempts.lastIndex] = it.copy(httpStatus = error.httpStatus, errorCodeName = error.errorCodeName) }
        val current = url ?: return

        val reason = refreshReason(error, current)
        if (reason != null && !refreshed) {
            refreshed = true
            effects.log('W', "${describe(error)}: $reason, re-fetching playlist")
            val s = session
            pending = Cancellable { }
            effects.refreshAndResolve { outcome ->
                if (s != session) return@refreshAndResolve
                pending = null
                refreshOutcome = outcome
                when (outcome) {
                    is RefreshOutcome.NewUrl -> {
                        effects.log('I', "retry with refreshed URL ${if (outcome.url == current) "(unchanged)" else "(new)"}")
                        url = outcome.url
                        load(outcome.url, viaRefresh = true)
                    }
                    RefreshOutcome.ItemGone -> {
                        effects.log('W', "item no longer in the refreshed playlist")
                        exhaust(error)
                    }
                    is RefreshOutcome.Failed -> {
                        effects.log('W', "playlist refresh failed (${outcome.message}), falling back to same-URL retries")
                        backoffOrExhaust(error, current)
                    }
                }
            }
            return
        }
        backoffOrExhaust(error, current)
    }

    private fun backoffOrExhaust(error: StreamError, current: String) {
        if (sameUrlRetries < backoffMillis.size) {
            val delay = backoffMillis[sameUrlRetries++]
            effects.log('W', "${describe(error)}: retry $sameUrlRetries/${backoffMillis.size} in ${delay / 1000} s")
            val s = session
            pending = scheduler.schedule(delay) {
                if (s != session) return@schedule
                pending = null
                load(current, viaRefresh = false)
            }
        } else {
            exhaust(error)
        }
    }

    private fun exhaust(error: StreamError) {
        val result = Exhausted(attempts, error, refreshed, refreshOutcome)
        effects.log('E', "giving up after ${result.attempts.size} attempts: ${describe(error)}")
        cancel()
        effects.exhausted(result)
    }

    private fun load(url: String, viaRefresh: Boolean) {
        userPaused = false // every load plays
        _attempts += Attempt(clock(), url, viaRefresh = viaRefresh)
        effects.load(url)
    }

    private fun stopStallTimer() {
        stallTimer?.cancel()
        stallTimer = null
    }

    private fun refreshReason(error: StreamError, current: String): String? {
        val hosts = listOfNotNull(error.host, hostOf(current))
        if (error.httpStatus in AUTH_STATUSES && hosts.any { it.endsWith("fastly.net", ignoreCase = true) }) {
            return "HTTP ${error.httpStatus} on a Fastly URL"
        }
        // PLAN: the auth-type status qualifies both clauses (Fastly host, or any host once stale).
        if (error.httpStatus !in AUTH_STATUSES) return null
        val age = effects.playlistAgeMillis()
        if (age != null && age > staleAfterMillis) return "HTTP ${error.httpStatus} with a playlist ${age / 60_000} min old"
        return null
    }

    private fun describe(e: StreamError) = "${e.errorCodeName}${e.httpStatus?.let { " http=$it" } ?: ""}"

    companion object {
        val AUTH_STATUSES = setOf(401, 403, 404, 410)
        fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()
    }
}
