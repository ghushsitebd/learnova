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
    @Test
    fun correctionRespectsAllAuthoredRoadWidthsAndKeepsEachTrafficStreamOnItsSide() {
        // Widths match the narrowest and widest road profiles used by the procedural mesh.
        val roadWidths = listOf(6.8, 7.2, 7.4, 7.5, 7.8)
        val shifts = (-80..80).map { it / 4.0 }

        for (width in roadWidths) {
            val edgeLimit = width * 0.5 - RoadTrafficLanePolicy.MIN_EDGE_CLEARANCE_METERS
            for (base in listOf(-1.75, 1.75)) {
                for (shift in shifts) {
                    val corrected = RoadTrafficLanePolicy.correctedLaneOffset(base, shift, width)
                    assertTrue("non-finite corrected offset for width=$width shift=$shift", corrected.isFinite())
                    assertTrue("traffic crossed the centre line for width=$width shift=$shift", corrected * base > 0.0)
                    assertTrue("traffic crossed the road edge for width=$width shift=$shift", kotlin.math.abs(corrected) <= edgeLimit + 1e-9)
                }
            }
        }
    }

    @Test
    fun laneSelectionIsStableForRepeatedSeeds() {
        for (seed in 0L..100_000L step 97L) {
            assertEquals(
                RoadTrafficLanePolicy.laneOffset(seed),
                RoadTrafficLanePolicy.laneOffset(seed)
            )
        }
    }

}
