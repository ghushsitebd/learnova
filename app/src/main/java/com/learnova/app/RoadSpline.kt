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


    /** Sample the same world path in a floating-origin coordinate frame. */
    fun sampleRelative(distance: Double, originDistance: Double): Sample {
        val world = sample(distance)
        return world.copy(z = distance - originDistance)
    }

    private fun lateralOffset(distance: Double): Double =
        // Predominantly long-radius bends keep the road readable and natural.
        // Medium/fine components add variation without creating a synthetic
        // repeating S-track or abrupt steering demand.
        sin(distance * 0.132 + 0.35) * 2.25 +
        sin(distance * 0.052 + 1.15) * 0.95 +
        sin(distance * 0.019 + 2.45) * 0.55 +
        sin(distance * 0.0065 + 0.40) * 0.35

    /**
     * Low-frequency terrain undulation rather than a perfectly flat game track.
     * Amplitude is intentionally small enough that a child sees natural terrain
     * variation without the vehicle becoming uncomfortable or visually unstable.
     */
    private fun elevation(distance: Double): Double =
        // Broad terrain undulation defines the grade; a very small higher
        // frequency component gives the suspension something believable to
        // react to without making the child uncomfortable.
        sin(distance * 0.044 + 0.7) * 0.70 +
        sin(distance * 0.016 + 1.9) * 0.34 +
        sin(distance * 0.0085 + 3.1) * 0.18 +
        sin(distance * 0.105 + 0.55) * 0.035 +
        sin(distance * 0.071 + 2.2) * 0.020
}

// Realism stage: road crest and dip shaping.

// Realism stage: smoother lateral curvature.

// Realism stage: long-radius route variation.

// Realism stage: refine banking response.

// Realism stage: stable terrain grade transitions.

// Realism stage: secondary terrain undulation.

// Realism stage: road path continuity.

// Realism stage: camera look-ahead geometry.
