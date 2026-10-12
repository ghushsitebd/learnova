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
        assertEquals("vehicle_026_minivan", VehicleFallbackSelector.fallbackKey("van", "vehicle_027_family_van"))
        assertEquals("vehicle_051_motorcycle", VehicleFallbackSelector.fallbackKey("motorcycle", "vehicle_054_adventure_bike"))
        assertEquals("vehicle_052_scooter", VehicleFallbackSelector.fallbackKey("electric", "vehicle_053_electric_scooter"))
        assertEquals("vehicle_059_bicycle", VehicleFallbackSelector.fallbackKey("cycle", "vehicle_060_cargo_bicycle"))
        assertEquals("vehicle_061_auto_rickshaw", VehicleFallbackSelector.fallbackKey("three_wheeler", "vehicle_062_electric_rickshaw"))
        assertEquals("vehicle_046_city_bus", VehicleFallbackSelector.fallbackKey("bus", "vehicle_049_tour_bus"))
        assertEquals("vehicle_077_small_tractor", VehicleFallbackSelector.fallbackKey("farm", "vehicle_076_farm_utility"))
        assertEquals("vehicle_078_loader", VehicleFallbackSelector.fallbackKey("construction", "vehicle_079_hauler"))
        assertEquals("vehicle_068_buggy", VehicleFallbackSelector.fallbackKey("suv", "vehicle_020_compact_suv"))
        assertEquals("vehicle_091_aero_taxi", VehicleFallbackSelector.fallbackKey("concept", "vehicle_091_aero_taxi"))
        assertEquals("vehicle_091_aero_taxi", VehicleFallbackSelector.fallbackKey("electric", "vehicle_006_electric_sedan"))
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
            "vehicle_001_city_car", "vehicle_026_minivan",
            "vehicle_033_police", "vehicle_035_ambulance", "vehicle_040_box_truck",
            "vehicle_044_fire_truck", "vehicle_046_city_bus", "vehicle_048_school_bus",
            "vehicle_051_motorcycle", "vehicle_052_scooter", "vehicle_058_tricycle",
            "vehicle_059_bicycle", "vehicle_061_auto_rickshaw", "vehicle_068_buggy",
            "vehicle_077_small_tractor", "vehicle_078_loader", "vehicle_082_airport_shuttle",
            "vehicle_091_aero_taxi", "vehicle_097_trex_mount"
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

    @Test fun dinosaurMountSlotsUseDedicatedMountDefinitionsWithoutWheelAnimation() {
        val mounts = listOf(97, 98, 99, 100).map { VehicleCatalog.byId(it) }
        assertEquals(
            listOf(
                "T-Rex Explorer Mount",
                "Triceratops Trail Mount",
                "Stegosaurus Forest Mount",
                "Parasaurolophus Adventure Mount"
            ),
            mounts.map { it.name }
        )
        assertTrue(mounts.all { it.type == "dinosaur_mount" })
        assertTrue(mounts.all { it.targetSpeed in 2.0..18.0 })
        assertFalse(VehicleRenderProfile.forType("dinosaur_mount").enableWheelAnimation)
    }

    @Test fun fallbackPriorityKeepsSpecializedVehiclesOutOfGenericCarFallback() {
        val expectedByType = mapOf(
            "truck" to "vehicle_040_box_truck",
            "bus" to "vehicle_046_city_bus",
            "construction" to "vehicle_078_loader",
            "farm" to "vehicle_077_small_tractor",
            "airport" to "vehicle_082_airport_shuttle",
            "van" to "vehicle_026_minivan",
            "motorcycle" to "vehicle_051_motorcycle",
            "cycle" to "vehicle_059_bicycle",
            "three_wheeler" to "vehicle_061_auto_rickshaw"
        )
        VehicleCatalog.all.filter { it.type in expectedByType.keys }.forEach { vehicle ->
            val expected = when {
                vehicle.type == "motorcycle" && vehicle.assetKey.contains("scooter") -> "vehicle_052_scooter"
                vehicle.type == "cycle" && vehicle.assetKey.contains("tricycle") -> "vehicle_058_tricycle"
                vehicle.type == "bus" && vehicle.assetKey.contains("school") -> "vehicle_048_school_bus"
                else -> expectedByType.getValue(vehicle.type)
            }
            assertEquals(
                "Vehicle ${vehicle.id} (${vehicle.name}) should use a matching class fallback",
                expected,
                VehicleFallbackSelector.fallbackKey(vehicle.type, vehicle.assetKey)
            )
        }
    }
}
