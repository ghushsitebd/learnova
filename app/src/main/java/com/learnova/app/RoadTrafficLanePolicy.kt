package com.learnova.app

import kotlin.math.PI

/** Two-way traffic lane policy shared by simulation and regression tests. */
internal object RoadTrafficLanePolicy {
    const val NOMINAL_ROAD_WIDTH_METERS = 7.2
    const val LANE_CENTER_OFFSET_METERS = 1.75
    const val MIN_EDGE_CLEARANCE_METERS = 0.25

    fun laneOffset(seed: Long): Double =
        if (((seed ushr 3) and 1L) == 0L) -LANE_CENTER_OFFSET_METERS else LANE_CENTER_OFFSET_METERS

    /** Keep a correction inside the selected lane and never let it cross the centre line. */
    fun correctedLaneOffset(baseLane: Double, requestedShift: Double, roadWidthMeters: Double = NOMINAL_ROAD_WIDTH_METERS): Double {
        val halfWidth = roadWidthMeters * 0.5
        val maxMagnitude = halfWidth - MIN_EDGE_CLEARANCE_METERS
        val minMagnitude = 0.05
        val candidate = baseLane + requestedShift
        val magnitude = kotlin.math.abs(candidate).coerceIn(minMagnitude, maxMagnitude)
        return if (baseLane < 0.0) -magnitude else magnitude
    }

    /** The two carriageway directions face opposite ways along the same spline. */
    fun headingYaw(pathYaw: Double, laneOffset: Double): Double =
        if (laneOffset < 0.0) pathYaw + PI else pathYaw
}
