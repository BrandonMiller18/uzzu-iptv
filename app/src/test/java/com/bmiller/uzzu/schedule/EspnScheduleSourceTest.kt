package com.bmiller.uzzu.schedule

import com.bmiller.uzzu.playlist.GameStatus
import com.bmiller.uzzu.playlist.League
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

class EspnScheduleSourceTest {
    private val server = MockWebServer()
    private lateinit var source: EspnScheduleSource
    private val requests = mutableListOf<RecordedRequest>()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val url = request.requestUrl!!
                val slug = url.pathSegments.dropLast(1).last()
                if (slug == "nba") return MockResponse().setResponseCode(503)
                val body = runCatching { Fixtures.resource("fixtures/espn/${slug}_${url.queryParameter("dates")}.json") }
                    .getOrNull() ?: return MockResponse().setResponseCode(404)
                return MockResponse().setBody(body)
            }
        }
        server.start()
        source = EspnScheduleSource(OkHttpClient(), server.url("/"), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `builds scoreboard URLs, NCAAF with groups=80`() = runTest {
        source.fetch(League.NHL, LocalDate.of(2026, 10, 19))
        source.fetch(League.NCAAF, LocalDate.of(2026, 10, 20))
        assertEquals("/apis/site/v2/sports/hockey/nhl/scoreboard?dates=20261019", requests[0].path)
        assertEquals("/apis/site/v2/sports/football/college-football/scoreboard?dates=20261020&groups=80", requests[1].path)
    }

    @Test
    fun `recorded post events yield start time and Final`() = runTest {
        val nhl = source.fetch(League.NHL, LocalDate.of(2026, 10, 19))
        assertEquals(4, nhl.size)
        val ducks = nhl.single { it.away.displayName == "Anaheim Ducks" }
        assertEquals("New York Rangers", ducks.home.displayName)
        assertEquals("Ducks", ducks.away.shortDisplayName)
        assertEquals("Anaheim", ducks.away.location)
        assertEquals(Instant.parse("2026-10-19T23:00:00Z"), ducks.startTime)
        assertEquals(GameStatus.Final, ducks.status)
        assertTrue(nhl.all { it.league == League.NHL && it.status == GameStatus.Final })
    }

    @Test
    fun `recorded pre events yield Scheduled`() = runTest {
        val ncaaf = source.fetch(League.NCAAF, LocalDate.of(2026, 10, 20))
        assertEquals(3, ncaaf.size)
        assertTrue(ncaaf.all { it.status == GameStatus.Scheduled })
        assertEquals(Instant.parse("2026-10-20T23:30:00Z"), ncaaf.single { it.away.location == "South Alabama" }.startTime)
    }

    @Test
    fun `in state yields Live with shortDetail`() {
        val live = EspnScheduleSource.parse(Fixtures.resource("fixtures/espn/mlb_20261019_in.json"), League.MLB).single()
        assertEquals(GameStatus.Live("Top 5th"), live.status)
    }

    @Test
    fun `empty day parses to no events`() = runTest {
        assertEquals(emptyList<ScheduleEvent>(), source.fetch(League.MLB, LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun `HTTP error throws`() = runTest {
        try {
            source.fetch(League.NBA, LocalDate.of(2026, 10, 20))
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("HTTP 503", e.message)
        }
    }
}
