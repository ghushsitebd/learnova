package com.learnova.app

data class VehicleEntry(
    val id: Int,
    val name: String,
    val kind: String,
    val learningMode: String
)

data class SmartScene(
    val id: Int,
    val region: String,
    val environment: String,
    val time: String,
    val weather: String,
    val activity: String
)

object LearnovaUnlimitedWorld {
    private val regions = listOf(
        "Forest","River","Mountain","Safari","Ocean","Island","Desert","Arctic",
        "Farm","Village","City","Wetland","Cave","Dinosaur Valley","Sky",
        "Space","Garden","Quran Learning Garden","Arabic Learning Garden",
        "Kindness Village","Science Park","Discovery Island"
    )
    private val environments = listOf(
        "green valley","wide river","quiet lake","open road","animal meadow",
        "tropical coast","snow trail","flower garden","coral bay","rocky canyon",
        "ancient landscape","modern park","learning village","space station"
    )
    private val times = listOf("Morning","Afternoon","Sunset","Night")
    private val weather = listOf("Clear","Cloudy","Breezy","Rainy","Fresh")
    private val activities = listOf(
        "animal discovery","alphabet practice","number practice","Arabic letters",
        "Quran learning","adab and kindness","nature discovery","road safety",
        "colors and shapes","science discovery","memory challenge","vehicle adventure"
    )

    // Procedural IDs allow the world to keep expanding without storing thousands
    // of duplicated scene records inside the APK.
    fun scene(id: Int): SmartScene {
        val safe = if (id < 1) 1 else id
        return SmartScene(
            safe,
            regions[(safe - 1) % regions.size],
            environments[(safe * 3) % environments.size],
            times[(safe * 5) % times.size],
            weather[(safe * 7) % weather.size],
            activities[(safe * 11) % activities.size]
        )
    }

    fun scenes(start: Int, count: Int): List<SmartScene> {
        if (count <= 0) return emptyList()
        return (0 until count).map { scene(start + it) }
    }

    val vehicles = listOf(
        VehicleEntry(1,"Family Car","car","road safety"),
        VehicleEntry(2,"Sport Car","car","colors and shapes"),
        VehicleEntry(3,"Electric Car","car","clean technology"),
        VehicleEntry(4,"Safari Jeep","car","animal discovery"),
        VehicleEntry(5,"School Bus","bus","community"),
        VehicleEntry(6,"Rescue Truck","truck","helping others"),
        VehicleEntry(7,"Fire Truck","truck","safety"),
        VehicleEntry(8,"Police Car","car","road safety"),
        VehicleEntry(9,"Ambulance","car","helping others"),
        VehicleEntry(10,"Motorbike","bike","road safety"),
        VehicleEntry(11,"Scooter","bike","balance"),
        VehicleEntry(12,"Micro Car","micro","city discovery"),
        VehicleEntry(13,"Speed Boat","boat","water discovery"),
        VehicleEntry(14,"Sail Boat","boat","wind science"),
        VehicleEntry(15,"Helicopter","air","flight science"),
        VehicleEntry(16,"Airplane","air","flight science"),
        VehicleEntry(17,"Rocket","space","space science")
    )
}
