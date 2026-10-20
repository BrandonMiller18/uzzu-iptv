package com.bmiller.uzzu.player

import com.bmiller.uzzu.playlist.FeedSide

enum class Section { GAMES, CHANNELS }

/**
 * Where a tune came from (PLAN "Navigation rules"). [group] is the tab the user played from (e.g.
 * "All", "NHL", "Regional Sports"); [itemKeys] is that tab's ordered items (Game.id / Channel.id),
 * so CH up/down (M4) can cycle within it and re-resolve items against a refreshed playlist.
 * [side] is the feed side playing for a game (null for channels); [chosenSide] is the side the user
 * last chose (picker / H/A toggle), which CH up/down keeps even after falling back on a single-feed game.
 */
data class PlayContext(
    val section: Section,
    val group: String,
    val itemKeys: List<String>,
    val currentKey: String,
    val side: FeedSide? = null,
    val chosenSide: FeedSide? = side,
)
