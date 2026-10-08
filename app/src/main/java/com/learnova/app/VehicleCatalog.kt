package com.learnova.app

/**
 * Data-driven garage catalog.
 *
 * Every slot is immediately available; there is intentionally no unlock state,
 * level gate, payment gate, or progression requirement.
 *
 * Asset keys are stable identifiers for the future GLB/LOD pipeline. Until a
 * dedicated asset is supplied, the renderer can fall back to the base vehicle
 * model without breaking the garage UI.
 */
data class VehicleDefinition(
    val id: Int,
    val name: String,
    val type: String,
    val assetKey: String,
    val previewKey: String = assetKey,
    val targetSpeed: Double = 7.2,
    val wheelRadius: Double = 0.30,
    val availableFromStart: Boolean = true,
    // Vehicle-specific longitudinal response keeps different vehicle classes
    // physically distinct while remaining child-friendly and deterministic.
    val driveAcceleration: Double = when (type) {
        "motorcycle", "cycle", "three_wheeler" -> 3.6
        "electric" -> 3.2
        "sport" -> 3.5
        "truck", "bus", "construction", "emergency" -> 1.9
        "farm" -> 1.6
        else -> 2.8
    },
    val coastDeceleration: Double = when (type) {
        "motorcycle", "cycle", "three_wheeler" -> 2.0
        "electric" -> 2.2
        "truck", "bus", "construction", "emergency" -> 1.5
        "farm" -> 1.4
        else -> 2.4
    },
    val serviceBraking: Double = when (type) {
        "motorcycle", "cycle", "three_wheeler" -> 5.4
        "electric" -> 5.0
        "truck", "bus", "construction", "emergency" -> 4.1
        "farm" -> 3.8
        else -> 4.8
    },
    val steeringResponse: Double = when (type) {
        "motorcycle", "cycle" -> 3.8
        "truck", "bus", "construction" -> 2.4
        "farm" -> 2.2
        else -> 3.2
    }
)

internal object VehicleCatalog {
    val all: List<VehicleDefinition> = listOf(
        VehicleDefinition(1, "City Car", "car", "vehicle_001_city_car"),
        VehicleDefinition(2, "Family Sedan", "car", "vehicle_002_family_sedan"),
        VehicleDefinition(3, "Compact Hatchback", "car", "vehicle_003_hatchback"),
        VehicleDefinition(4, "Sport Coupe", "car", "vehicle_004_sport_coupe"),
        VehicleDefinition(5, "Grand Tourer", "car", "vehicle_005_grand_tourer"),
        VehicleDefinition(6, "Electric Sedan", "electric", "vehicle_006_electric_sedan"),
        VehicleDefinition(7, "Electric Hatchback", "electric", "vehicle_007_electric_hatch"),
        VehicleDefinition(8, "Electric SUV", "electric", "vehicle_008_electric_suv"),
        VehicleDefinition(9, "Luxury Sedan", "luxury", "vehicle_009_luxury_sedan"),
        VehicleDefinition(10, "Luxury Coupe", "luxury", "vehicle_010_luxury_coupe"),
        VehicleDefinition(11, "Classic Sedan", "classic", "vehicle_011_classic_sedan"),
        VehicleDefinition(12, "Classic Coupe", "classic", "vehicle_012_classic_coupe"),
        VehicleDefinition(13, "Convertible", "sport", "vehicle_013_convertible"),
        VehicleDefinition(14, "Roadster", "sport", "vehicle_014_roadster"),
        VehicleDefinition(15, "Supercar", "sport", "vehicle_015_supercar"),
        VehicleDefinition(16, "Hypercar", "sport", "vehicle_016_hypercar"),
        VehicleDefinition(17, "Rally Car", "sport", "vehicle_017_rally"),
        VehicleDefinition(18, "Off-Road Car", "offroad", "vehicle_018_offroad_car"),
        VehicleDefinition(19, "Crossover", "suv", "vehicle_019_crossover"),
        VehicleDefinition(20, "Compact SUV", "suv", "vehicle_020_compact_suv"),
        VehicleDefinition(21, "Family SUV", "suv", "vehicle_021_family_suv"),
        VehicleDefinition(22, "Large SUV", "suv", "vehicle_022_large_suv"),
        VehicleDefinition(23, "Adventure SUV", "offroad", "vehicle_023_adventure_suv"),
        VehicleDefinition(24, "Pickup", "pickup", "vehicle_024_pickup"),
        VehicleDefinition(25, "Heavy Pickup", "pickup", "vehicle_025_heavy_pickup"),
        VehicleDefinition(26, "Mini Van", "van", "vehicle_026_minivan"),
        VehicleDefinition(27, "Family Van", "van", "vehicle_027_family_van"),
        VehicleDefinition(28, "Luxury Van", "van", "vehicle_028_luxury_van"),
        VehicleDefinition(29, "Passenger Van", "van", "vehicle_029_passenger_van"),
        VehicleDefinition(30, "Classic Van", "classic", "vehicle_030_classic_van"),
        VehicleDefinition(31, "City Taxi", "service", "vehicle_031_taxi"),
        VehicleDefinition(32, "Electric Taxi", "electric", "vehicle_032_electric_taxi"),
        VehicleDefinition(33, "Police Car", "service", "vehicle_033_police"),
        VehicleDefinition(34, "Fire Response Car", "service", "vehicle_034_fire_response"),
        VehicleDefinition(35, "Ambulance", "service", "vehicle_035_ambulance"),
        VehicleDefinition(36, "Rescue SUV", "service", "vehicle_036_rescue_suv"),
        VehicleDefinition(37, "Delivery Van", "commercial", "vehicle_037_delivery_van"),
        VehicleDefinition(38, "Cargo Van", "commercial", "vehicle_038_cargo_van"),
        VehicleDefinition(39, "Small Truck", "truck", "vehicle_039_small_truck"),
        VehicleDefinition(40, "Box Truck", "truck", "vehicle_040_box_truck"),
        VehicleDefinition(41, "Flatbed Truck", "truck", "vehicle_041_flatbed"),
        VehicleDefinition(42, "Utility Truck", "truck", "vehicle_042_utility_truck"),
        VehicleDefinition(43, "Garbage Truck", "truck", "vehicle_043_garbage_truck"),
        VehicleDefinition(44, "Fire Truck", "emergency", "vehicle_044_fire_truck"),
        VehicleDefinition(45, "Rescue Truck", "emergency", "vehicle_045_rescue_truck"),
        VehicleDefinition(46, "City Bus", "bus", "vehicle_046_city_bus"),
        VehicleDefinition(47, "Electric Bus", "bus", "vehicle_047_electric_bus"),
        VehicleDefinition(48, "School Bus", "bus", "vehicle_048_school_bus"),
        VehicleDefinition(49, "Tour Bus", "bus", "vehicle_049_tour_bus"),
        VehicleDefinition(50, "Mini Bus", "bus", "vehicle_050_minibus"),
        VehicleDefinition(51, "Motorcycle", "motorcycle", "vehicle_051_motorcycle"),
        VehicleDefinition(52, "Scooter", "motorcycle", "vehicle_052_scooter"),
        VehicleDefinition(53, "Electric Scooter", "electric", "vehicle_053_electric_scooter"),
        VehicleDefinition(54, "Adventure Bike", "motorcycle", "vehicle_054_adventure_bike"),
        VehicleDefinition(55, "Sport Bike", "motorcycle", "vehicle_055_sport_bike"),
        VehicleDefinition(56, "Cruiser Bike", "motorcycle", "vehicle_056_cruiser"),
        VehicleDefinition(57, "Dirt Bike", "motorcycle", "vehicle_057_dirt_bike"),
        VehicleDefinition(58, "Tricycle", "cycle", "vehicle_058_tricycle"),
        VehicleDefinition(59, "Bicycle", "cycle", "vehicle_059_bicycle"),
        VehicleDefinition(60, "Cargo Bicycle", "cycle", "vehicle_060_cargo_bicycle"),
        VehicleDefinition(61, "Auto Rickshaw", "three_wheeler", "vehicle_061_auto_rickshaw"),
        VehicleDefinition(62, "Electric Rickshaw", "three_wheeler", "vehicle_062_electric_rickshaw"),
        VehicleDefinition(63, "Microcar", "micro", "vehicle_063_microcar"),
        VehicleDefinition(64, "Compact EV", "electric", "vehicle_064_compact_ev"),
        VehicleDefinition(65, "Solar Concept Car", "concept", "vehicle_065_solar_concept"),
        VehicleDefinition(66, "Future City Pod", "concept", "vehicle_066_city_pod"),
        VehicleDefinition(67, "Autonomous Shuttle", "concept", "vehicle_067_shuttle"),
        VehicleDefinition(68, "Off-Road Buggy", "offroad", "vehicle_068_buggy"),
        VehicleDefinition(69, "Dune Buggy", "offroad", "vehicle_069_dune_buggy"),
        VehicleDefinition(70, "ATV", "offroad", "vehicle_070_atv"),
        VehicleDefinition(71, "Mountain Rescue", "service", "vehicle_071_mountain_rescue"),
        VehicleDefinition(72, "Forest Ranger", "service", "vehicle_072_forest_ranger"),
        VehicleDefinition(73, "Safari SUV", "safari", "vehicle_073_safari_suv"),
        VehicleDefinition(74, "Safari Truck", "safari", "vehicle_074_safari_truck"),
        VehicleDefinition(75, "Farm Pickup", "farm", "vehicle_075_farm_pickup"),
        VehicleDefinition(76, "Farm Utility", "farm", "vehicle_076_farm_utility"),
        VehicleDefinition(77, "Small Tractor", "farm", "vehicle_077_small_tractor"),
        VehicleDefinition(78, "Construction Loader", "construction", "vehicle_078_loader"),
        VehicleDefinition(79, "Construction Hauler", "construction", "vehicle_079_hauler"),
        VehicleDefinition(80, "Road Service Truck", "service", "vehicle_080_road_service"),
        VehicleDefinition(81, "Airport Service", "airport", "vehicle_081_airport_service"),
        VehicleDefinition(82, "Airport Shuttle", "airport", "vehicle_082_airport_shuttle"),
        VehicleDefinition(83, "Airport Bus", "airport", "vehicle_083_airport_bus"),
        VehicleDefinition(84, "Beach Buggy", "offroad", "vehicle_084_beach_buggy"),
        VehicleDefinition(85, "Island Jeep", "offroad", "vehicle_085_island_jeep"),
        VehicleDefinition(86, "Desert SUV", "offroad", "vehicle_086_desert_suv"),
        VehicleDefinition(87, "Mountain Jeep", "offroad", "vehicle_087_mountain_jeep"),
        VehicleDefinition(88, "Forest Utility", "offroad", "vehicle_088_forest_utility"),
        VehicleDefinition(89, "Snow Rescue", "rescue", "vehicle_089_snow_rescue"),
        VehicleDefinition(90, "Rain Rescue", "rescue", "vehicle_090_rain_rescue"),
        VehicleDefinition(91, "2050 Aero Taxi", "concept", "vehicle_091_aero_taxi", targetSpeed = 8.0),
        VehicleDefinition(92, "2050 Solar Transit", "concept", "vehicle_092_solar_transit", targetSpeed = 7.0),
        VehicleDefinition(93, "2050 Hydrogen Utility", "concept", "vehicle_093_hydrogen_utility", targetSpeed = 7.6),
        VehicleDefinition(94, "2050 Adaptive Roadster", "concept", "vehicle_094_adaptive_roadster", targetSpeed = 8.8),
        VehicleDefinition(95, "2050 Terrain EV", "concept", "vehicle_095_terrain_ev", targetSpeed = 7.8),
        VehicleDefinition(96, "2050 Solid-State EV", "concept", "vehicle_096_solid_state_ev", targetSpeed = 8.2),
        VehicleDefinition(97, "2050 Family Capsule", "concept", "vehicle_097_family_capsule", targetSpeed = 7.4),
        VehicleDefinition(98, "2050 Learning Explorer", "concept", "vehicle_098_learning_explorer", targetSpeed = 7.5),
        VehicleDefinition(99, "2050 Amphibious Explorer", "concept", "vehicle_099_amphibious_explorer", targetSpeed = 6.8),
        VehicleDefinition(100, "Learnova 2050 Vision", "concept", "vehicle_100_2050_vision", targetSpeed = 8.5)
    )

    fun byId(id: Int): VehicleDefinition =
        all[(id - 1).coerceIn(0, all.lastIndex)]
}
