package com.learnova.app

/**
 * Runtime quality governor for the mobile 3D world.
 *
 * Uses frame-time EMA plus hysteresis/cooldown. Optional CPU/GPU headroom
 * constraints are supplied by Android 16 ADPF when available.
 */
internal class LearnovaAdaptiveQuality {
    enum class Tier { HIGH, MEDIUM, LOW }

    private var emaMs = 16.67
    private var initialized = false
    private var evaluationFrames = 0
    private var cooldownFrames = 0

    var tier: Tier = Tier.MEDIUM
        private set

    fun sample(
        frameMs: Double,
        constrainedDevice: Boolean,
        cpuHeadroom: Float = Float.NaN,
        gpuHeadroom: Float = Float.NaN
    ): Tier? {
        val clamped = frameMs.coerceIn(1.0, 100.0)
        emaMs = if (!initialized) {
            initialized = true
            clamped
        } else {
            emaMs * 0.90 + clamped * 0.10
        }

        evaluationFrames++
        if (cooldownFrames > 0) cooldownFrames--
        if (evaluationFrames < 30) return null

        val old = tier
        val severeFrameTime = emaMs >= 24.0
        val excellent = emaMs <= 12.0
        val cpuTight = cpuHeadroom.isFinite() && cpuHeadroom < 15.0f
        val gpuTight = gpuHeadroom.isFinite() && gpuHeadroom < 15.0f
        val resourceTight = cpuTight || gpuTight
        val resourceHealthy =
            cpuHeadroom.isFinite() && gpuHeadroom.isFinite() &&
                cpuHeadroom >= 35.0f && gpuHeadroom >= 35.0f

        if (constrainedDevice && tier == Tier.HIGH) {
            tier = Tier.MEDIUM
            cooldownFrames = 90
        } else if (cooldownFrames == 0) {
            tier = when (tier) {
                Tier.HIGH -> if (severeFrameTime || resourceTight) Tier.MEDIUM else Tier.HIGH
                Tier.MEDIUM -> when {
                    severeFrameTime || resourceTight -> Tier.LOW
                    !constrainedDevice && excellent && resourceHealthy -> Tier.HIGH
                    else -> Tier.MEDIUM
                }
                Tier.LOW -> when {
                    severeFrameTime || resourceTight -> Tier.LOW
                    !constrainedDevice && excellent && resourceHealthy -> Tier.MEDIUM
                    else -> Tier.LOW
                }
            }
            if (old != tier) cooldownFrames = 90
        }

        evaluationFrames = 0
        return if (old != tier) tier else null
    }
}
