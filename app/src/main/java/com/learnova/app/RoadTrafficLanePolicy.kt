package com.learnova.app

import kotlin.math.PI
import kotlin.math.abs

/** Two-way traffic lane policy shared by simulation and regression tests. */
internal object RoadTrafficLanePolicy {
    const val NOMINAL_ROAD_WIDTH_METERS = 7.2
    const val LANE_CENTER_OFFSET_METERS = 1.75
    const val MIN_EDGE_CLEARANCE_METERS = 0.25

    fun laneOffset(seed: Long): Double =
        if (((seed ushr 3) and 1L) == 0L) -LANE_CENTER_OFFSET_METERS else LANE_CENTER_OFFSET_METERS

    /** Keep a correction inside the selected lane and never let it cross the centre line. */
    fun correctedLaneOffset(
        baseLane: Double,
        requestedShift: Double,
        roadWidthMeters: Double = NOMINAL_ROAD_WIDTH_METERS
    ): Double {
        // Malformed/custom road dimensions must not create an invalid coerceIn range.
        val safeWidth = if (roadWidthMeters.isFinite()) {
            roadWidthMeters.coerceAtLeast(0.0)
        } else {
            NOMINAL_ROAD_WIDTH_METERS
        }
        val halfWidth = safeWidth * 0.5
        val maxMagnitude = (halfWidth - MIN_EDGE_CLEARANCE_METERS).coerceAtLeast(0.0)
        val minMagnitude = minOf(0.05, maxMagnitude)
        val shift = if (requestedShift.isFinite()) requestedShift else 0.0
        val candidate = baseLane + shift
        val safeCandidate = if (candidate.isFinite()) candidate else 0.0
        val magnitude = abs(safeCandidate).coerceIn(minMagnitude, maxMagnitude)
        return if (baseLane < 0.0) -magnitude else magnitude
    }

    /** The two carriageway directions face opposite ways along the same spline. */
    fun headingYaw(pathYaw: Double, laneOffset: Double): Double =
        if (laneOffset < 0.0) pathYaw + PI else pathYaw
}
