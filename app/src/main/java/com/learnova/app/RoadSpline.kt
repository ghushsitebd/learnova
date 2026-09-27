package com.learnova.app

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Authoritative road path sampler for Learnova's real-time 3D world.
 *
 * The path models three coupled quantities used by the vehicle, camera and
 * procedural road: horizontal curvature, gentle terrain elevation and
 * physically coherent banking. Keeping one sampler prevents the road and
 * vehicle from visually disagreeing.
 */
internal object RoadSpline {

    data class Sample(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val bank: Float,
        val grade: Float
    )

    fun sample(distance: Double): Sample {
        val x = lateralOffset(distance)
        val ahead = lateralOffset(distance + 0.50)
        val behind = lateralOffset(distance - 0.50)

        val y = elevation(distance)
        val yAhead = elevation(distance + 0.50)
        val yBehind = elevation(distance - 0.50)

        val dx = 1.0
        val dz = 1.0
        val yaw = atan2(ahead - behind, 2.0 * dx).toFloat()

        val curvature = (ahead - 2.0 * x + behind) / 0.25
        val bankFromCurve = curvature * 0.040
        val grade = ((yAhead - yBehind) / (2.0 * dz)).coerceIn(-0.16, 0.16)
        val bankFromGrade = grade * 0.10
        val bank = (bankFromCurve + bankFromGrade).coerceIn(-0.12, 0.12).toFloat()

        return Sample(
            x = x,
            y = y,
            z = distance,
            yaw = yaw,
            bank = bank,
            grade = grade.toFloat()
        )
    }

    private fun lateralOffset(distance: Double): Double =
        sin(distance * 0.23) * 2.15 +
        sin(distance * 0.075 + 0.8) * 0.85 +
        sin(distance * 0.031 + 2.1) * 0.45

    /**
     * Low-frequency terrain undulation rather than a perfectly flat game track.
     * Amplitude is intentionally small enough that a child sees natural terrain
     * variation without the vehicle becoming uncomfortable or visually unstable.
     */
    private fun elevation(distance: Double): Double =
        sin(distance * 0.045 + 0.7) * 0.70 +
        sin(distance * 0.017 + 1.9) * 0.34 +
        sin(distance * 0.009 + 3.1) * 0.18
}

// Realism stage: road crest and dip shaping.
