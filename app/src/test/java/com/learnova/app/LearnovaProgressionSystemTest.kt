package com.learnova.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Boundary and malformed-input regression coverage for child learning progression. */
class LearnovaProgressionSystemTest {
    private val progression = LearnovaProgressionSystem()

    @Test
    fun stageBoundariesAdvanceOnlyAfterBothLessonAndMasteryThresholds() {
        assertEquals(
            LearnovaProgressionSystem.Stage.FOUNDATION,
            progression.stage(500, 4.0f, 11)
        )
        assertEquals(
            LearnovaProgressionSystem.Stage.FOUNDATION,
            progression.stage(500, 1.24f, 12)
        )
        assertEquals(
            LearnovaProgressionSystem.Stage.BUILDING,
            progression.stage(500, 1.25f, 12)
        )
        assertEquals(
            LearnovaProgressionSystem.Stage.BUILDING,
            progression.stage(500, 3.0f, 39)
        )
        assertEquals(
            LearnovaProgressionSystem.Stage.THINKING,
            progression.stage(500, 2.5f, 40)
        )
        assertEquals(
            LearnovaProgressionSystem.Stage.THINKING,
            progression.stage(500, 4.5f, 99)
        )
        assertEquals(
            LearnovaProgressionSystem.Stage.CONFIDENT,
            progression.stage(500, 3.75f, 100)
        )
    }

    @Test
    fun masteryAverageIgnoresUnseenLessonsAndClampsInvalidScores() {
        val scores = listOf(0, 1, 2, 3, 0, 9, -4)
        val average = progression.masteryAverage(scores.size) { scores[it] }

        // Positive observations become [1, 2, 3, 5]; zero/negative scores are unseen after clamping.
        assertEquals(2.75f, average, 0.0001f)
    }

    @Test
    fun masteryAverageIsZeroWhenNoLessonHasBeenObserved() {
        assertEquals(0.0f, progression.masteryAverage(0) { 5 }, 0.0f)
        assertEquals(0.0f, progression.masteryAverage(4) { 0 }, 0.0f)
    }

    @Test
    fun difficultyHintMatchesTheCurrentLearningStage() {
        assertEquals("Touch • Listen • Learn", progression.difficultyHint(500, { 1 }, 5))
        assertEquals("Touch • Recall • Repeat", progression.difficultyHint(500, { 2 }, 20))
        assertEquals("Choose • Connect • Think", progression.difficultyHint(500, { 3 }, 60))
        assertEquals("Recall • Solve • Explore", progression.difficultyHint(500, { 4 }, 120))
    }
}
