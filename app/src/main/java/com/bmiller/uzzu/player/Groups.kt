package com.bmiller.uzzu.player

import com.bmiller.uzzu.playlist.FeedSide
import com.bmiller.uzzu.playlist.Game
import com.bmiller.uzzu.playlist.League
import com.bmiller.uzzu.playlist.Playlist
import com.bmiller.uzzu.ui.channels.channelTabs
import com.bmiller.uzzu.ui.channels.channelsInTab
import com.bmiller.uzzu.ui.channels.orderedChannels
import com.bmiller.uzzu.ui.games.gameTabs
import com.bmiller.uzzu.ui.games.gamesInTab
import com.bmiller.uzzu.ui.label

/** What the player shows for a resolved item (banner, S6, logs). */
data class Resolved(
    val title: String,
    val url: String,
    /** Feed side actually used (may differ from the requested one after the fallback); null for channels. */
    val side: FeedSide?,
    /** `// TUNED · HOME FEED · NFL` */
    val bannerLine: String,
    /** Games: away, home. Channels: one. */
    val logos: List<String?>,
    val isChannel: Boolean,
    /** `Ducks @ Rangers · Home feed`, for S6 and logs. */
    val label: String,
)

/** Tabs of a section (zero/empty tabs already hidden). */
fun sectionTabs(playlist: Playlist, section: Section): List<String> = when (section) {
    Section.GAMES -> gameTabs(playlist.games)
    Section.CHANNELS -> channelTabs(playlist.channels)
}

/** Item keys of one tab, in display order (Game.id / Channel.key). */
fun groupKeys(playlist: Playlist, section: Section, group: String): List<String> = when (section) {
    Section.GAMES -> gamesInTab(playlist.games, group).map { it.id }
    Section.CHANNELS -> channelsInTab(orderedChannels(playlist.channels), group).map { it.key }
}

fun Game.hasSide(side: FeedSide) = feed(side) != null

/** [preferred] if the game has it, else the other available side (PLAN: CH up/down feed fallback). */
fun Game.sideOrFallback(preferred: FeedSide?): FeedSide? = when {
    preferred != null && hasSide(preferred) -> preferred
    homeFeed != null -> FeedSide.HOME
    awayFeed != null -> FeedSide.AWAY
    else -> null
}

/**
 * Resolves [ctx] (key + side) against [playlist] at tune time. With [strictSide] the requested side
 * must exist (refresh retry: "resolve the same game/side"); otherwise it falls back to the other feed.
 */
fun resolve(playlist: Playlist, ctx: PlayContext, strictSide: Boolean = false): Resolved? = when (ctx.section) {
    Section.GAMES -> {
        val game = playlist.games.firstOrNull { it.id == ctx.currentKey }
        val side = if (strictSide) ctx.side?.takeIf { game?.hasSide(it) == true } else game?.sideOrFallback(ctx.side)
        val feed = side?.let { game?.feed(it) }
        if (game == null || feed == null) {
            null
        } else {
            val sideText = if (side == FeedSide.HOME) "HOME FEED" else "AWAY FEED"
            val raw = game.rawTitle != null
            Resolved(
                title = game.title,
                url = feed.url,
                side = side,
                bannerLine = if (raw) "// TUNED · ${League.UNGROUPED.label.uppercase()}" else "// TUNED · $sideText · ${game.league.label.uppercase()}",
                logos = if (raw) listOf(feed.logoUrl) else listOf(game.awayLogoUrl, game.homeLogoUrl),
                isChannel = false,
                label = if (raw) game.title else "${game.title} · ${sideText.lowercase().replaceFirstChar { it.uppercase() }}",
            )
        }
    }
    Section.CHANNELS -> playlist.channels.firstOrNull { it.key == ctx.currentKey }?.let { ch ->
        val index = ctx.itemKeys.indexOf(ch.key).takeIf { it >= 0 }?.let { "CH %02d · ".format(it + 1) }.orEmpty()
        Resolved(
            title = ch.name,
            url = ch.url,
            side = null,
            bannerLine = "// TUNED · $index${ch.subGroup.label.uppercase()}",
            logos = listOf(ch.logoUrl),
            isChannel = true,
            label = ch.name,
        )
    }
}

/**
 * CH up/down: the previous ([delta] < 0) or next item in the current group, wrapping. The group is
 * re-read from [playlist] so a refresh is respected; if the current item left the group, its old
 * position in the snapshot is used. Games keep the last chosen side, falling back to the other one.
 */
fun stepTarget(playlist: Playlist, ctx: PlayContext, delta: Int): PlayContext? {
    val keys = groupKeys(playlist, ctx.section, ctx.group).ifEmpty { return null }
    val idx = keys.indexOf(ctx.currentKey)
    val next = if (idx >= 0) {
        Math.floorMod(idx + delta, keys.size)
    } else {
        val base = ctx.itemKeys.indexOf(ctx.currentKey).coerceIn(0, keys.size - 1)
        Math.floorMod(if (delta > 0) base else base - 1, keys.size)
    }
    val key = keys[next]
    val side = if (ctx.section == Section.GAMES) playlist.games.firstOrNull { it.id == key }?.sideOrFallback(ctx.chosenSide ?: ctx.side) else null
    return ctx.copy(itemKeys = keys, currentKey = key, side = side)
}
