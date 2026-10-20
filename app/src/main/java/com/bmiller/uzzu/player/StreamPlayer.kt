package com.bmiller.uzzu.player

/** A playback failure as the retry policy sees it. [host] is the host of the failing request, if known. */
data class StreamError(val httpStatus: Int?, val errorCodeName: String, val host: String? = null) {
    companion object {
        /** Stall detection (no rendered frame for 15 s while buffering) reported as an error. */
        const val STALL = "STALL_NO_FRAME_15S"
    }
}

/** The one video player, as seen by [PlaybackManager]. Implemented by [PlayerController]; faked in tests. */
interface StreamPlayer {
    interface Listener {
        fun onBuffering()
        fun onReady()
        fun onFirstFrame()
        fun onError(error: StreamError)
    }

    var listener: Listener?

    /** setMediaItem + prepare + play on the existing instance. */
    fun load(url: String)
    fun pause()

    /** Resume at the live edge. */
    fun resumeAtLiveEdge()

    /** Stop and clear the media item; the instance stays alive. */
    fun stop()
}

/** Delayed work on the main thread; a virtual-time fake in tests. */
fun interface Scheduler {
    fun schedule(delayMillis: Long, block: () -> Unit): Cancellable
}

fun interface Cancellable {
    fun cancel()
}
