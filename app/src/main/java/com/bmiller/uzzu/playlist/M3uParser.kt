package com.bmiller.uzzu.playlist

/**
 * Parses the uzzu #EXTM3U playlist into [Playlist]. Never throws on bad input: malformed entries
 * are skipped and reported through [log]. Only `Live-Games` and `Live-Channels` are used;
 * `*-Replays` groups are ignored quietly, any other group is ignored and logged.
 */
class M3uParser(private val log: (String) -> Unit = {}) {

    private data class Entry(
        val index: Int,
        val tvgId: String?,
        val logo: String?,
        val group: String?,
        val name: String,
        val url: String,
    )

    fun parse(text: String): Playlist {
        val entries = mutableListOf<Entry>()
        var malformed = 0
        var pending: Pair<Int, String>? = null // line number, #EXTINF line

        fun malformed(lineNo: Int, reason: String) {
            malformed++
            log("playlist: skipped malformed entry at line $lineNo: $reason")
        }

        val lines = text.removePrefix("\uFEFF").split(lineBreak)
        lines.forEachIndexed { i, raw ->
            val lineNo = i + 1
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending?.let { malformed(it.first, "#EXTINF without URL") }
                    pending = lineNo to line
                }
                line.startsWith("#") -> Unit // #EXTM3U and other directives/comments
                else -> {
                    val p = pending
                    pending = null
                    if (p == null) {
                        malformed(lineNo, "URL without #EXTINF")
                    } else {
                        val entry = parseEntry(entries.size + malformed, p.second, line)
                        if (entry == null) malformed(p.first, "unparseable #EXTINF or URL") else entries += entry
                    }
                }
            }
        }
        pending?.let { malformed(it.first, "#EXTINF without URL at end of file") }

        var duplicates = 0
        var replays = 0
        var unknown = 0
        val seen = HashSet<Triple<String?, String, String>>()
        val gameEntries = mutableListOf<Entry>()
        val channels = mutableListOf<Channel>()
        val unknownGroups = linkedSetOf<String>()

        for (e in entries) {
            if (!seen.add(Triple(e.group, e.name, e.url))) {
                duplicates++
                log("playlist: duplicate entry ignored: ${e.name}")
                continue
            }
            when {
                e.group.equals(GROUP_GAMES, ignoreCase = true) -> gameEntries += e
                e.group.equals(GROUP_CHANNELS, ignoreCase = true) -> channels += Channel(
                    id = e.tvgId?.takeIf { it.isNotBlank() } ?: e.name,
                    name = e.name,
                    logoUrl = e.logo,
                    url = e.url,
                    subGroup = ChannelGroup.OTHER,
                    playlistIndex = e.index,
                )
                e.group != null && e.group.endsWith("-Replays", ignoreCase = true) -> replays++
                else -> {
                    unknown++
                    unknownGroups += e.group ?: "<none>"
                }
            }
        }
        unknownGroups.forEach { log("playlist: ignored unknown group-title \"$it\"") }

        return Playlist(
            games = buildGames(gameEntries),
            channels = withUniqueKeys(channels),
            stats = ParseStats(entries.size + malformed, malformed, duplicates, replays, unknown),
        )
    }

    private fun withUniqueKeys(channels: List<Channel>): List<Channel> {
        val seen = HashMap<String, Int>()
        return channels.map { ch ->
            val n = seen.merge(ch.id, 1, Int::plus)!!
            if (n == 1) ch else ch.copy(key = "${ch.id}#$n")
        }
    }

    private fun parseEntry(index: Int, extinf: String, urlLine: String): Entry? {
        if (!urlLine.startsWith("http://", true) && !urlLine.startsWith("https://", true)) return null
        val comma = lastTopLevelComma(extinf)
        val attrs = attribute.findAll(if (comma >= 0) extinf.substring(0, comma) else extinf)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val name = (if (comma >= 0) extinf.substring(comma + 1).trim() else "")
            .ifBlank { attrs["tvg-id"]?.trim().orEmpty() }
        if (name.isEmpty()) return null
        return Entry(
            index = index,
            tvgId = attrs["tvg-id"],
            logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
            group = attrs["group-title"]?.trim()?.takeIf { it.isNotEmpty() },
            name = name,
            url = urlLine,
        )
    }

    /** Index of the last comma that is not inside a quoted attribute value, or -1. */
    private fun lastTopLevelComma(s: String): Int {
        var inQuotes = false
        var last = -1
        s.forEachIndexed { i, c ->
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> last = i
            }
        }
        return last
    }

    private fun buildGames(entries: List<Entry>): List<Game> {
        val byKey = LinkedHashMap<String, Game>()
        for (e in entries) {
            val m = gamePattern.matchEntire(e.name)
            if (m == null) {
                val key = "raw|" + normalizeName(e.name)
                if (key in byKey) {
                    log("playlist: second unpaired feed for \"${e.name}\" ignored")
                    continue
                }
                byKey[key] = Game(
                    id = key, awayName = "", homeName = "", league = League.UNGROUPED,
                    homeFeed = Feed(e.url, e.logo, FeedSide.HOME), awayFeed = null,
                    startTime = null, status = GameStatus.Unknown, playlistIndex = e.index,
                    rawTitle = e.name,
                )
                continue
            }
            val (away, home, sideTag) = m.destructured
            val side = if (sideTag == "H") FeedSide.HOME else FeedSide.AWAY
            val feed = Feed(e.url, e.logo, side)
            val key = normalizeName(away) + "|" + normalizeName(home)
            val existing = byKey[key]
            if (existing == null) {
                byKey[key] = Game(
                    id = key, awayName = away.trim(), homeName = home.trim(), league = League.UNGROUPED,
                    homeFeed = feed.takeIf { side == FeedSide.HOME },
                    awayFeed = feed.takeIf { side == FeedSide.AWAY },
                    startTime = null, status = GameStatus.Unknown, playlistIndex = e.index,
                )
            } else if (existing.feed(side) != null) {
                log("playlist: duplicate ${side.name.lowercase()} feed for \"${existing.title}\" ignored")
            } else {
                byKey[key] = if (side == FeedSide.HOME) existing.copy(homeFeed = feed) else existing.copy(awayFeed = feed)
            }
        }
        return byKey.values.toList()
    }

    companion object {
        const val GROUP_GAMES = "Live-Games"
        const val GROUP_CHANNELS = "Live-Channels"
        private val lineBreak = Regex("\r\n|\r|\n")
        private val attribute = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
        val gamePattern = Regex("^(.+?) @ (.+?)-(H|A)$")
    }
}
