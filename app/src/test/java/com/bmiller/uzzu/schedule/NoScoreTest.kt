package com.bmiller.uzzu.schedule

import com.bmiller.uzzu.playlist.Game
import com.bmiller.uzzu.playlist.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** No score anywhere: the models must not even be able to carry one. */
class NoScoreTest {
    @Test
    fun `no model or wire class has a score field`() {
        val classes = listOf(
            Game::class.java, GameStatus.Live::class.java, ScheduleEvent::class.java, ScheduleTeam::class.java,
            EspnScoreboard::class.java, EspnEvent::class.java, EspnCompetition::class.java,
            EspnCompetitor::class.java, EspnTeam::class.java, EspnStatus::class.java, EspnStatusType::class.java,
        )
        val offenders = classes.flatMap { c ->
            (c.declaredFields.map { it.name } + c.declaredMethods.map { it.name })
                .filter { it.contains("score", ignoreCase = true) && !it.contains("scoreboard", ignoreCase = true) }
                .map { "${c.simpleName}.$it" }
        }
        assertEquals(emptyList<String>(), offenders)
    }
}
