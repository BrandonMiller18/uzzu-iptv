package com.bmiller.uzzu.schedule

import com.bmiller.uzzu.playlist.GameStatus
import com.bmiller.uzzu.playlist.League
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * ESPN's public (undocumented) scoreboard API:
 * `/apis/site/v2/sports/{sport}/{league}/scoreboard?dates=yyyyMMdd` (+ `groups=80` for all FBS).
 * Only names, start time and status are decoded; score fields are never read.
 */
class EspnScheduleSource(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://site.api.espn.com/".toHttpUrl(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ScheduleSource {

    override val leagues: List<League> = PATHS.keys.toList()

    override suspend fun fetch(league: League, date: LocalDate): List<ScheduleEvent> = withContext(io) {
        val path = PATHS[league] ?: return@withContext emptyList()
        val url = baseUrl.newBuilder()
            .addPathSegments("apis/site/v2/sports/$path/scoreboard")
            .addQueryParameter("dates", date.format(DateTimeFormatter.BASIC_ISO_DATE))
            .apply { if (league == League.NCAAF) addQueryParameter("groups", "80") }
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            parse(resp.body.string(), league)
        }
    }

    companion object {
        val PATHS = linkedMapOf(
            League.MLB to "baseball/mlb",
            League.NHL to "hockey/nhl",
            League.NBA to "basketball/nba",
            League.NFL to "football/nfl",
            League.NCAAF to "football/college-football",
        )

        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        /** Decodes a scoreboard response. Events that lack two named competitors or a date are skipped. */
        fun parse(text: String, league: League): List<ScheduleEvent> =
            json.decodeFromString<EspnScoreboard>(text).events.mapNotNull { e ->
                val competitors = e.competitions.firstOrNull()?.competitors.orEmpty()
                val away = competitors.firstOrNull { it.homeAway == "away" }?.team?.toScheduleTeam()
                val home = competitors.firstOrNull { it.homeAway == "home" }?.team?.toScheduleTeam()
                val start = parseInstant(e.date)
                if (away == null || home == null || start == null) return@mapNotNull null
                val status = e.status ?: e.competitions.firstOrNull()?.status
                ScheduleEvent(e.id, league, start, status.toGameStatus(), away, home)
            }

        private fun parseInstant(s: String?): Instant? = s?.let {
            runCatching { Instant.parse(it) }.recoverCatching { _ -> OffsetDateTime.parse(it).toInstant() }.getOrNull()
        }

        private fun EspnTeam.toScheduleTeam(): ScheduleTeam? =
            displayName?.takeIf { it.isNotBlank() }?.let { ScheduleTeam(it, shortDisplayName.orEmpty(), location.orEmpty()) }

        private fun EspnStatus?.toGameStatus(): GameStatus {
            val type = this?.type ?: return GameStatus.Unknown
            if (type.name in NOT_PLAYED) return GameStatus.Unknown
            return when (type.state) {
                "pre" -> GameStatus.Scheduled
                "in" -> GameStatus.Live(type.shortDetail ?: type.detail ?: "")
                "post" -> GameStatus.Final
                else -> GameStatus.Unknown
            }
        }

        /** "post" states that are not a finished game. */
        private val NOT_PLAYED = setOf("STATUS_POSTPONED", "STATUS_CANCELED", "STATUS_SUSPENDED")
    }
}

// Wire DTOs: only the fields we use. There is intentionally no score/linescore property.
@Serializable
internal data class EspnScoreboard(val events: List<EspnEvent> = emptyList())

@Serializable
internal data class EspnEvent(
    val id: String = "",
    val date: String? = null,
    val status: EspnStatus? = null,
    val competitions: List<EspnCompetition> = emptyList(),
)

@Serializable
internal data class EspnCompetition(
    val competitors: List<EspnCompetitor> = emptyList(),
    val status: EspnStatus? = null,
)

@Serializable
internal data class EspnCompetitor(val homeAway: String? = null, val team: EspnTeam? = null)

@Serializable
internal data class EspnTeam(
    val displayName: String? = null,
    val shortDisplayName: String? = null,
    val location: String? = null,
)

@Serializable
internal data class EspnStatus(val type: EspnStatusType? = null)

@Serializable
internal data class EspnStatusType(
    val name: String? = null,
    val state: String? = null,
    val detail: String? = null,
    val shortDetail: String? = null,
)
