package com.bmiller.uzzu.playlist

import java.time.Instant

enum class League { MLB, NHL, NBA, NFL, NCAAF, UNGROUPED }

enum class FeedSide { HOME, AWAY }

/** [logoUrl] is the logo of the team whose broadcast this is (tvg-logo of the feed entry). */
data class Feed(val url: String, val logoUrl: String?, val side: FeedSide)

sealed interface GameStatus {
    data object Unknown : GameStatus
    data object Scheduled : GameStatus
    data class Live(val periodLabel: String) : GameStatus
    data object Final : GameStatus
}

data class Game(
    /** Normalized `away|home` key; for regex misses `raw|<normalized title>`. */
    val id: String,
    val awayName: String,
    val homeName: String,
    val league: League,
    val homeFeed: Feed?,
    val awayFeed: Feed?,
    val startTime: Instant?,
    val status: GameStatus,
    val playlistIndex: Int,
    /** Set only for entries that failed the `{Away} @ {Home}-{H|A}` pattern. */
    val rawTitle: String? = null,
) {
    val title: String get() = rawTitle ?: "$awayName @ $homeName"
    val homeLogoUrl: String? get() = homeFeed?.logoUrl
    val awayLogoUrl: String? get() = awayFeed?.logoUrl
    fun feed(side: FeedSide): Feed? = if (side == FeedSide.HOME) homeFeed else awayFeed
}

/** Live-Channels sub-groups, assigned by assets/channel_groups.json ([ChannelGrouper]). Declaration order = display order. */
enum class ChannelGroup(val label: String) {
    LEAGUE_NETWORKS("League Networks"),
    NATIONAL_SPORTS("National Sports"),
    COLLEGE_SPORTS("College Sports"),
    REGIONAL_SPORTS("Regional Sports"),
    INTERNATIONAL("International"),
    NEWS_AND_ENTERTAINMENT("News and Entertainment"),
    OTHER("Other"),
}

data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val url: String,
    val subGroup: ChannelGroup,
    val playlistIndex: Int,
)

data class Playlist(
    val games: List<Game>,
    val channels: List<Channel>,
    val stats: ParseStats,
)

data class ParseStats(
    val entries: Int,
    val malformed: Int,
    val duplicates: Int,
    val ignoredReplays: Int,
    val ignoredUnknownGroup: Int,
)
