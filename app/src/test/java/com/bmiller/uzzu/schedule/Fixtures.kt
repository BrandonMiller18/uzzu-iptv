package com.bmiller.uzzu.schedule

import com.bmiller.uzzu.playlist.ChannelGrouper
import com.bmiller.uzzu.playlist.League
import com.bmiller.uzzu.playlist.M3uParser
import com.bmiller.uzzu.playlist.Playlist
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Shared test fixtures: bundled assets (read from src/main/assets) and recorded ESPN responses. */
object Fixtures {
    val teams: TeamDictionary by lazy { TeamDictionary.parse(File("src/main/assets/teams.json").readText()) }
    val channelGrouper: ChannelGrouper by lazy { ChannelGrouper.parse(File("src/main/assets/channel_groups.json").readText()) }
    val classifier by lazy { PlaylistClassifier(LeagueClassifier(teams), channelGrouper) }

    fun resource(path: String): String =
        requireNotNull(javaClass.classLoader!!.getResource(path)) { "missing fixture $path" }.readText()

    val sampleM3u: String by lazy { resource("fixtures/sample.m3u") }

    /** sample.m3u (optionally with extra #EXTINF entries appended), parsed and classified. */
    fun playlist(extraEntries: List<String> = emptyList()): Playlist {
        val extra = extraEntries.joinToString("") { name ->
            "#EXTINF:0 tvg-id=\"\" tvg-logo=\"\" group-title=\"Live-Games\",$name\nhttps://example.test/hls/x/${name.hashCode()}.m3u8\n"
        }
        return classifier.classify(M3uParser().parse(sampleM3u.trimEnd() + "\n" + extra))
    }

    fun slug(league: League) = EspnScheduleSource.PATHS.getValue(league).substringAfter('/')

    fun espn(league: League, date: LocalDate, suffix: String = ""): String? = runCatching {
        resource("fixtures/espn/${slug(league)}_${date.format(DateTimeFormatter.BASIC_ISO_DATE)}$suffix.json")
    }.getOrNull()
}

/** Serves the recorded ESPN JSON; [suffixFor] picks e.g. the synthetic "_in" variant. */
class FakeScheduleSource(
    var suffixFor: (League, LocalDate) -> String = { _, _ -> "" },
    var extraEvents: Map<League, List<ScheduleEvent>> = emptyMap(),
) : ScheduleSource {
    override val leagues: List<League> = EspnScheduleSource.PATHS.keys.toList()
    var failing: Set<League> = emptySet()
    val calls = mutableListOf<Pair<League, LocalDate>>()

    override suspend fun fetch(league: League, date: LocalDate): List<ScheduleEvent> {
        calls += league to date
        if (league in failing) throw IOException("simulated ESPN outage")
        val recorded = Fixtures.espn(league, date, suffixFor(league, date))
            ?: Fixtures.espn(league, date)
            ?: return emptyList()
        return EspnScheduleSource.parse(recorded, league) + extraEvents[league].orEmpty()
    }
}
