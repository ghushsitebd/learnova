package com.learnova.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimalIslamicExamDirectorTest {

    @Test
    fun allFiveHundredLevelsHaveValidIslamicExamChallenges() {
        assertTrue(AnimalIslamicExamDirector.validateAllLevels())
        for (level in 1..500) {
            val challenge = AnimalIslamicExamDirector.challengeForLevel(level)
            assertEquals(level, challenge.level)
            assertTrue(challenge.correctAnswer in challenge.choices)
            assertEquals(3, challenge.choices.distinct().size)
        }
    }

    @Test
    fun animalsAreExamPartnersAndQuestionsAreIslamic() {
        val allowedTopics = setOf(
            "Quran", "Surah Al-Fatiha", "Bismillah", "Alhamdulillah", "Salam",
            "Wudu", "Salah", "Masjid", "Truthfulness", "Parents", "Cleanliness",
            "Sadaqah", "Dhikr", "Astaghfirullah", "Tawheed", "Ramadan",
            "Zakah", "Halal", "Prophet Muhammad ﷺ", "Dua"
        )
        for (level in 1..500) {
            val challenge = AnimalIslamicExamDirector.challengeForLevel(level)
            assertTrue("Unexpected non-Islamic topic at level $level: ${challenge.topic}", challenge.topic in allowedTopics)
            assertTrue(challenge.animal.isNotBlank())
            assertTrue(challenge.successMessage.contains(challenge.animal))
        }
    }

    @Test
    fun outOfRangeLevelsAreSafelyClamped() {
        assertEquals(1, AnimalIslamicExamDirector.challengeForLevel(0).level)
        assertEquals(500, AnimalIslamicExamDirector.challengeForLevel(501).level)
    }
}
