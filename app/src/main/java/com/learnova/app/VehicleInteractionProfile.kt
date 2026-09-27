package com.learnova.app

data class VehicleInteractionProfile(
    val vehicleType: String,
    val entryMode: String,
    val hasCabin: Boolean,
    val doorAnimation: String,
    val passengerAnimation: String
)

object VehicleInteractionProfiles {
    fun forVehicle(type: String): VehicleInteractionProfile = when (type) {
        "micro", "van" -> VehicleInteractionProfile(type, "side_door", true, "open_side_door", "enter_cabin")
        "bus" -> VehicleInteractionProfile(type, "front_or_side_door", true, "open_passenger_door", "enter_cabin")
        "motorcycle", "cycle" -> VehicleInteractionProfile(type, "open_mount", false, "none", "mount_vehicle")
        else -> VehicleInteractionProfile(type, "side_door", true, "open_side_door", "enter_cabin")
    }
}
