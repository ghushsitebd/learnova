package com.learnova.app

/**
 * Learnova content catalog.
 *
 * The catalog is intentionally data-driven so new animals, worlds and lessons
 * can be added without changing the game engine. Visual assets can be mapped
 * to these IDs later or supplied as lightweight content packs.
 */
data class AnimalEntry(
    val id: Int,
    val name: String,
    val habitat: String,
    val group: String,
    val lesson: String
)

data class WorldScene(
    val id: Int,
    val name: String,
    val mood: String,
    val learningFocus: String
)

object LearnovaWorldCatalog {
    private val baseAnimals = listOf(
        "Elephant","Lion","Tiger","Leopard","Cheetah","Giraffe","Zebra","Hippopotamus",
        "Rhinoceros","Gorilla","Chimpanzee","Orangutan","Bear","Wolf","Fox","Deer",
        "Moose","Camel","Horse","Donkey","Cow","Buffalo","Goat","Sheep","Rabbit",
        "Kangaroo","Panda","Koala","Sloth","Otter","Seal","Walrus","Dolphin","Whale",
        "Shark","Octopus","Turtle","Crocodile","Alligator","Penguin","Eagle","Falcon",
        "Owl","Parrot","Peacock","Swan","Flamingo","Ostrich","Sparrow","Pigeon",
        "Kingfisher","Woodpecker","Hummingbird","Duck","Chicken","Rooster","Turkey",
        "Goose","Frog","Salamander","Snake","Lizard","Chameleon","Iguana","Gecko",
        "Butterfly","Bee","Ant","Ladybird","Dragonfly","Grasshopper","Cricket",
        "Spider","Scorpion","Beetle","Dinosaur","Triceratops","Stegosaurus",
        "Brachiosaurus","Velociraptor","Tyrannosaurus","Mammoth","Bison","Yak",
        "Reindeer","Alpaca","Llama","Meerkat","Hyena","Warthog","Gorilla","Baboon",
        "Mongoose","Porcupine","Hedgehog","Squirrel","Raccoon","Badger","Beaver",
        "Armadillo","Platypus","Seahorse","Jellyfish","Stingray","Swordfish","Salmon",
        "Crab","Lobster","Starfish","Seahorse","Pelican","Heron","Toucan","Canary",
        "Robin","Magpie","Bat","Mole","Marmot"
    )

    private val habitats = listOf(
        "Rainforest","Savanna","Mountain","River","Wetland","Ocean","Desert",
        "Grassland","Arctic","Farm","Village","City","Island","Cave","Dinosaur Valley"
    )

    /**
     * 1,000+ catalog records generated from real species + habitat learning
     * contexts. This is a lightweight index, not 1,000 embedded 3D models.
     */
    val animals: List<AnimalEntry> by lazy {
        val result = ArrayList<AnimalEntry>(baseAnimals.size * habitats.size)
        var id = 1
        for (animal in baseAnimals) {
            for (habitat in habitats) {
                result += AnimalEntry(
                    id = id++,
                    name = animal,
                    habitat = habitat,
                    group = if (animal in listOf("Dinosaur","Triceratops","Stegosaurus","Brachiosaurus","Velociraptor","Tyrannosaurus","Mammoth")) "Prehistoric" else "Wildlife",
                    lesson = "Discover $animal in the $habitat"
                )
            }
        }
        result
    }

    /**
     * Creature runtime index: each catalog record gets a stable behaviour family,
     * movement style and voice eligibility. The renderer can stream authored GLBs
     * later without changing lesson IDs or save-state references.
     */
    data class CreatureRuntimeProfile(
        val creatureId: Int,
        val species: String,
        val habitat: String,
        val behaviour: String,
        val movement: String,
        val speaks: Boolean
    )

    val creatureRuntimeProfiles: List<CreatureRuntimeProfile> by lazy {
        animals.map { entry ->
            val behaviour = when (entry.group) {
                "Prehistoric" -> when (entry.id % 4) {
                    0 -> "territorial"
                    1 -> "herding"
                    2 -> "curious"
                    else -> "roaming"
                }
                else -> when (entry.id % 6) {
                    0 -> "grazing"
                    1 -> "foraging"
                    2 -> "curious"
                    3 -> "social"
                    4 -> "resting"
                    else -> "roaming"
                }
            }
            val movement = when {
                entry.habitat == "Ocean" || entry.habitat == "River" -> "swim"
                entry.habitat == "Arctic" -> "walk_or_swim"
                entry.name in setOf("Eagle", "Falcon", "Owl", "Parrot", "Swan", "Flamingo", "Ostrich", "Sparrow", "Pigeon") -> "fly_or_walk"
                entry.name in setOf("Butterfly", "Bee", "Dragonfly") -> "fly"
                else -> "walk_run"
            }
            CreatureRuntimeProfile(
                creatureId = entry.id,
                species = entry.name,
                habitat = entry.habitat,
                behaviour = behaviour,
                movement = movement,
                speaks = entry.id <= 250
            )
        }
    }

    val scenes = listOf(
        WorldScene(1,"Green Forest","calm","plants and animals"),
        WorldScene(2,"Great River","bright","water and nature"),
        WorldScene(3,"Mountain Valley","adventure","geography"),
        WorldScene(4,"Safari Plains","exciting","wildlife"),
        WorldScene(5,"Ocean Coast","peaceful","marine life"),
        WorldScene(6,"Coral Sea","colorful","sea creatures"),
        WorldScene(7,"Desert Road","warm","adaptation"),
        WorldScene(8,"Snow Land","cool","arctic animals"),
        WorldScene(9,"Happy Farm","friendly","farm animals"),
        WorldScene(10,"Modern City","busy","road safety"),
        WorldScene(11,"Village Path","gentle","community"),
        WorldScene(12,"Island Beach","playful","islands"),
        WorldScene(13,"Dinosaur Valley","prehistoric","dinosaurs"),
        WorldScene(14,"Night Forest","magical","nocturnal animals"),
        WorldScene(15,"Sky Journey","dreamy","birds and flight"),
        WorldScene(16,"Space Gateway","futuristic","space science"),
        WorldScene(17,"Clean Water Park","fresh","cleanliness"),
        WorldScene(18,"Kindness Garden","happy","good character"),
        WorldScene(19,"Arabic Learning Garden","peaceful","Arabic letters"),
        WorldScene(20,"Quran Learning Garden","respectful","Quran learning")
    )
}
