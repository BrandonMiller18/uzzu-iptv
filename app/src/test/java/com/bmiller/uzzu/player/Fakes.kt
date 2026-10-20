package com.bmiller.uzzu.player

import com.bmiller.uzzu.data.PlaylistSource
import com.bmiller.uzzu.data.PlaylistState
import com.bmiller.uzzu.data.RefreshRecord
import com.bmiller.uzzu.playlist.Playlist
import kotlinx.coroutines.flow.MutableStateFlow

/** Virtual-time [Scheduler] + clock. */
class FakeScheduler : Scheduler {
    var now = 1_000_000L
        private set
    private data class Task(val at: Long, val seq: Int, val block: () -> Unit, var cancelled: Boolean = false)
    private val tasks = mutableListOf<Task>()
    private var seq = 0

    val clock: () -> Long = { now }
    val pending: Int get() = tasks.count { !it.cancelled }

    override fun schedule(delayMillis: Long, block: () -> Unit): Cancellable {
        val t = Task(now + delayMillis, seq++, block)
        tasks += t
        return Cancellable { t.cancelled = true }
    }

    fun advance(millis: Long) {
        val end = now + millis
        while (true) {
            val next = tasks.filter { !it.cancelled && it.at <= end }.minWithOrNull(compareBy({ it.at }, { it.seq })) ?: break
            tasks.remove(next)
            now = next.at
            next.block()
        }
        now = end
        tasks.removeAll { it.cancelled }
    }
}

class FakeStreamPlayer : StreamPlayer {
    override var listener: StreamPlayer.Listener? = null
    val loads = mutableListOf<String>()
    var paused = false
    var liveEdgeResumes = 0
    var stops = 0

    override fun load(url: String) { loads += url }
    override fun pause() { paused = true }
    override fun resumeAtLiveEdge() { paused = false; liveEdgeResumes++ }
    override fun stop() { stops++ }

    fun fail(status: Int? = null, code: String = "ERROR_CODE_IO_BAD_HTTP_STATUS", host: String? = null) =
        listener!!.onError(StreamError(status, code, host))
}

class FakePlaylistSource(playlist: Playlist, fetchedAt: Long) : PlaylistSource {
    override val state = MutableStateFlow<PlaylistState>(PlaylistState.Loaded(playlist, fetchedAt))
    override var lastRefresh: RefreshRecord? = RefreshRecord(fetchedAt, true, "OK")
    var refreshes = 0
    /** Next refresh result; null = failure. */
    var next: (() -> Playlist?)? = null
    var clock: () -> Long = { fetchedAt }

    override suspend fun refresh(): Result<Playlist> {
        refreshes++
        val p = next?.invoke() ?: (state.value as PlaylistState.Loaded).playlist.takeIf { next == null }
        return if (p != null) {
            state.value = PlaylistState.Loaded(p, clock())
            lastRefresh = RefreshRecord(clock(), true, "OK")
            Result.success(p)
        } else {
            lastRefresh = RefreshRecord(clock(), false, "Network error: timeout")
            Result.failure(java.io.IOException("Network error: timeout"))
        }
    }
}
