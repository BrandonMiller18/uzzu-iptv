package com.bmiller.uzzu.ui

import com.bmiller.uzzu.playlist.Game
import com.bmiller.uzzu.playlist.GameStatus
import com.bmiller.uzzu.playlist.League
import com.bmiller.uzzu.ui.components.GameCardData
import com.bmiller.uzzu.ui.components.GameCardState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** Device-local short time ("7:15 PM", or "19:15" on 24 h locales), uppercase, plain spaces. */
fun formatClock(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    instant.atZone(zone).format(clockFormat).replace(' ', ' ').replace(' ', ' ').uppercase()

fun formatClock(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = formatClock(Instant.ofEpochMilli(millis), zone)

/** Start time, live period or FINAL. Never a score: [GameStatus] cannot carry one. */
fun Game.detailText(zone: ZoneId = ZoneId.systemDefault()): String = when (val s = status) {
    is GameStatus.Live -> s.periodLabel.uppercase()
    GameStatus.Final -> "FINAL"
    GameStatus.Scheduled, GameStatus.Unknown -> startTime?.let { formatClock(it, zone) }.orEmpty()
}

val League.label: String get() = if (this == League.UNGROUPED) "Ungrouped" else name

fun Game.toCardData(zone: ZoneId = ZoneId.systemDefault()): GameCardData {
    val state = when {
        league == League.UNGROUPED -> GameCardState.Ungrouped
        status is GameStatus.Live -> GameCardState.Live
        status == GameStatus.Final -> GameCardState.Final
        else -> GameCardState.Scheduled
    }
    return if (state == GameCardState.Ungrouped) {
        GameCardData(state, away = title, home = "", awayLogo = (homeFeed ?: awayFeed)?.logoUrl, homeLogo = null, league = "", detail = "")
    } else {
        GameCardData(state, awayName, homeName, awayLogoUrl, homeLogoUrl, league.name, detailText(zone))
    }
}
