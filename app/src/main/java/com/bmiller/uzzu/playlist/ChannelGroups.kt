package com.bmiller.uzzu.playlist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Live-Channels sub-group rules from assets/channel_groups.json. Rules are tried in file order and
 * the first match wins; matching is case-insensitive against the trimmed display name. No match →
 * [ChannelGroup.OTHER].
 */
class ChannelGrouper(private val rules: List<Rule>) {

    @Serializable
    data class Rule(val group: ChannelGroup, val match: Match, val values: List<String>)

    @Serializable
    enum class Match {
        @SerialName("exact") EXACT,
        @SerialName("prefix") PREFIX,
        @SerialName("contains") CONTAINS,
    }

    @Serializable
    private data class RulesFile(val rules: List<Rule>)

    fun groupOf(name: String): ChannelGroup {
        val n = name.trim()
        return rules.firstOrNull { r ->
            r.values.any { v ->
                when (r.match) {
                    Match.EXACT -> n.equals(v, ignoreCase = true)
                    Match.PREFIX -> n.startsWith(v, ignoreCase = true)
                    Match.CONTAINS -> n.contains(v, ignoreCase = true)
                }
            }
        }?.group ?: ChannelGroup.OTHER
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String) = ChannelGrouper(json.decodeFromString<RulesFile>(text).rules)
    }
}
