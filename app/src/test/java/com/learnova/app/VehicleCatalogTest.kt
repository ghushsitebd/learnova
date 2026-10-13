package com.learnova.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCatalogTest {

    @Test
    fun productionCatalogHasFiveHundredUniqueStableSlots() {
        assertEquals(500, VehicleCatalog.all.size)
        assertEquals((1..500).toList(), VehicleCatalog.all.map { it.id })
        assertEquals(500, VehicleCatalog.all.map { it.assetKey }.toSet().size)
    }

    @Test
    fun allLaunchGarageVehiclesAreAvailableWithoutProgressionGates() {
        val launchVehicles = VehicleCatalog.all.take(100)
        assertEquals(100, launchVehicles.size)
        assertTrue(launchVehicles.all { it.availableFromStart })
        assertTrue(launchVehicles.all { it.name.isNotBlank() && it.assetKey.isNotBlank() })
    }

    @Test
    fun dinosaurMountsOccupyTheLastFourLaunchSlots() {
        val expected = mapOf(
            97 to "vehicle_097_trex_mount",
            98 to "vehicle_098_triceratops_mount",
            99 to "vehicle_099_stegosaurus_mount",
            100 to "vehicle_100_parasaurolophus_mount"
        )

        expected.forEach { (id, assetKey) ->
            val vehicle = VehicleCatalog.all[id - 1]
            assertEquals(id, vehicle.id)
            assertEquals(assetKey, vehicle.assetKey)
            assertEquals("dinosaur_mount", vehicle.type)
            assertTrue(vehicle.availableFromStart)
        }
    }

    @Test
    fun vehicleDynamicsArePositiveAndDistinctForDifferentClasses() {
        VehicleCatalog.all.forEach { vehicle ->
            assertTrue("acceleration for ${vehicle.name}", vehicle.driveAcceleration > 0.0)
            assertTrue("coasting for ${vehicle.name}", vehicle.coastDeceleration > 0.0)
            assertTrue("braking for ${vehicle.name}", vehicle.serviceBraking > 0.0)
            assertTrue("steering for ${vehicle.name}", vehicle.steeringResponse > 0.0)
            assertTrue("speed for ${vehicle.name}", vehicle.targetSpeed > 0.0)
        }

        val cycle = VehicleCatalog.all.first { it.type == "cycle" }
        val truck = VehicleCatalog.all.first { it.type == "truck" }
        assertFalse(cycle.driveAcceleration == truck.driveAcceleration)
        assertFalse(cycle.serviceBraking == truck.serviceBraking)
    }
}
