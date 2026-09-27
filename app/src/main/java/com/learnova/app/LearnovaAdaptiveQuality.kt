package com.learnova.app

/**
 * Lightweight runtime quality governor for the mobile 3D world.
 *
 * It reacts to measured frame time rather than assuming that every Android phone
 * has the same CPU/GPU/thermal budget. The renderer can move between quality
 * tiers without changing gameplay or lesson logic.
 */
internal class LearnovaAdaptiveQuality {

    enum class Tier {
        HIGH,
        MEDIUM,
        LOW
    }

    private var accumulatedMs = 0.0
    private var samples = 0
    private var badFrames = 0
    private var goodFrames = 0

    var tier: Tier = Tier.MEDIUM
        private set

    /**
     * Returns a new tier only when the controller has enough evidence to avoid
     * oscillating quality every frame.
     */
    fun sample(frameMs: Double, constrainedDevice: Boolean): Tier? {
        val clamped = frameMs.coerceIn(1.0, 100.0)
        accumulatedMs += clamped
        samples++

        if (clamped > 22.0) {
            badFrames++
            goodFrames = 0
        } else if (clamped < 14.0) {
            goodFrames++
            badFrames = 0
        } else {
            badFrames = (badFrames - 1).coerceAtLeast(0)
            goodFrames = (goodFrames - 1).coerceAtLeast(0)
        }

        if (samples < 30) return null

        val average = accumulatedMs / samples
        val old = tier

        if (badFrames >= 8 || average > 22.0) {
            tier = when (tier) {
                Tier.HIGH -> Tier.MEDIUM
                Tier.MEDIUM -> Tier.LOW
                Tier.LOW -> Tier.LOW
            }
        } else if (!constrainedDevice && goodFrames >= 24 && average < 14.0) {
            tier = when (tier) {
                Tier.LOW -> Tier.MEDIUM
                Tier.MEDIUM -> Tier.HIGH
                Tier.HIGH -> Tier.HIGH
            }
        }

        accumulatedMs = 0.0
        samples = 0
        badFrames = 0
        goodFrames = 0

        return if (old != tier) tier else null
    }
}
