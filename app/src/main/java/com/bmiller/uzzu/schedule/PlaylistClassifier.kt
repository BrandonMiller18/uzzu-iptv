package com.bmiller.uzzu.schedule

import com.bmiller.uzzu.playlist.ChannelGrouper
import com.bmiller.uzzu.playlist.Playlist

/** Offline enrichment applied right after parsing: dictionary league per game, sub-group per channel. */
class PlaylistClassifier(private val leagues: LeagueClassifier, private val groups: ChannelGrouper) {
    fun classify(p: Playlist): Playlist = p.copy(
        games = p.games.map { g -> if (g.rawTitle != null) g else g.copy(league = leagues.classify(g.awayName, g.homeName)) },
        channels = p.channels.map { it.copy(subGroup = groups.groupOf(it.name)) },
    )
}
