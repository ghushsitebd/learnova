package com.learnova.app

import kotlin.math.abs
import kotlin.math.sin

/**
 * Data-driven encounter scheduler for Learnova's 500-level journey.
 *
 * The director deliberately contains no Android/UI/Filament state. It can therefore
 * be reused by the 3D renderer, audio layer, analytics, and future offline content
 * packs without multiplying gameplay logic.
 */
internal class LearningEncounterDirector {

    enum class Phase { APPROACH, OBSERVE, REPEAT, REWARD }

    data class Encounter(
        val level: Int,
        val chapter: Int,
        val phase: Phase,
        val intensity: Float,
        val companionCue: Boolean,
        val recommendedPauseSeconds: Double
    )

    private var lastLevel = -1
    private var lastChapter = -1
    private var encounterSerial = 0L

    fun resetIfNeeded(level: Int, chapter: Int) {
        if (level != lastLevel || chapter != lastChapter) {
            lastLevel = level
            lastChapter = chapter
            encounterSerial++
        }
    }

    fun encounter(level: Int, chapter: Int, elapsedSeconds: Double): Encounter {
        resetIfNeeded(level, chapter)

        // Five 18-second chapters form one 90-second learning window.
        // The phase is deterministic, so replaying a level produces the same
        // learning rhythm while the small intensity modulation prevents a flat feel.
        val chapterProgress = ((elapsedSeconds - chapter * 18.0) / 18.0)
            .coerceIn(0.0, 0.999)
        val phase = when {
            chapterProgress < 0.22 -> Phase.APPROACH
            chapterProgress < 0.58 -> Phase.OBSERVE
            chapterProgress < 0.84 -> Phase.REPEAT
            else -> Phase.REWARD
        }

        val levelBand = ((level.coerceIn(1, 500) - 1) / 50)
        val chapterPulse = ((chapter + levelBand) % 5) / 4.0
        val motionPulse = ((sin(chapterProgress * Math.PI * 2.0) + 1.0) * 0.5)
        val intensity = (0.55 + levelBand * 0.025 + chapterPulse * 0.08 + motionPulse * 0.06)
            .coerceIn(0.55, 1.0)
            .toFloat()

        return Encounter(
            level = level.coerceIn(1, 500),
            chapter = chapter.coerceIn(0, 4),
            phase = phase,
            intensity = intensity,
            companionCue = phase == Phase.APPROACH || phase == Phase.REPEAT,
            recommendedPauseSeconds = when (phase) {
                Phase.APPROACH -> 1.5
                Phase.OBSERVE -> 2.0
                Phase.REPEAT -> 1.25
                Phase.REWARD -> 0.75
            }
        )
    }

    fun isMilestoneLevel(level: Int): Boolean {
        val safe = level.coerceIn(1, 500)
        return safe == 1 || safe == 5 || safe % 10 == 0 || safe == 500
    }

    fun masteryBand(level: Int): Int =
        ((level.coerceIn(1, 500) - 1) / 50).coerceIn(0, 9)

    fun repetitionWeight(level: Int, chapter: Int): Float {
        val safeLevel = level.coerceIn(1, 500)
        val safeChapter = chapter.coerceIn(0, 4)
        val novelty = abs(((safeLevel * 31 + safeChapter * 17) % 100) - 50) / 50f
        return (0.35f + novelty * 0.25f + masteryBand(safeLevel) * 0.035f).coerceIn(0.35f, 0.8f)
    }
}
