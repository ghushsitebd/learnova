package com.learnova.app

/**
 * Runtime quality governor for the mobile 3D world.
 *
 * Uses an exponential moving average (EMA) plus hysteresis/cooldown so quality
 * changes follow sustained performance evidence instead of reacting to single
 * frame spikes. Gameplay is unaffected; only expensive visual features change.
 */
internal class LearnovaAdaptiveQuality {

    enum class Tier {
        HIGH,
        MEDIUM,
        LOW
    }

    private var emaMs = 16.67
    private var initialized = false
    private var evaluationFrames = 0
    private var cooldownFrames = 0

    var tier: Tier = Tier.MEDIUM
        private set

    /**
     * Samples render-frame time. A tier change requires sustained evidence and a
     * cooldown, preventing visible quality oscillation during brief stalls.
     */
    fun sample(frameMs: Double, constrainedDevice: Boolean): Tier? {
        val clamped = frameMs.coerceIn(1.0, 100.0)
        emaMs = if (!initialized) {
            initialized = true
            clamped
        } else {
            // EMA reacts quickly enough to sustained load while filtering spikes.
            emaMs * 0.90 + clamped * 0.10
        }

        evaluationFrames++
        if (cooldownFrames > 0) cooldownFrames--

        if (evaluationFrames < 30) return null

        val old = tier
        val severe = emaMs >= 24.0
        val degraded = emaMs >= 18.0
        val excellent = emaMs <= 12.0

        // Constrained/thermal devices never promote above MEDIUM automatically.
        if (constrainedDevice && tier == Tier.HIGH) {
            tier = Tier.MEDIUM
            cooldownFrames = 90
        } else if (cooldownFrames == 0) {
            tier = when (tier) {
                Tier.HIGH -> if (severe) Tier.MEDIUM else Tier.HIGH
                Tier.MEDIUM -> when {
                    severe -> Tier.LOW
                    !constrainedDevice && excellent -> Tier.HIGH
                    else -> Tier.MEDIUM
                }
                Tier.LOW -> when {
                    severe -> Tier.LOW
                    !constrainedDevice && excellent -> Tier.MEDIUM
                    else -> Tier.LOW
                }
            }

            if (old != tier) {
                // Roughly 1.5 seconds at 60Hz before another automatic transition.
                cooldownFrames = 90
            }
        }

        evaluationFrames = 0
        return if (old != tier) tier else null
    }
}
