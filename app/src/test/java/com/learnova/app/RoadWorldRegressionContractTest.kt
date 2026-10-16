package com.learnova.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Regression contracts for the deterministic road and streamed world timeline. */
class RoadWorldRegressionContractTest {

    @Test
    fun roadSamplesRemainFiniteAndWithinComfortBoundsAcrossLongJourneys() {
        for (distance in 0..120_000 step 7) {
            val sample = RoadSpline.sample(distance.toDouble())
            assertTrue("x must be finite at $distance", sample.x.isFinite())
            assertTrue("y must be finite at $distance", sample.y.isFinite())
            assertTrue("yaw must be finite at $distance", sample.yaw.isFinite())
            assertTrue("bank must be finite at $distance", sample.bank.isFinite())
            assertTrue("grade must be finite at $distance", sample.grade.isFinite())
            assertTrue("bank exceeds comfort contract at $distance", abs(sample.bank) <= 0.12f)
            assertTrue("grade exceeds comfort contract at $distance", abs(sample.grade) <= 0.16f)
        }
    }

    @Test
    fun relativeRoadSamplesPreservePathAndShiftOnlyLongitudinalOrigin() {
        for (distance in listOf(0.0, 0.5, 12.0, 96.0, 1_000.0, 12_345.0)) {
            val origin = distance - 25.0
            val world = RoadSpline.sample(distance)
            val relative = RoadSpline.sampleRelative(distance, origin)
            assertEquals(world.x, relative.x, 0.000001)
            assertEquals(world.y, relative.y, 0.000001)
            assertEquals(world.yaw, relative.yaw, 0.000001f)
            assertEquals(world.bank, relative.bank, 0.000001f)
            assertEquals(world.grade, relative.grade, 0.000001f)
            assertEquals(25.0, relative.z, 0.000001)
        }
    }

    @Test
    fun worldTimelineRetainsAllEightBiomesAndValidAtmosphereChannels() {
        val seen = (0..7).map { WorldDirector.profile(it * 96.0).biome }.toSet()
        assertEquals(WorldDirector.Biome.values().toSet(), seen)

        for (distance in 0..100_000 step 13) {
            val profile = WorldDirector.atmosphere(distance.toDouble())
            assertTrue(profile.skyR in 0f..1f)
            assertTrue(profile.skyG in 0f..1f)
            assertTrue(profile.skyB in 0f..1f)
            assertTrue(profile.exposure.isFinite())
            assertTrue(profile.cameraHeight.isFinite())
            assertTrue(profile.lookAheadLift.isFinite())
            assertTrue(profile.fovBias.isFinite())
        }
    }

    @Test
    fun releasedVehicleCoastsForwardThenSettlesWithoutNegativeSpeed() {
        val dynamics = VehicleDriveDynamics(VehicleCatalog.byId(1))
        repeat(24) { dynamics.update(0.25, requestedMotion = true) }
        val beforeRelease = dynamics.snapshot()
        assertTrue(beforeRelease.speedMetersPerSecond > 0.0)

        var previousSpeed = beforeRelease.speedMetersPerSecond
        var travelledAfterRelease = false
        repeat(80) {
            val snapshot = dynamics.update(0.25, requestedMotion = false)
            assertTrue(snapshot.speedMetersPerSecond >= 0.0)
            assertTrue(snapshot.speedMetersPerSecond <= previousSpeed + 0.000001)
            if (snapshot.distanceMeters > beforeRelease.distanceMeters) travelledAfterRelease = true
            previousSpeed = snapshot.speedMetersPerSecond
        }
        assertTrue("release should coast rather than teleport-stop", travelledAfterRelease)
        assertEquals(0.0, dynamics.snapshot().speedMetersPerSecond, 0.05)
    }

    @Test
    fun oversizedFrameDeltaIsClampedAndCannotCreateAnUnboundedJump() {
        val normal = VehicleDriveDynamics(VehicleCatalog.byId(1))
        val oversized = VehicleDriveDynamics(VehicleCatalog.byId(1))
        val normalResult = normal.update(0.25, requestedMotion = true)
        val oversizedResult = oversized.update(25.0, requestedMotion = true)
        assertEquals(normalResult.speedMetersPerSecond, oversizedResult.speedMetersPerSecond, 0.000001)
        assertEquals(normalResult.distanceMeters, oversizedResult.distanceMeters, 0.000001)
        assertTrue(oversized.isStable())
    }
}
