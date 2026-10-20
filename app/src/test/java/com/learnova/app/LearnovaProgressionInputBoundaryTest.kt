package com.learnova.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression coverage for untrusted counters and scores at the learning progression boundary. */
class LearnovaProgressionInputBoundaryTest {
    private val progression = LearnovaProgressionSystem()

    @Test
    fun masteryScoresAreClampedAndOnlyObservedScoresContribute() {
        val scores = listOf(-100, 0, 1, 2, 3, 5, 6, 100)
        val average = progression.masteryAverage(scores.size) { scores[it] }
        // Clamped positive observations: 1, 2, 3, 5, 5, 5.
        assertEquals(3.5f, average, 0.0001f)
    }

    @Test
    fun noObservedMasteryNeverProducesNanOrInfinity() {
        assertEquals(0.0f, progression.masteryAverage(-1) { Int.MAX_VALUE }, 0.0f)
        assertEquals(0.0f, progression.masteryAverage(0) { Int.MAX_VALUE }, 0.0f)
        assertEquals(0.0f, progression.masteryAverage(5) { 0 }, 0.0f)
    }

    @Test
    fun masteryAverageStaysWithinValidScoreRange() {
        val scores = listOf(Int.MIN_VALUE, -1, 1, 2, 3, 4, 5, 6, Int.MAX_VALUE)
        val average = progression.masteryAverage(scores.size) { scores[it] }
        assertEquals(true, average.isFinite())
        assertEquals(true, average in 1.0f..5.0f)
    }

    @Test
    fun negativeCompletionCountDoesNotUnlockAdvancedStage() {
        assertEquals(
            LearnovaProgressionSystem.Stage.FOUNDATION,
            progression.stage(500, 5.0f, -1)
        )
    }

    @Test
    fun completionThresholdsRemainMonotonicAcrossStages() {
        assertEquals(LearnovaProgressionSystem.Stage.FOUNDATION, progression.stage(500, 0.0f, 11))
        assertEquals(LearnovaProgressionSystem.Stage.BUILDING, progression.stage(500, 1.25f, 12))
        assertEquals(LearnovaProgressionSystem.Stage.THINKING, progression.stage(500, 2.5f, 40))
        assertEquals(LearnovaProgressionSystem.Stage.CONFIDENT, progression.stage(500, 3.75f, 100))
    }
}
