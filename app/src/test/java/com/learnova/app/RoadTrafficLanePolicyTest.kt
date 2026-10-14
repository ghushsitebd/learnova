package com.learnova.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.PI

class RoadTrafficLanePolicyTest {
    @Test
    fun deterministicTrafficOccupiesOnlyTheTwoLaneCenters() {
        val seen = mutableSetOf<Double>()
        for (seed in 0L..10_000L) {
            val lane = RoadTrafficLanePolicy.laneOffset(seed)
            seen += lane
            assertTrue(abs(lane) < RoadTrafficLanePolicy.NOMINAL_ROAD_WIDTH_METERS * 0.5)
        }
        assertEquals(setOf(-1.75, 1.75), seen)
    }

    @Test
    fun laneCorrectionCannotCrossCentreLineOrLeaveRoadEdges() {
        for (base in listOf(-1.75, 1.75)) {
            for (shift in listOf(-9.0, -1.15, -0.2, 0.0, 0.2, 1.15, 9.0)) {
                val corrected = RoadTrafficLanePolicy.correctedLaneOffset(base, shift)
                assertTrue("lane correction crossed centre line", corrected * base > 0.0)
                assertTrue(
                    "lane correction crossed road edge",
                    abs(corrected) <= RoadTrafficLanePolicy.NOMINAL_ROAD_WIDTH_METERS * 0.5 -
                        RoadTrafficLanePolicy.MIN_EDGE_CLEARANCE_METERS
                )
            }
        }
    }

    @Test
    fun opposingLanesFaceOppositeDirectionsAlongSameRoadSpline() {
        val pathYaw = 0.42
        val left = RoadTrafficLanePolicy.headingYaw(pathYaw, -1.75)
        val right = RoadTrafficLanePolicy.headingYaw(pathYaw, 1.75)
        assertEquals(PI, abs(left - right), 0.000001)
    }
}
