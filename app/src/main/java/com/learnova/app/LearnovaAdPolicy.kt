package com.learnova.app

/**
 * Learnova monetization rules.
 *
 * The game is designed for children, so advertising must remain child-safe,
 * non-intrusive and separate from core learning controls.
 *
 * This file intentionally contains no ad-network SDK. The production ad
 * provider/app ID can be connected later without changing gameplay.
 */
object LearnovaAdPolicy {
    const val MAX_ADS_PER_24_HOURS = 2
    const val MIN_SECONDS_BETWEEN_ADS = 60L * 60L * 6L
    const val SHOW_ONLY_AFTER_LESSON = true
    const val ALLOW_DURING_GAMEPLAY = false
    const val MAX_CONTENT_RATING = "G"
    const val CHILD_DIRECTED = true
    const val PERSONALIZED_ADS = false

    /**
     * Frequency gate for the local controller.
     * Ad SDK integration can use this before requesting an ad.
     */
    fun canShow(nowMillis: Long, history: List<Long>): Boolean {
        val recent = history.filter { nowMillis - it < 24L * 60L * 60L * 1000L }
        if (recent.size >= MAX_ADS_PER_24_HOURS) return false
        val last = recent.maxOrNull() ?: return true
        return nowMillis - last >= MIN_SECONDS_BETWEEN_ADS * 1000L
    }
}
