package com.bmiller.uzzu.schedule

import com.bmiller.uzzu.playlist.League
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeagueClassifierTest {
    private val classifier = LeagueClassifier(Fixtures.teams)

    @Test
    fun `dictionary covers all four leagues`() {
        val counts = Fixtures.teams.teams.groupingBy { it.league }.eachCount()
        assertEquals(mapOf(League.NFL to 32, League.MLB to 30, League.NHL to 32, League.NBA to 30), counts)
    }

    @Test
    fun `every fixture game lands in the correct league`() {
        val leagues = Fixtures.playlist().games.associate { it.title to it.league }
        assertEquals(
            mapOf(
                "Anaheim Ducks @ New York Rangers" to League.NHL,
                "Colorado Avalanche @ Philadelphia Flyers" to League.NHL,
                "San Jose Sharks @ Toronto Maple Leafs" to League.NHL,
                "Cleveland Guardians @ Tampa Bay Rays" to League.MLB,
                "Washington Commanders @ San Francisco 49ers" to League.NFL,
                "Pittsburgh Penguins @ Utah Mammoth" to League.NHL,
            ),
            leagues,
        )
    }

    @Test
    fun `quirks and aliases resolve`() {
        assertEquals(League.NHL, classifier.classify("St.Louis Blues", "Utah Hockey Club"))
        assertEquals(League.NHL, classifier.classify("Montréal Canadiens", "Vegas Golden Knights"))
        assertEquals(League.MLB, classifier.classify("Athletics", "St. Louis Cardinals"))
        assertEquals(League.MLB, classifier.classify("Oakland Athletics", "LA Dodgers"))
        assertEquals(League.NBA, classifier.classify("Los Angeles Clippers", "LA Clippers"))
        assertEquals(League.NBA, classifier.classify("LA Clippers", "Golden State Warriors"))
    }

    @Test
    fun `nicknames alone never classify`() {
        assertEquals(League.UNGROUPED, classifier.classify("Rangers", "Kings"))
        assertEquals(League.UNGROUPED, classifier.classify("Giants", "Cardinals"))
        assertEquals(League.UNGROUPED, classifier.classify("Panthers", "Jets"))
        assertTrue(Fixtures.teams.lookup("Giants").isEmpty())
    }

    @Test
    fun `teams from different leagues stay ungrouped`() {
        assertEquals(League.UNGROUPED, classifier.classify("New York Giants", "San Francisco Giants"))
        assertEquals(League.UNGROUPED, classifier.classify("Florida Panthers", "Carolina Panthers"))
        assertEquals(League.UNGROUPED, classifier.classify("Middle Tennessee Blue Raiders", "Florida International Panthers"))
        assertEquals(League.UNGROUPED, classifier.classify("", "Florida Panthers"))
    }
}
