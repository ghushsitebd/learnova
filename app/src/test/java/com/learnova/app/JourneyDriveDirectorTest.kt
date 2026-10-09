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
        val elapsedAtRelease = d.snapshot().elapsedSeconds
        repeat(80) { d.update(0.25) }
        assertEquals("level timer must pause while released", elapsedAtRelease, d.snapshot().elapsedSeconds, 0.01)
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

    @Test fun releasingDuringLearningPausesBothClocksAndResumesLearning() {
        val d = JourneyDriveDirector()
        assertTrue(d.setDriveHeld(true))
        repeat(240) { d.update(0.25) }
        assertEquals(JourneyDriveDirector.State.LEARNING, d.snapshot().state)

        assertFalse(d.setDriveHeld(false))
        val elapsedAtRelease = d.snapshot().elapsedSeconds
        val learningAtRelease = d.snapshot().learningElapsedSeconds
        repeat(120) { d.update(0.25) }

        assertEquals(elapsedAtRelease, d.snapshot().elapsedSeconds, 0.01)
        assertEquals(learningAtRelease, d.snapshot().learningElapsedSeconds, 0.01)
        assertTrue(d.setDriveHeld(true))
        assertEquals(JourneyDriveDirector.State.LEARNING, d.snapshot().state)
        repeat(4) { d.update(0.25) }
        assertTrue(d.snapshot().learningElapsedSeconds > learningAtRelease)
        assertEquals(JourneyDriveDirector.State.LEARNING, d.snapshot().state)
    }

    @Test fun completionAndNextLevelContract() {
        val d = JourneyDriveDirector()
        d.setDriveHeld(true)
        repeat(720) { d.update(0.25) }
        assertEquals(JourneyDriveDirector.State.COMPLETED, d.snapshot().state)
        assertEquals(180.0, d.snapshot().elapsedSeconds, 0.01)
        assertTrue(d.isComplete)
        assertTrue(d.advanceToNextLevel())
        assertEquals(2, d.currentLevel)
        assertEquals(JourneyDriveDirector.State.READY, d.snapshot().state)
        assertEquals(0.0, d.snapshot().elapsedSeconds, 0.01)
        assertFalse(d.isComplete)
        assertFalse(d.advanceToNextLevel())
    }

    @Test fun vehicleCatalogContract() {
        assertEquals(500, VehicleCatalog.all.size)
        assertEquals(500, VehicleCatalog.all.map { it.id }.distinct().size)
        assertEquals(1, VehicleCatalog.all.first().id)
        assertEquals(500, VehicleCatalog.all.last().id)
        assertEquals(500, VehicleCatalog.all.map { it.assetKey }.distinct().size)
        assertTrue(VehicleCatalog.all.all { it.availableFromStart })
        assertEquals(VehicleCatalog.all.first(), VehicleCatalog.byId(0))
        assertEquals(VehicleCatalog.all.last(), VehicleCatalog.byId(501))
    }

    @Test fun releaseRestartContract() {
        val d = JourneyDriveDirector()
        d.setDriveHeld(true)
        repeat(12) { d.update(0.25) }
        d.setDriveHeld(false)
        repeat(80) { d.update(0.25) }
        assertTrue(d.setDriveHeld(true))
        repeat(8) { d.update(0.25) }
        assertTrue(d.snapshot().elapsedSeconds > 0.0)
        assertTrue(d.snapshot().vehicleSpeedMetersPerSecond > 0.0)
    }

    @Test fun levelBoundContract() {
        val d = JourneyDriveDirector()
        assertTrue(d.setCurrentLevel(500))
        assertEquals(500, d.currentLevel)
        assertTrue(d.validate())
        assertTrue(d.setCurrentLevel(501))
        assertEquals(500, d.currentLevel)
        assertFalse(d.advanceToNextLevel())
    }

    @Test fun vehicleDynamicsSafetyContract() {
        val profile = VehicleCatalog.byId(184)
        val dynamics = VehicleDriveDynamics(profile)
        dynamics.reset()

        var moving = dynamics.update(0.25, requestedMotion = true)
        repeat(20) {
            moving = dynamics.update(0.25, requestedMotion = true)
        }
        assertTrue(moving.speedMetersPerSecond > 0.0)
        assertTrue(moving.distanceMeters > 0.0)
        assertTrue(moving.wheelRotationRadians > 0.0)
        assertTrue(dynamics.isStable())

        var released = dynamics.update(0.25, requestedMotion = false)
        repeat(40) {
            released = dynamics.update(0.25, requestedMotion = false)
        }
        assertEquals(0.0, released.speedMetersPerSecond, 0.05)
        assertTrue(released.distanceMeters > moving.distanceMeters)
        assertTrue(dynamics.isStable())
    }

    @Test fun vehicleFallbackSelectionContract() {
        assertEquals("vehicle_040_box_truck", VehicleFallbackSelector.fallbackKey("truck", "vehicle_039_small_truck"))
        assertEquals("vehicle_040_box_truck", VehicleFallbackSelector.fallbackKey("van", "vehicle_026_minivan"))
        assertEquals("vehicle_068_buggy", VehicleFallbackSelector.fallbackKey("motorcycle", "vehicle_051_motorcycle"))
        assertEquals("vehicle_068_buggy", VehicleFallbackSelector.fallbackKey("electric", "vehicle_053_electric_scooter"))
        assertEquals("vehicle_068_buggy", VehicleFallbackSelector.fallbackKey("suv", "vehicle_020_compact_suv"))
        assertEquals("vehicle_100_2050_vision", VehicleFallbackSelector.fallbackKey("concept", "vehicle_091_aero_taxi"))
        assertEquals("vehicle_100_2050_vision", VehicleFallbackSelector.fallbackKey("electric", "vehicle_006_electric_sedan"))
        assertEquals("vehicle_001_city_car", VehicleFallbackSelector.fallbackKey("service", "vehicle_031_taxi"))
    }

    @Test fun vehicleSelectionContract() {
        val d = JourneyDriveDirector()
        assertTrue(d.setVehicle(VehicleCatalog.byId(184)))
        d.setDriveHeld(true)
        repeat(4) { d.update(0.25) }
        assertFalse(d.setVehicle(VehicleCatalog.byId(1)))
        d.setDriveHeld(false)
        repeat(40) { d.update(0.25) }
        assertTrue(d.setVehicle(VehicleCatalog.byId(1)))
    }

    @Test fun all500VehicleFallbacksResolveToVerifiedAssetFamilies() {
        val verifiedFallbacks = setOf(
            "vehicle_001_city_car",
            "vehicle_040_box_truck",
            "vehicle_068_buggy",
            "vehicle_100_2050_vision"
        )
        assertEquals(500, VehicleCatalog.all.size)
        VehicleCatalog.all.forEach { vehicle ->
            val fallback = VehicleFallbackSelector.fallbackKey(vehicle.type, vehicle.assetKey)
            assertTrue(
                "Vehicle ${vehicle.id} (${vehicle.name}) has unverified fallback: $fallback",
                fallback in verifiedFallbacks
            )
        }
    }

    @Test fun fallbackPriorityKeepsSpecializedVehiclesOutOfGenericCarFallback() {
        val truckTypes = setOf("truck", "bus", "construction", "emergency", "commercial", "airport", "farm", "van")
        VehicleCatalog.all.filter { it.type in truckTypes }.forEach { vehicle ->
            assertEquals(
                "Vehicle ${vehicle.id} (${vehicle.name}) must use the truck-family model",
                "vehicle_040_box_truck",
                VehicleFallbackSelector.fallbackKey(vehicle.type, vehicle.assetKey)
            )
        }
        VehicleCatalog.all.filter { it.type in setOf("motorcycle", "cycle", "three_wheeler") }.forEach { vehicle ->
            assertEquals(
                "Vehicle ${vehicle.id} (${vehicle.name}) must use the compact/off-road family model",
                "vehicle_068_buggy",
                VehicleFallbackSelector.fallbackKey(vehicle.type, vehicle.assetKey)
            )
        }
    }
}
