package com.bmiller.uzzu.schedule

import com.bmiller.uzzu.playlist.GameStatus
import com.bmiller.uzzu.playlist.League
import java.time.Instant
import java.time.LocalDate

/**
 * Names of one side of a scheduled event. By design nothing here (or in [ScheduleEvent]) carries a
 * score: the app never shows scores, so it never reads them.
 */
data class ScheduleTeam(
    val displayName: String,
    val shortDisplayName: String = "",
    val location: String = "",
)

data class ScheduleEvent(
    val id: String,
    val league: League,
    val startTime: Instant,
    val status: GameStatus,
    val away: ScheduleTeam,
    val home: ScheduleTeam,
)

/**
 * A per-league schedule provider (ESPN today; MLB Stats API / NHL API could be swapped in per
 * league). [fetch] throws on any failure; the caller keeps the last known values.
 */
interface ScheduleSource {
    val leagues: List<League>
    suspend fun fetch(league: League, date: LocalDate): List<ScheduleEvent>
}
