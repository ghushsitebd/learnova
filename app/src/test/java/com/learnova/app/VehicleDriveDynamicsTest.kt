package com.learnova.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for authoritative motion, frame-time bounds, and release-to-coast behaviour. */
class VehicleDriveDynamicsTest {
    private fun dynamics() = VehicleDriveDynamics(VehicleCatalog.byId(1)).apply { reset() }

    @Test fun frameDeltaIsClampedToQuarterSecond() {
        val clamped = dynamics().update(10.0, requestedMotion = true)
        val bounded = dynamics().update(0.25, requestedMotion = true)
        assertEquals(bounded.speedMetersPerSecond, clamped.speedMetersPerSecond, 1e-9)
        assertEquals(bounded.distanceMeters, clamped.distanceMeters, 1e-9)
        assertEquals(bounded.wheelRotationRadians, clamped.wheelRotationRadians, 1e-9)
    }

    @Test fun negativeAndZeroFrameDeltasCannotAdvanceMotion() {
        val d = dynamics()
        val initial = d.snapshot()
        val negative = d.update(-5.0, requestedMotion = true)
        assertEquals(initial.speedMetersPerSecond, negative.speedMetersPerSecond, 0.0)
        assertEquals(initial.distanceMeters, negative.distanceMeters, 0.0)
        assertEquals(initial.wheelRotationRadians, negative.wheelRotationRadians, 0.0)

        val zero = d.update(0.0, requestedMotion = true)
        assertEquals(negative.speedMetersPerSecond, zero.speedMetersPerSecond, 0.0)
        assertEquals(negative.distanceMeters, zero.distanceMeters, 0.0)
        assertEquals(negative.wheelRotationRadians, zero.wheelRotationRadians, 0.0)
    }

    @Test fun releasingPropulsionNeverAcceleratesAndEventuallyStops() {
        val d = dynamics()
        repeat(24) { d.update(0.25, requestedMotion = true) }
        var previous = d.snapshot()
        assertTrue(previous.speedMetersPerSecond > 0.0)

        repeat(60) {
            val current = d.update(0.25, requestedMotion = false)
            assertTrue("released vehicle must not accelerate", current.speedMetersPerSecond <= previous.speedMetersPerSecond + 1e-9)
            assertTrue("coasting distance must be monotonic", current.distanceMeters >= previous.distanceMeters)
            previous = current
        }
        assertEquals(0.0, previous.speedMetersPerSecond, 0.05)
    }

    @Test fun learningMotionUsesChildSafeCrawlTarget() {
        val d = dynamics()
        var current = d.snapshot()
        repeat(80) {
            current = d.update(0.25, requestedMotion = true, learningPause = true)
        }
        assertTrue(current.speedMetersPerSecond > 0.0)
        assertTrue("learning crawl must remain capped", current.speedMetersPerSecond <= 1.4 + 1e-9)
        assertTrue(current.distanceMeters > 0.0)
    }

    @Test fun repeatedLongFramesRemainFiniteAndStable() {
        val d = dynamics()
        repeat(200) {
            val current = d.update(30.0, requestedMotion = true)
            assertTrue(current.speedMetersPerSecond.isFinite())
            assertTrue(current.distanceMeters.isFinite())
            assertTrue(current.wheelRotationRadians.isFinite())
            assertTrue(d.isStable())
        }
    }
}
