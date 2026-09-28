package com.learnova.app

import android.content.SharedPreferences

class LearnovaProgressionSystem(private val prefs: SharedPreferences) {
    enum class Stage(val title: String, val level: Int) {
        FOUNDATION("Foundation", 1),
        BUILDING("Building", 2),
        THINKING("Thinking", 3),
        CONFIDENT("Confident", 4)
    }

    fun stage(totalLessons: Int, masteryAverage: Float, completedLessons: Int): Stage {
        val avg = masteryAverage.coerceIn(0f, 5f)
        return when {
            completedLessons < 12 || avg < 1.25f -> Stage.FOUNDATION
            completedLessons < 40 || avg < 2.5f -> Stage.BUILDING
            completedLessons < 100 || avg < 3.75f -> Stage.THINKING
            else -> Stage.CONFIDENT
        }
    }

    fun masteryAverage(totalLessons: Int, masteryOf: (Int) -> Int): Float {
        if (totalLessons <= 0) return 0f
        var sum = 0
        for (i in 0 until totalLessons) sum += masteryOf(i).coerceIn(0, 5)
        return sum.toFloat() / totalLessons
    }

    fun stageTitle(totalLessons: Int, masteryOf: (Int) -> Int, completedLessons: Int): String =
        stage(totalLessons, masteryAverage(totalLessons, masteryOf), completedLessons).title

    fun difficultyHint(totalLessons: Int, masteryOf: (Int) -> Int, completedLessons: Int): String {
        return when (stage(totalLessons, masteryAverage(totalLessons, masteryOf), completedLessons)) {
            Stage.FOUNDATION -> "Touch • Listen • Learn"
            Stage.BUILDING -> "Touch • Recall • Repeat"
            Stage.THINKING -> "Choose • Connect • Think"
            Stage.CONFIDENT -> "Recall • Solve • Explore"
        }
    }
}
