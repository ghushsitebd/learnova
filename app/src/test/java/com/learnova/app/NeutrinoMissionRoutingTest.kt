package com.learnova.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NeutrinoMissionRoutingTest {
    @Test fun scienceMissionAppearsOnlyAtFiftyLevelMilestones() {
        for (level in 1..500) {
            val mission = LevelContentDirector.scienceMissionForLevel(level)
            if (level % 50 == 0) assertNotNull("expected science mission at level $level", mission)
            else assertNull("unexpected science mission at level $level", mission)
        }
        assertNull(LevelContentDirector.scienceMissionForLevel(0))
        assertNull(LevelContentDirector.scienceMissionForLevel(501))
    }

    @Test fun milestoneMissionSupportsAllThreeLanguages() {
        assertEquals(
            "Neutrino Explorer",
            LevelContentDirector.scienceMissionForLevel(50, NeutrinoLearningModule.Language.ENGLISH)?.title
        )
        assertEquals(
            "নিউট্রিনো অভিযাত্রী",
            LevelContentDirector.scienceMissionForLevel(100, NeutrinoLearningModule.Language.BANGLA)?.title
        )
        assertEquals(
            "مستكشف النيوترينو",
            LevelContentDirector.scienceMissionForLevel(500, NeutrinoLearningModule.Language.ARABIC)?.title
        )
    }
}
