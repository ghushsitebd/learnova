package com.learnova.app

/**
 * Child-safe engagement guardrails for Learnova.
 *
 * The game can be delightful and rewarding without coercive retention patterns.
 * This policy deliberately avoids dark patterns such as forced continuation,
 * guilt prompts, fake scarcity, manipulative streak loss, or autoplay loops.
 */
internal object ChildSafeEngagementPolicy {

    const val MAX_REWARD_CHAIN = 5
    const val BREAK_REMINDER_MINUTES = 20

    data class Reward(
        val points: Int,
        val celebration: Celebration
    )

    enum class Celebration {
        NONE,
        SPARKLE,
        CONFETTI
    }

    /**
     * Rewards learning progress, not time spent in the app.
     * The chain is capped so longer sessions do not create escalating pressure.
     */
    fun rewardForCorrectLesson(correctAnswers: Int): Reward {
        val safe = correctAnswers.coerceAtLeast(0)
        return when {
            safe > 0 && safe % MAX_REWARD_CHAIN == 0 ->
                Reward(points = 5, celebration = Celebration.CONFETTI)
            safe > 0 ->
                Reward(points = 1, celebration = Celebration.SPARKLE)
            else ->
                Reward(points = 0, celebration = Celebration.NONE)
        }
    }

    fun shouldSuggestBreak(sessionMinutes: Int): Boolean =
        sessionMinutes >= BREAK_REMINDER_MINUTES
}
