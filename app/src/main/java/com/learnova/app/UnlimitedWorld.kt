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

data class LevelInfo(
    val number: Int,
    val chapter: Int,
    val stage: Int,
    val targetDistance: Float,
    val difficulty: String,
    val lessonType: String
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

    // 1000+ procedural levels. Each level has a small, child-friendly goal.
    // The rules become gradually richer without requiring complicated controls.
    fun level(number: Int): LevelInfo {
        val safe = number.coerceAtLeast(1)
        val chapter = ((safe - 1) / 25) + 1
        val stage = ((safe - 1) % 25) + 1
        val target = (0.035f + (stage - 1) * 0.0018f + (chapter - 1) * 0.0007f)
            .coerceAtMost(0.095f)
        val difficulty = when {
            safe <= 25 -> "Starter"
            safe <= 100 -> "Explorer"
            safe <= 250 -> "Adventurer"
            safe <= 500 -> "Discovery"
            safe <= 750 -> "Advanced"
            else -> "Master"
        }
        val lessonType = when {
            safe <= 26 -> "English alphabet"
            safe <= 52 -> "Arabic letters"
            safe <= 68 -> "Arabic sounds"
            safe <= 100 -> "Quran learning"
            else -> activities[(safe * 13) % activities.size]
        }
        return LevelInfo(safe, chapter, stage, target, difficulty, lessonType)
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
        VehicleEntry(11,"R15-style Sport Bike","sportBike","road safety"),
        VehicleEntry(12,"Bicycle","bicycle","balance and road safety"),
        VehicleEntry(13,"Scooter","bike","balance"),
        VehicleEntry(14,"Micro Car","micro","city discovery"),
        VehicleEntry(15,"City Taxi","car","community"),
        VehicleEntry(16,"Delivery Van","van","community"),
        VehicleEntry(17,"Pickup Truck","truck","transport science"),
        VehicleEntry(18,"Coach Bus","bus","community"),
        VehicleEntry(19,"Speed Boat","boat","water discovery"),
        VehicleEntry(20,"Sail Boat","boat","wind science"),
        VehicleEntry(21,"Ferry","boat","water safety"),
        VehicleEntry(22,"Helicopter","air","flight science"),
        VehicleEntry(23,"Airplane","air","flight science"),
        VehicleEntry(24,"Glider","air","wind science"),
        VehicleEntry(25,"Rocket","space","space science")
    )
}
