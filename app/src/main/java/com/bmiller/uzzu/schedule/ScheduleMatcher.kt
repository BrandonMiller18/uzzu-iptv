package com.bmiller.uzzu.schedule

import com.bmiller.uzzu.playlist.Game
import com.bmiller.uzzu.playlist.GameStatus
import com.bmiller.uzzu.playlist.League
import com.bmiller.uzzu.playlist.normalizeName

/**
 * PLAN Step 3: matches playlist games to schedule events and sets startTime/status (and league
 * NCAAF for games the dictionary could not classify), then sorts.
 *
 * A side matches an event competitor when its normalized name equals the competitor's displayName,
 * shortDisplayName or location, or one of the dictionary full names/aliases of that team. Both
 * sides must match the same event (away to away, home to home). If they match events with
 * different matchups (ambiguous schools like Miami / USC), nothing is matched.
 */
class ScheduleMatcher(dictionary: Lazy<TeamDictionary>, private val log: (String) -> Unit = {}) {
    constructor(dictionary: TeamDictionary, log: (String) -> Unit = {}) : this(lazyOf(dictionary), log)

    private val dictionary by dictionary

    fun apply(games: List<Game>, events: Map<League, List<ScheduleEvent>>): List<Game> =
        sortGames(games.map { match(it, events) })

    private fun match(game: Game, events: Map<League, List<ScheduleEvent>>): Game {
        if (game.rawTitle != null) return game
        val target = if (game.league == League.UNGROUPED) League.NCAAF else game.league
        val candidates = events[target].orEmpty()
        if (candidates.isEmpty()) return game
        val away = normalizeName(game.awayName)
        val home = normalizeName(game.homeName)
        val hits = candidates
            .filter { sideMatches(away, it.away, target) && sideMatches(home, it.home, target) }
            .distinctBy { it.id }
        if (hits.isEmpty()) return game
        val matchups = hits.map { normalizeName(it.away.displayName) to normalizeName(it.home.displayName) }.toSet()
        if (matchups.size > 1) {
            log("schedule: ambiguous ${target.name} match for \"${game.title}\" (${matchups.size} matchups), left unmatched")
            return game
        }
        val event = pick(hits)
        return game.copy(league = target, startTime = event.startTime, status = event.status)
    }

    private fun sideMatches(name: String, team: ScheduleTeam, league: League): Boolean {
        if (name.isEmpty()) return false
        if (name == normalizeName(team.displayName) || name == normalizeName(team.shortDisplayName) ||
            name == normalizeName(team.location)
        ) {
            return true
        }
        return dictionary.entryFor(league, team.displayName)?.fullNames?.contains(name) == true
    }

    /**
     * Same matchup on both fetched days (series, doubleheaders): prefer the live event, then the
     * next scheduled one, then the most recent final.
     */
    private fun pick(hits: List<ScheduleEvent>): ScheduleEvent =
        hits.firstOrNull { it.status is GameStatus.Live }
            ?: hits.filter { it.status == GameStatus.Scheduled }.minByOrNull { it.startTime }
            ?: hits.maxBy { it.startTime }
}

/**
 * Sort order: start time, then playlist order. Status (Final) never moves a game. Games without a
 * time follow the timed ones; Ungrouped comes last, in playlist order.
 */
fun sortGames(games: List<Game>): List<Game> = games.sortedWith(
    compareBy<Game>({ it.league == League.UNGROUPED }, { it.startTime == null }, { it.startTime }, { it.playlistIndex }),
)
