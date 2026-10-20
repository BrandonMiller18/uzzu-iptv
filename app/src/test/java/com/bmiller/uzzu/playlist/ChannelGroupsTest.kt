package com.bmiller.uzzu.playlist

import com.bmiller.uzzu.playlist.ChannelGroup.COLLEGE_SPORTS
import com.bmiller.uzzu.playlist.ChannelGroup.INTERNATIONAL
import com.bmiller.uzzu.playlist.ChannelGroup.LEAGUE_NETWORKS
import com.bmiller.uzzu.playlist.ChannelGroup.NATIONAL_SPORTS
import com.bmiller.uzzu.playlist.ChannelGroup.NEWS_AND_ENTERTAINMENT
import com.bmiller.uzzu.playlist.ChannelGroup.OTHER
import com.bmiller.uzzu.playlist.ChannelGroup.REGIONAL_SPORTS
import com.bmiller.uzzu.schedule.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelGroupsTest {
    private val grouper = Fixtures.channelGrouper

    @Test
    fun `every fixture channel lands in the expected sub-group`() {
        val channels = Fixtures.playlist().channels
        assertEquals(96, channels.size)
        val actual = channels.groupBy({ it.subGroup }, { it.name })
        println(ChannelGroup.entries.joinToString("\n") { g -> "${g.label} (${actual[g].orEmpty().size}): ${actual[g].orEmpty().joinToString()}" })
        val expected = mapOf(
            LEAGUE_NETWORKS to listOf("NFL Network", "Red Zone", "Red Zone2", "NBA TV", "NHL Network", "MLB Network"),
            COLLEGE_SPORTS to listOf("PAC 12 Network", "BTN Network", "ACCN", "SEC Network"),
            REGIONAL_SPORTS to listOf(
                "Marquee Network", "Root Sports NW",
                "FanDuel Sports Network Detroit", "FanDuel Sports Network Florida", "FanDuel Sports Network Great Lakes",
                "FanDuel Sports Network Indiana", "FanDuel Sports Network Kansas City", "FanDuel Sports Network Midwest",
                "FanDuel Sports Network New Orleans", "FanDuel Sports Network North", "FanDuel Sports Network North +",
                "FanDuel Sports Network Ohio", "FanDuel Sports Network Oklahoma", "FanDuel Sports Network San Diego",
                "FanDuel Sports Network SoCal", "FanDuel Sports Network South", "FanDuel Sports Network Southeast",
                "FanDuel Sports Network Sun", "FanDuel Sports Network West", "FanDuel Sports Network Wisconsin",
                "FanDuel Sports Network Wisconsin +", "FanDuel Sports Network Wisconsin Extra",
                "FanDuel Sports Network South +", "FanDuel Sports Network Southwest",
                "Yes Network", "MASN", "NESN", "NESN2", "SportsNet Lakers", "SportsNet LA Dodgers", "Space City Home",
                "AT&T Pittsburgh", "Altitude Sports", "MSG", "MSG+",
                "NBC Sports Bay Area", "NBC Sports Bay Area+", "NBC Sports Boston", "NBC Sports California",
                "NBC Sports California+", "NBC Sports Chicago", "NBC Sports Philadelphia", "NBC Sports Philadelphia+",
                "Monumental Sports", "NBC SNY",
            ),
            NATIONAL_SPORTS to listOf(
                "TVG Network", "USA Network", "ESPN", "ESPN 2", "ESPNU", "Fox Sports 1", "Fox Sports 2",
                "Tennis Channel", "CBS", "TruTV East", "Golf Channel", "beIN USA", "TBS",
            ),
            INTERNATIONAL to listOf(
                "TSN 1", "TSN 2", "TSN 3", "TSN 4", "TSN 5", "Star Sports Hindi", "Star Sports 2", "Star Sports 1",
                "Willow Cricket", "Sky Sports Cricket", "SuperSport", "Astro Cricket", "Sky Sports Golf", "Premier Sports",
            ),
            NEWS_AND_ENTERTAINMENT to listOf(
                "Weather Channel", "Fox News", "Fox Business", "CNN News", "CNBC", "MSNBC", "Animal Planet East",
                "Discovery Science", "History Channel", "Discovery Channel", "Nickelodeon", "Cartoon Network",
                "A&E NETWORK", "HGTV",
            ),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `order-sensitive names`() {
        assertEquals(NATIONAL_SPORTS, grouper.groupOf("ESPNU"))
        assertEquals(NATIONAL_SPORTS, grouper.groupOf("ESPNews"))
        assertEquals(NATIONAL_SPORTS, grouper.groupOf("fox sports 1"))
        assertEquals(NEWS_AND_ENTERTAINMENT, grouper.groupOf("FOX NEWS CHANNEL"))
        assertEquals(NATIONAL_SPORTS, grouper.groupOf("NBC"))
        assertEquals(REGIONAL_SPORTS, grouper.groupOf("NBC Sports Washington"))
        assertEquals(REGIONAL_SPORTS, grouper.groupOf("NBC SNY"))
        assertEquals(INTERNATIONAL, grouper.groupOf("Sky Sports Golf")) // not Golf Channel
        assertEquals(NATIONAL_SPORTS, grouper.groupOf("  golf channel "))
        assertEquals(NEWS_AND_ENTERTAINMENT, grouper.groupOf("MSNBC")) // not MSG
        assertEquals(OTHER, grouper.groupOf("Hallmark Movies"))
    }
}
