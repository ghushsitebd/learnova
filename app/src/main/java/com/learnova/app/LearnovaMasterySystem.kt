package com.learnova.app

import android.content.SharedPreferences

class LearnovaMasterySystem(private val prefs: SharedPreferences) {
    private fun key(index: Int) = "mastery_$index"
    private fun attemptsKey(index: Int) = "mastery_attempts_$index"
    private fun wrongKey(index: Int) = "mastery_wrong_$index"
    private fun lastSeenKey(index: Int) = "mastery_last_seen_$index"

    fun mastery(index: Int): Int = prefs.getInt(key(index), 0).coerceIn(0, 5)

    fun recordCorrect(index: Int, completedCount: Int) {
        prefs.edit()
            .putInt(key(index), (mastery(index) + 1).coerceAtMost(5))
            .putInt(attemptsKey(index), prefs.getInt(attemptsKey(index), 0) + 1)
            .putLong(lastSeenKey(index), completedCount.toLong())
            .apply()
    }

    fun recordWrong(index: Int) {
        prefs.edit()
            .putInt(key(index), (mastery(index) - 1).coerceAtLeast(0))
            .putInt(attemptsKey(index), prefs.getInt(attemptsKey(index), 0) + 1)
            .putInt(wrongKey(index), prefs.getInt(wrongKey(index), 0) + 1)
            .apply()
    }

    fun shouldReview(completedCount: Int): Boolean =
        completedCount > 0 && completedCount % 5 == 0

    fun nextLesson(currentIndex: Int, totalLessons: Int, completedCount: Int): Int {
        if (totalLessons <= 1) return 0

        if (shouldReview(completedCount)) {
            var bestIndex = -1
            var bestScore = Int.MAX_VALUE
            for (i in 0 until totalLessons) {
                if (i == currentIndex) continue
                val m = mastery(i)
                val wrong = prefs.getInt(wrongKey(i), 0)
                val lastSeen = prefs.getLong(lastSeenKey(i), Long.MAX_VALUE / 4)
                val score = m * 1000000 - wrong * 1000 + lastSeen.coerceIn(0L, 999999L).toInt()
                if (score < bestScore) {
                    bestScore = score
                    bestIndex = i
                }
            }
            if (bestIndex >= 0) return bestIndex
        }

        return (currentIndex + 1) % totalLessons
    }

    fun reviewLabel(index: Int): String =
        if (mastery(index) <= 1) "Let's learn this again" else "Quick review"
}
