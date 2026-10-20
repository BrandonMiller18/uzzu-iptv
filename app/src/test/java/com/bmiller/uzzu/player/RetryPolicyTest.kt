package com.bmiller.uzzu.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {
    private val sched = FakeScheduler()
    private val loads = mutableListOf<Pair<Long, String>>()
    private var age: Long? = 60_000L
    private var refreshCalls = 0
    private var onRefresh: ((RefreshOutcome) -> Unit) -> Unit = { it(RefreshOutcome.NewUrl(NEW)) }
    private var exhausted: Exhausted? = null
    val logs = mutableListOf<String>()

    private val policy = RetryPolicy(sched.clock, sched, object : RetryPolicy.Effects {
        override fun load(url: String) { loads += sched.now to url }
        override fun playlistAgeMillis() = age
        override fun refreshAndResolve(onResult: (RefreshOutcome) -> Unit) { refreshCalls++; onRefresh(onResult) }
        override fun exhausted(result: Exhausted) { exhausted = result }
        override fun log(level: Char, msg: String) { logs += "$level $msg" }
    })

    private fun err(status: Int? = 500, host: String? = null) = policy.onError(StreamError(status, "ERROR_CODE_IO_BAD_HTTP_STATUS", host))

    @Test fun `same URL is retried twice with 2 s then 5 s backoff, then S6`() {
        val t0 = sched.now
        policy.start(HOME)
        err(404)
        sched.advance(1_999)
        assertEquals(1, loads.size)
        sched.advance(1)
        assertEquals(listOf(t0 to HOME, t0 + 2_000 to HOME), loads)
        err(404)
        sched.advance(4_999)
        assertEquals(2, loads.size)
        sched.advance(1)
        assertEquals(t0 + 7_000 to HOME, loads.last())
        assertNull(exhausted)
        err(404)
        val ex = exhausted!!
        assertEquals(3, ex.attempts.size)
        assertEquals(listOf(t0, t0 + 2_000, t0 + 7_000), ex.attempts.map { it.atMillis })
        assertTrue(ex.attempts.all { it.httpStatus == 404 && it.errorCodeName == "ERROR_CODE_IO_BAD_HTTP_STATUS" })
        assertFalse(ex.refreshed)
        assertEquals(0, refreshCalls)
        assertFalse(policy.active)
        assertEquals(0, sched.pending)
    }

    @Test fun `fastly 403 re-fetches the playlist and retries once with the new URL immediately`() {
        policy.start(AWAY)
        err(403, host = "tran03.global.ssl.fastly.net")
        assertEquals(1, refreshCalls)
        assertEquals(listOf(AWAY, NEW), loads.map { it.second })
        assertTrue(policy.attempts.last().viaRefresh)
        assertEquals(loads[0].first, loads[1].first)
    }

    @Test fun `every fastly auth-type status triggers the refresh path, others do not`() {
        for (code in listOf(401, 403, 404, 410)) {
            refreshCalls = 0
            policy.start(AWAY)
            err(code)
            assertEquals("HTTP $code", 1, refreshCalls)
        }
        refreshCalls = 0
        policy.start(AWAY)
        err(500)
        assertEquals(0, refreshCalls)
        policy.start(HOME)
        err(403) // not fastly, fresh playlist
        assertEquals(0, refreshCalls)
    }

    @Test fun `stale playlist refreshes on any error on any host`() {
        age = 11 * 60_000L
        policy.start(HOME)
        err(null)
        assertEquals(1, refreshCalls)
        assertEquals(NEW, loads.last().second)
    }

    @Test fun `only one refresh per session, then same-URL backoff on the new URL, then S6`() {
        policy.start(AWAY)
        err(403)
        err(403)
        assertEquals(1, refreshCalls)
        sched.advance(2_000)
        err(403)
        sched.advance(5_000)
        err(403)
        val ex = exhausted!!
        assertEquals(listOf(AWAY, NEW, NEW, NEW), ex.attempts.map { it.url })
        assertTrue(ex.refreshed)
        assertEquals(RefreshOutcome.NewUrl(NEW), ex.refreshOutcome)
    }

    @Test fun `item gone after refresh goes straight to S6`() {
        onRefresh = { it(RefreshOutcome.ItemGone) }
        policy.start(AWAY)
        err(410)
        assertEquals(RefreshOutcome.ItemGone, exhausted!!.refreshOutcome)
        assertEquals(1, loads.size)
    }

    @Test fun `failed refresh falls back to same-URL retries`() {
        onRefresh = { it(RefreshOutcome.Failed("timeout")) }
        policy.start(AWAY)
        err(403)
        sched.advance(2_000)
        assertEquals(listOf(AWAY, AWAY), loads.map { it.second })
    }

    @Test fun `refresh result arriving after a new tune is ignored`() {
        var pendingResult: ((RefreshOutcome) -> Unit)? = null
        onRefresh = { pendingResult = it }
        policy.start(AWAY)
        err(403)
        policy.start(HOME)
        pendingResult!!(RefreshOutcome.NewUrl(NEW))
        assertEquals(listOf(AWAY, HOME), loads.map { it.second })
    }

    @Test fun `no frame for 15 s while buffering counts as an error`() {
        policy.start(HOME)
        policy.onBuffering()
        sched.advance(14_999)
        assertEquals(1, loads.size)
        sched.advance(1)
        assertEquals(StreamError.STALL, policy.attempts.single().errorCodeName)
        sched.advance(2_000)
        assertEquals(2, loads.size)
    }

    @Test fun `a frame or READY cancels the stall timer`() {
        policy.start(HOME)
        policy.onBuffering()
        sched.advance(10_000)
        policy.onFirstFrame()
        sched.advance(30_000)
        assertEquals(1, loads.size)
        assertNull(exhausted)
        policy.onBuffering()
        sched.advance(5_000)
        policy.onReady()
        sched.advance(30_000)
        assertEquals(1, loads.size)
    }

    @Test fun `three stalls exhaust the policy`() {
        policy.start(HOME)
        repeat(3) { i ->
            policy.onBuffering()
            sched.advance(15_000)
            if (i < 2) sched.advance(if (i == 0) 2_000 else 5_000)
        }
        assertEquals(StreamError.STALL, exhausted!!.lastError.errorCodeName)
    }

    @Test fun `a stream that played 30 s gets a fresh budget`() {
        policy.start(HOME)
        err(500)
        sched.advance(2_000)
        policy.onFirstFrame()
        sched.advance(60_000)
        err(500)
        sched.advance(2_000)
        assertEquals(3, loads.size)
        assertNull(exhausted)
        assertTrue(logs.any { "fresh retry budget" in it })
    }

    @Test fun `cancel drops pending retries`() {
        policy.start(HOME)
        err(500)
        policy.cancel()
        sched.advance(60_000)
        assertEquals(1, loads.size)
        err(500)
        assertNull(exhausted)
    }

    private companion object {
        const val HOME = "https://uzzu.tv/hls/10000007220/g4678.m3u8"
        const val AWAY = "https://tran03.global.ssl.fastly.net/1792476833~aaaa/perdias/444734-away/chunks.m3u8"
        const val NEW = "https://tran03.global.ssl.fastly.net/1792999999~bbbb/perdias/444734-away/chunks.m3u8"
    }
}
