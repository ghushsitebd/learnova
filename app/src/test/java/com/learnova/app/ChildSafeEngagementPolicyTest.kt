package com.learnova.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for non-coercive rewards and session-break reminders. */
class ChildSafeEngagementPolicyTest {

    @Test
    fun zeroOrNegativeCorrectAnswersNeverReward() {
        assertEquals(
            ChildSafeEngagementPolicy.Reward(0, ChildSafeEngagementPolicy.Celebration.NONE),
            ChildSafeEngagementPolicy.rewardForCorrectLesson(0)
        )
        assertEquals(
            ChildSafeEngagementPolicy.Reward(0, ChildSafeEngagementPolicy.Celebration.NONE),
            ChildSafeEngagementPolicy.rewardForCorrectLesson(-4)
        )
    }

    @Test
    fun ordinaryCorrectAnswerGivesSmallSparkleReward() {
        assertEquals(
            ChildSafeEngagementPolicy.Reward(1, ChildSafeEngagementPolicy.Celebration.SPARKLE),
            ChildSafeEngagementPolicy.rewardForCorrectLesson(1)
        )
        assertEquals(
            ChildSafeEngagementPolicy.Reward(1, ChildSafeEngagementPolicy.Celebration.SPARKLE),
            ChildSafeEngagementPolicy.rewardForCorrectLesson(4)
        )
    }

    @Test
    fun everyFifthCorrectAnswerUsesCappedCelebration() {
        for (answers in listOf(5, 10, 15, 1000)) {
            assertEquals(
                "answers=$answers",
                ChildSafeEngagementPolicy.Reward(5, ChildSafeEngagementPolicy.Celebration.CONFETTI),
                ChildSafeEngagementPolicy.rewardForCorrectLesson(answers)
            )
        }
    }

    @Test
    fun breakReminderBeginsAtTwentyMinutes() {
        assertFalse(ChildSafeEngagementPolicy.shouldSuggestBreak(-1))
        assertFalse(ChildSafeEngagementPolicy.shouldSuggestBreak(0))
        assertFalse(ChildSafeEngagementPolicy.shouldSuggestBreak(19))
        assertTrue(ChildSafeEngagementPolicy.shouldSuggestBreak(20))
        assertTrue(ChildSafeEngagementPolicy.shouldSuggestBreak(21))
    }
}
