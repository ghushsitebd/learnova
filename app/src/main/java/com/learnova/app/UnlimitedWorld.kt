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

data class AnimalSpecies(
    val id: Int,
    val name: String,
    val archetype: String,
    val scale: Float,
    val stride: Float
)


object LearnovaUnlimitedWorld {
    private val animalBases = listOf(
        "African Elephant","Asian Elephant","Giraffe","Lion","Tiger","Leopard","Cheetah","Jaguar",
        "Brown Bear","Polar Bear","Panda","Wolf","Fox","Deer","Moose","Zebra","Rhinoceros","Hippopotamus",
        "Gorilla","Chimpanzee","Orangutan","Kangaroo","Koala","Camel","Horse","Donkey","Goat","Sheep",
        "Cow","Buffalo","Bison","Wild Boar","Rabbit","Squirrel","Otter","Beaver","Crocodile","Alligator",
        "Komodo Dragon","Iguana","Tortoise","Penguin","Flamingo","Eagle","Falcon","Owl","Parrot","Peacock",
        "Swan","Pelican","Dolphin","Whale","Orca","Seal","Sea Lion","Shark","Tuna","Turtle",
        "Butterfly","Bee","Dragonfly","Ladybird","Frog","Salamander","Octopus","Squid","Jellyfish","Seahorse",
        "Triceratops","Tyrannosaurus","Velociraptor","Brachiosaurus","Stegosaurus","Ankylosaurus","Spinosaurus","Pteranodon"
    )
    private val animalArchetypes = listOf(
        "elephant","bear","deer","fox","giraffe","zebra","lion","fish","crocodile","penguin","bird","dolphin","dinosaur"
    )
    val animalSpecies: List<AnimalSpecies> by lazy {
        (0 until 1000).map { i ->
            val base = animalBases[i % animalBases.size]
            val variant = i / animalBases.size
            AnimalSpecies(
                id = i + 1,
                name = if (variant == 0) base else base + " Variant " + (variant + 1),
                archetype = animalArchetypes[(i * 7 + variant) % animalArchetypes.size],
                scale = 0.72f + ((i * 37) % 31) / 100f,
                stride = 0.72f + ((i * 19) % 37) / 100f
            )
        }
    }

    fun animal(id: Int): AnimalSpecies = animalSpecies[(id.coerceAtLeast(1) - 1) % animalSpecies.size]

    private val regions = listOf(
        "Forest","River","Mountain","Safari","Ocean","Island","Desert","Arctic",
        "Farm","Village","City","Wetland","Cave","Dinosaur Valley","Sky",
        "Space","Garden","Quran Learning Garden","Arabic Learning Garden",
        "Kindness Village","Science Park","Discovery Island","Bangladesh Village","Village Market","River Bazaar","Town Market","City Center","School Campus","Mosque Courtyard","Quran School","Library","Farmhouse","Rice Field","Tea Garden","Fishing Village","Boat Terminal","Railway Station","Bus Terminal","Hospital District","Fire Station","Police Station","Construction Zone","Harbor","Beach Town","Mountain Town","Forest Camp","Safari Lodge","Animal Rescue Center","Dinosaur Museum","Science Museum","Space Center","Airport","Amusement Park","Childrens Park","Sports Ground","Food Street","Craft Village","Night Market","Festival Ground","Water Park","Eco Village","Islamic Library","Wudu Garden","Ramadan Community Market","Halal Food Street","Eid Festival Ground","Islamic History Museum","Calligraphy Market","Charity Center"
    )
    private val environments = listOf(
        "green valley","wide river","quiet lake","open road","animal meadow",
        "tropical coast","snow trail","flower garden","coral bay","rocky canyon",
        "ancient landscape","modern park","learning village","space station","busy village road","colorful market street","riverside bazaar","rice-growing countryside","school road","mosque garden","community center","farm lane","tea estate road","fishing harbor","railway crossing","city boulevard","hospital road","rescue district","harbor road","beach promenade","mountain pass","forest trail","wildlife reserve","museum avenue","airport approach","family park","sports avenue","festival street"
    )
    private val times = listOf("Morning","Afternoon","Sunset","Night")
    private val weather = listOf("Clear","Cloudy","Breezy","Rainy","Fresh")
    private val activities = listOf(
        "animal discovery","alphabet practice","number practice","Arabic letters",
        "Quran learning","adab and kindness","nature discovery","road safety",
        "colors and shapes","science discovery","memory challenge","vehicle adventure","market discovery","village life","community helpers","mosque manners","Quran reading","Arabic vocabulary","nature stewardship","animal care","farm discovery","water safety","travel discovery","kindness challenge","clean environment","food and nutrition","history discovery","science experiment","Arabic pronunciation","Surah Fatiha","Short Surah Practice","Islamic manners","Mosque manners","Wudu learning","Charity and kindness","Halal food discovery","Arabic calligraphy","Islamic history"
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
            safe % 9 == 0 -> "Arabic pronunciation"
            safe % 11 == 0 -> "Surah Fatiha"
            safe % 13 == 0 -> "Short Surah Practice"
            safe % 17 == 0 -> "Islamic manners"
            safe % 19 == 0 -> "Wudu learning"
            safe % 23 == 0 -> "Charity and kindness"
            safe % 29 == 0 -> "Halal food discovery"
            safe % 31 == 0 -> "Arabic calligraphy"
            safe % 37 == 0 -> "Islamic history"
            else -> activities[(safe * 13) % activities.size]
        }
        return LevelInfo(safe, chapter, stage, target, difficulty, lessonType)
    }

    // A broad real-world vehicle catalog. Rendering stays lightweight by reusing
    // optimized procedural body templates while preserving distinct vehicle roles.
    val vehicles = listOf(
        VehicleEntry(1,"Family Sedan","car","road safety"),
        VehicleEntry(2,"Executive Sedan","car","road safety"),
        VehicleEntry(3,"Compact Hatchback","car","colors and shapes"),
        VehicleEntry(4,"Electric Hatchback","car","clean technology"),
        VehicleEntry(5,"Electric Sedan","car","clean technology"),
        VehicleEntry(6,"Electric SUV","car","clean technology"),
        VehicleEntry(7,"Family SUV","car","road safety"),
        VehicleEntry(8,"Off-Road SUV","car","nature discovery"),
        VehicleEntry(9,"Safari 4x4","car","animal discovery"),
        VehicleEntry(10,"Pickup Truck","truck","transport science"),
        VehicleEntry(11,"Heavy Duty Truck","truck","transport science"),
        VehicleEntry(12,"Box Delivery Truck","truck","community"),
        VehicleEntry(13,"Fire Truck","truck","safety"),
        VehicleEntry(14,"Rescue Truck","truck","helping others"),
        VehicleEntry(15,"Ambulance","van","helping others"),
        VehicleEntry(16,"Police Car","car","road safety"),
        VehicleEntry(17,"Taxi","car","community"),
        VehicleEntry(18,"School Bus","bus","community"),
        VehicleEntry(19,"City Bus","bus","community"),
        VehicleEntry(20,"Coach Bus","bus","community"),
        VehicleEntry(21,"Minivan","van","family travel"),
        VehicleEntry(22,"Delivery Van","van","community"),
        VehicleEntry(23,"Panel Van","van","transport science"),
        VehicleEntry(24,"Micro Car","micro","city discovery"),
        VehicleEntry(25,"City Car","car","city discovery"),
        VehicleEntry(26,"Sports Coupe","car","colors and shapes"),
        VehicleEntry(27,"Supercar","car","science discovery"),
        VehicleEntry(28,"Convertible","car","nature discovery"),
        VehicleEntry(29,"Classic Car","car","history discovery"),
        VehicleEntry(30,"Rally Car","car","road safety"),
        VehicleEntry(31,"Race Car","car","science discovery"),
        VehicleEntry(32,"Motorbike","bike","road safety"),
        VehicleEntry(33,"Adventure Bike","sportBike","nature discovery"),
        VehicleEntry(34,"Sport Bike","sportBike","road safety"),
        VehicleEntry(35,"Scooter","bike","balance"),
        VehicleEntry(36,"Bicycle","bicycle","balance and road safety"),
        VehicleEntry(37,"Mountain Bike","bicycle","nature discovery"),
        VehicleEntry(38,"Speed Boat","boat","water discovery"),
        VehicleEntry(39,"Sail Boat","boat","wind science"),
        VehicleEntry(40,"Ferry","boat","water safety"),
        VehicleEntry(41,"Rescue Boat","boat","helping others"),
        VehicleEntry(42,"Helicopter","air","flight science"),
        VehicleEntry(43,"Passenger Airplane","air","flight science"),
        VehicleEntry(44,"Cargo Airplane","air","transport science"),
        VehicleEntry(45,"Glider","air","wind science"),
        VehicleEntry(46,"Rescue Helicopter","air","helping others"),
        VehicleEntry(47,"Rocket","space","space science"),
        VehicleEntry(48,"Lunar Lander","space","space science"),
        VehicleEntry(49,"Airport Shuttle","bus","community"),
        VehicleEntry(50,"Tour Bus","bus","nature discovery"),
        VehicleEntry(51,"Tractor","truck","farm discovery"),
        VehicleEntry(52,"Construction Truck","truck","science discovery"),
        VehicleEntry(53,"Garbage Truck","truck","community"),
        VehicleEntry(54,"Tow Truck","truck","helping others"),
        VehicleEntry(55,"Fire Engine","truck","safety"),
        VehicleEntry(56,"Emergency Van","van","safety"),
        VehicleEntry(57,"Luxury SUV","car","colors and shapes"),
        VehicleEntry(58,"Crossover","car","road safety"),
        VehicleEntry(59,"Off-Road Pickup","truck","nature discovery"),
        VehicleEntry(60,"Electric City Van","van","clean technology")
    )
}
