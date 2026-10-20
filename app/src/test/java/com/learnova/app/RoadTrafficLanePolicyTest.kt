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
    fun narrowOrMalformedRoadWidthsNeverCreateAnInvalidClampRange() {
        for (width in listOf(0.0, 0.1, 0.5, -3.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val corrected = RoadTrafficLanePolicy.correctedLaneOffset(
                baseLane = -1.75,
                requestedShift = 99.0,
                roadWidthMeters = width
            )
            assertTrue("offset must remain finite for width=$width", corrected.isFinite())
            assertTrue("offset must not exceed nominal road bounds for width=$width",
                abs(corrected) <= RoadTrafficLanePolicy.NOMINAL_ROAD_WIDTH_METERS * 0.5)
        }
    }

    @Test
    fun nonFiniteLaneCorrectionsAreSafelyIgnored() {
        for (shift in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val corrected = RoadTrafficLanePolicy.correctedLaneOffset(1.75, shift)
            assertTrue(corrected.isFinite())
            assertEquals(1.75, corrected, 0.0)
        }
    }
}
