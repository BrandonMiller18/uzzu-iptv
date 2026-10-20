package com.bmiller.uzzu.schedule

import com.bmiller.uzzu.data.PlaylistState
import com.bmiller.uzzu.log.AppLogger
import com.bmiller.uzzu.playlist.Game
import com.bmiller.uzzu.playlist.GameStatus
import com.bmiller.uzzu.playlist.League
import com.bmiller.uzzu.playlist.Playlist
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** 2026-10-20 12:00 in New York: today = 20261020, yesterday = 20261019 (device-local dates). */
    private val clock = Clock.fixed(Instant.parse("2026-10-20T16:00:00Z"), ZoneId.of("America/New_York"))
    private val today = LocalDate.of(2026, 10, 20)
    private val logger by lazy { AppLogger(File(tmp.root, "logs")) }

    private fun TestScope.repo(
        source: ScheduleSource,
        playlist: Playlist = Fixtures.playlist(),
    ): Pair<ScheduleRepository, MutableStateFlow<PlaylistState>> {
        val state = MutableStateFlow<PlaylistState>(PlaylistState.Loaded(playlist, 0L))
        val repo = ScheduleRepository(state, source, ScheduleMatcher(Fixtures.teams), logger, backgroundScope, clock)
        return repo to state
    }

    private suspend fun TestScope.refreshed(source: ScheduleSource, playlist: Playlist = Fixtures.playlist()): ScheduleRepository {
        val (repo, _) = repo(source, playlist)
        repo.refresh()
        runCurrent()
        return repo
    }

    private fun ScheduleRepository.games(): List<Game> = (model.value as PlaylistState.Loaded).playlist.games
    private fun List<Game>.byTitle(title: String) = single { it.title == title }

    @Test
    fun `recorded JSON yields start times and Final, sorted by start time then playlist order`() = runTest {
        val games = refreshed(FakeScheduleSource()).games()
        assertEquals(
            listOf(
                "Anaheim Ducks @ New York Rangers" to "2026-10-19T23:00:00Z",
                "Colorado Avalanche @ Philadelphia Flyers" to "2026-10-19T23:00:00Z",
                "San Jose Sharks @ Toronto Maple Leafs" to "2026-10-19T23:30:00Z",
                "Cleveland Guardians @ Tampa Bay Rays" to "2026-10-20T00:00:00Z",
                "Washington Commanders @ San Francisco 49ers" to "2026-10-20T00:15:00Z",
                "Pittsburgh Penguins @ Utah Mammoth" to "2026-10-20T01:00:00Z",
            ),
            games.map { it.title to it.startTime.toString() },
        )
        assertTrue(games.all { it.status == GameStatus.Final })
        assertEquals(
            listOf(League.NHL, League.NHL, League.NHL, League.MLB, League.NFL, League.NHL),
            games.map { it.league },
        )
    }

    @Test
    fun `in state yields Live with period label, Final stays in place`() = runTest {
        val source = FakeScheduleSource(suffixFor = { _, d -> if (d == today.minusDays(1)) "_in" else "" })
        val games = refreshed(source).games()
        assertEquals(GameStatus.Live("2nd 8:14"), games.byTitle("Anaheim Ducks @ New York Rangers").status)
        assertEquals(GameStatus.Live("Top 5th"), games.byTitle("Cleveland Guardians @ Tampa Bay Rays").status)
        assertEquals(GameStatus.Live("Q3 4:12"), games.byTitle("Washington Commanders @ San Francisco 49ers").status)
        assertEquals(GameStatus.Final, games.byTitle("Colorado Avalanche @ Philadelphia Flyers").status)
        // Status never changes the order.
        assertEquals("Anaheim Ducks @ New York Rangers", games[0].title)
        assertEquals("Colorado Avalanche @ Philadelphia Flyers", games[1].title)
    }

    @Test
    fun `scheduled events yield Scheduled`() = runTest {
        val playlist = Fixtures.playlist(listOf("Boston Celtics @ Detroit Pistons-H", "Winnipeg Jets @ St.Louis Blues-H"))
        val games = refreshed(FakeScheduleSource(), playlist).games()
        val celtics = games.byTitle("Boston Celtics @ Detroit Pistons")
        assertEquals(League.NBA, celtics.league)
        assertEquals(GameStatus.Scheduled, celtics.status)
        assertEquals(Instant.parse("2026-10-20T19:00:00Z"), celtics.startTime)
        val jets = games.byTitle("Winnipeg Jets @ St.Louis Blues")
        assertEquals(League.NHL, jets.league)
        assertEquals(Instant.parse("2026-10-21T00:00:00Z"), jets.startTime)
    }

    @Test
    fun `unknown matchup and pattern misses land in Ungrouped, last, in playlist order, no time`() = runTest {
        val playlist = Fixtures.playlist(listOf("Springfield Isotopes @ Shelbyville Shelbyvillians-H", "UFC 300 Main Card"))
        val games = refreshed(FakeScheduleSource(), playlist).games()
        val tail = games.takeLast(2)
        assertEquals(listOf("Springfield Isotopes @ Shelbyville Shelbyvillians", "UFC 300 Main Card"), tail.map { it.title })
        tail.forEach {
            assertEquals(League.UNGROUPED, it.league)
            assertNull(it.startTime)
            assertEquals(GameStatus.Unknown, it.status)
        }
    }

    @Test
    fun `unclassified games match the NCAAF scoreboard`() = runTest {
        val playlist = Fixtures.playlist(
            listOf(
                "Middle Tennessee Blue Raiders @ Florida International Panthers-H",
                "Missouri State @ Delaware-A", // ESPN location
                "South Alabama Jaguars @ Delaware Blue Hens-H", // only one side agrees with an event
            ),
        )
        val games = refreshed(FakeScheduleSource(), playlist).games()
        val mtsu = games.byTitle("Middle Tennessee Blue Raiders @ Florida International Panthers")
        assertEquals(League.NCAAF, mtsu.league)
        assertEquals(GameStatus.Scheduled, mtsu.status)
        assertEquals(Instant.parse("2026-10-20T23:00:00Z"), mtsu.startTime)
        assertEquals(League.NCAAF, games.byTitle("Missouri State @ Delaware").league)
        assertEquals(League.UNGROUPED, games.byTitle("South Alabama Jaguars @ Delaware Blue Hens").league)
    }

    @Test
    fun `ambiguous schools match only when both teams agree with a single event`() = runTest {
        fun ev(id: String, away: ScheduleTeam, home: ScheduleTeam) =
            ScheduleEvent(id, League.NCAAF, Instant.parse("2026-10-20T20:00:00Z"), GameStatus.Scheduled, away, home)
        val miamiFl = ScheduleTeam("Miami Hurricanes", "Miami", "Miami")
        val miamiOh = ScheduleTeam("Miami (OH) RedHawks", "Miami", "Miami (OH)")
        val usc = ScheduleTeam("USC Trojans", "USC", "USC")
        val scar = ScheduleTeam("South Carolina Gamecocks", "USC", "South Carolina")
        val extra = mapOf(League.NCAAF to listOf(ev("1", miamiFl, usc), ev("2", miamiOh, scar)))
        val playlist = Fixtures.playlist(listOf("Miami @ USC-H", "Miami Hurricanes @ USC Trojans-H", "Miami (OH) @ USC-H"))
        val games = refreshed(FakeScheduleSource(extraEvents = extra), playlist).games()
        assertEquals(League.UNGROUPED, games.byTitle("Miami @ USC").league) // agrees with two different events
        assertEquals(League.NCAAF, games.byTitle("Miami Hurricanes @ USC Trojans").league)
        assertEquals(League.NCAAF, games.byTitle("Miami (OH) @ USC").league)
    }

    @Test
    fun `ESPN failure keeps last known values and is logged`() = runTest {
        val source = FakeScheduleSource()
        val repo = refreshed(source)
        val before = repo.games()
        assertTrue(before.all { it.status == GameStatus.Final })
        source.failing = source.leagues.toSet()
        repo.refresh()
        runCurrent()
        assertEquals(before, repo.games())
        logger.flush()
        val log = logger.file.readText()
        assertTrue(log.contains("ESPN nhl 20261019 failed, keeping last values"))
        assertTrue(log.contains("ESPN fetch 0/10 ok"))
    }

    @Test
    fun `playlist refresh is re-merged with cached schedule data`() = runTest {
        val (repo, state) = repo(FakeScheduleSource())
        repo.refresh()
        runCurrent()
        state.value = PlaylistState.Loaded(Fixtures.playlist(listOf("Boston Celtics @ Detroit Pistons-A")), 1L)
        runCurrent()
        assertEquals(GameStatus.Scheduled, repo.games().byTitle("Boston Celtics @ Detroit Pistons").status)
        assertEquals(GameStatus.Final, repo.games().byTitle("Anaheim Ducks @ New York Rangers").status)
    }

    @Test
    fun `polls every 60 s only while a consumer is active`() = runTest {
        val source = FakeScheduleSource()
        val (repo, _) = repo(source)
        runCurrent()
        assertEquals(0, source.calls.size)

        val handle = repo.startPolling()
        runCurrent()
        assertEquals(10, source.calls.size) // 5 leagues x (today, yesterday)
        assertEquals(setOf(today, today.minusDays(1)), source.calls.map { it.second }.toSet())
        advanceTimeBy(60_001)
        assertEquals(20, source.calls.size)

        handle.close()
        handle.close() // idempotent
        runCurrent()
        advanceTimeBy(300_000)
        assertEquals(20, source.calls.size)

        // Two consumers: polling continues until both are gone.
        val a = repo.startPolling()
        val b = repo.startPolling()
        runCurrent()
        advanceTimeBy(60_001)
        val running = source.calls.size
        assertTrue(running > 20)
        a.close()
        advanceTimeBy(60_001)
        assertTrue(source.calls.size > running)
        b.close()
        runCurrent()
        val stopped = source.calls.size
        advanceTimeBy(300_000)
        assertEquals(stopped, source.calls.size)
    }
}
