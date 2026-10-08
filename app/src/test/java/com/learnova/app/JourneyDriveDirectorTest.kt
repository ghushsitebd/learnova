package com.learnova.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyDriveDirectorTest {
    @Test fun holdReleaseContract() {
        val d = JourneyDriveDirector()
        assertEquals(JourneyDriveDirector.State.READY, d.snapshot().state)
        assertTrue(d.setDriveHeld(true))
        repeat(8) { d.update(0.25) }
        assertTrue(d.snapshot().elapsedSeconds > 0.0)
        assertTrue(d.snapshot().vehicleSpeedMetersPerSecond > 0.0)
        assertFalse(d.setDriveHeld(false))
        assertEquals(JourneyDriveDirector.State.STOPPED, d.snapshot().state)
        repeat(80) { d.update(0.25) }
        assertEquals(0.0, d.snapshot().vehicleSpeedMetersPerSecond, 0.05)
    }

    @Test fun learningWindowContract() {
        val d = JourneyDriveDirector()
        d.setDriveHeld(true)
        repeat(240) { d.update(0.25) }
        assertEquals(JourneyDriveDirector.State.LEARNING, d.snapshot().state)
        assertEquals(60.0, d.snapshot().elapsedSeconds, 0.5)
        repeat(360) { d.update(0.25) }
        assertEquals(150.0, d.snapshot().elapsedSeconds, 0.5)
        assertEquals(90.0, d.snapshot().learningElapsedSeconds, 0.5)
        assertEquals(JourneyDriveDirector.State.DRIVING, d.snapshot().state)
    }

    @Test fun levelBoundContract() {
        val d = JourneyDriveDirector()
        assertTrue(d.setCurrentLevel(500))
        assertEquals(500, d.currentLevel)
        assertTrue(d.validate())
        assertFalse(d.setCurrentLevel(501))
        assertEquals(500, d.currentLevel)
    }
}
