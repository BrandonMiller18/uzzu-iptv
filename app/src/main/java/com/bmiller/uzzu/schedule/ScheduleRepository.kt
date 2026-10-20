package com.bmiller.uzzu.schedule

import com.bmiller.uzzu.data.PlaylistState
import com.bmiller.uzzu.log.AppLogger
import com.bmiller.uzzu.playlist.League
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Merges the playlist with schedule data and exposes the result as one [model] StateFlow (games
 * with league, start time and status, sorted). Schedule data is fetched for today and yesterday
 * (device-local dates) for every league in parallel, every [intervalMillis] while at least one
 * consumer holds a [startPolling] handle. A failed fetch keeps that league/day's last known events.
 */
class ScheduleRepository(
    private val playlistState: StateFlow<PlaylistState>,
    private val source: ScheduleSource,
    private val matcher: ScheduleMatcher,
    private val logger: AppLogger,
    scope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val intervalMillis: Long = 60_000L,
) {
    private data class Key(val league: League, val date: LocalDate)

    private val cache = MutableStateFlow<Map<Key, List<ScheduleEvent>>>(emptyMap())
    private val consumers = MutableStateFlow(0)
    private val fetchLock = Mutex()
    @Volatile private var lastFetchAt = Long.MIN_VALUE / 2

    val model: StateFlow<PlaylistState> = combine(playlistState, cache) { state, cached -> merge(state, cached) }
        .stateIn(scope, SharingStarted.Eagerly, merge(playlistState.value, emptyMap()))

    init {
        scope.launch {
            consumers.map { it > 0 }.distinctUntilChanged().collectLatest { active ->
                if (!active) return@collectLatest
                logger.i(TAG, "polling started")
                try {
                    // Let the launch playlist fetch finish first; it gates S1 and competes for bandwidth/CPU.
                    playlistState.first { it !is PlaylistState.Idle && it !is PlaylistState.Loading }
                    val sinceLast = clock.millis() - lastFetchAt
                    if (sinceLast < intervalMillis) delay(intervalMillis - sinceLast)
                    while (true) {
                        refresh()
                        delay(intervalMillis)
                    }
                } finally {
                    logger.i(TAG, "polling stopped")
                }
            }
        }
    }

    /**
     * Registers a visible consumer (games list, overlay). Polling runs while any handle is open;
     * close the handle when the consumer is hidden.
     */
    fun startPolling(): AutoCloseable {
        consumers.update { it + 1 }
        val closed = AtomicBoolean(false)
        return AutoCloseable { if (closed.compareAndSet(false, true)) consumers.update { it - 1 } }
    }

    /** Fetches all leagues for today and yesterday once. Never throws (except cancellation). */
    suspend fun refresh() = fetchLock.withLock {
        val today = LocalDate.now(clock)
        val keys = source.leagues.flatMap { listOf(Key(it, today), Key(it, today.minusDays(1))) }
        val started = clock.millis()
        val results = coroutineScope {
            keys.map { key -> async { key to fetchOne(key) } }.awaitAll()
        }
        val ok = results.mapNotNull { (k, r) -> r.getOrNull()?.let { k to it } }.toMap()
        cache.update { old -> old.filterKeys { it in keys } + ok }
        lastFetchAt = clock.millis()
        logger.i(
            TAG,
            "ESPN fetch ${ok.size}/${keys.size} ok in ${lastFetchAt - started} ms, " +
                "${ok.values.sumOf { it.size }} events (days ${today.minusDays(1).format(DAY)} and ${today.format(DAY)})",
        )
    }

    private suspend fun fetchOne(key: Key): Result<List<ScheduleEvent>> = try {
        Result.success(source.fetch(key.league, key.date))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // No URL in the message: the masker would mangle the date; league + day identify the call.
        logger.w(TAG, "ESPN ${key.league.name.lowercase()} ${key.date.format(DAY)} failed, keeping last values", e)
        Result.failure(e)
    }

    private fun merge(state: PlaylistState, cached: Map<Key, List<ScheduleEvent>>): PlaylistState {
        if (state !is PlaylistState.Loaded) return state
        val events = cached.entries.groupBy({ it.key.league }, { it.value })
            .mapValues { (_, lists) -> lists.flatten().distinctBy { it.id } }
        return state.copy(playlist = state.playlist.copy(games = matcher.apply(state.playlist.games, events)))
    }

    private companion object {
        const val TAG = "Schedule"
        val DAY: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    }
}

