package com.learnova.app

import kotlin.math.atan2
import kotlin.math.sin

/**
 * Authoritative road path sampler for Learnova's real-time 3D world.
 *
 * Vehicle, camera and (in the next renderer stage) procedural road geometry
 * can all sample the same path, preventing visual divergence between them.
 */
internal object RoadSpline {

    data class Sample(
        val x: Double,
        val z: Double,
        val yaw: Float,
        val bank: Float
    )

    fun sample(distance: Double): Sample {
        val x = lateralOffset(distance)
        val ahead = lateralOffset(distance + 0.50)
        val behind = lateralOffset(distance - 0.50)

        val yaw = atan2(ahead - behind, 1.0).toFloat()
        val curvature = (ahead - 2.0 * x + behind) / 0.25
        val bank = (curvature * 0.035).coerceIn(-0.10, 0.10).toFloat()

        return Sample(
            x = x,
            z = distance,
            yaw = yaw,
            bank = bank
        )
    }

    private fun lateralOffset(distance: Double): Double =
        sin(distance * 0.23) * 2.15 +
        sin(distance * 0.075 + 0.8) * 0.85
}
