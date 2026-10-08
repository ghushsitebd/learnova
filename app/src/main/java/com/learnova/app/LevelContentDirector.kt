package com.learnova.app

/**
 * Deterministic 500-level content director.
 *
 * A level is a content address, not a separate hard-coded scene. Every ten
 * levels form a chapter and each chapter has a learning focus, biome family,
 * encounter set and pacing profile. This lets the game scale to all 500
 * levels without duplicating gameplay code.
 */
internal object LevelContentDirector {

    data class LessonContent(val english: String, val bangla: String, val arabic: String, val prompt: String, val answer: String)

    data class LevelProfile(
        val level: Int,
        val chapter: Int,
        val biome: WorldDirector.Biome,
        val focus: String,
        val primaryEncounter: String,
        val secondaryEncounter: String,
        val lessonMode: String,
        val lessons: List<LessonContent>
    )

    private const val MAX_LEVEL = 500
    private const val LEVELS_PER_CHAPTER = 10

    private val chapterThemes = arrayOf(
        Triple(WorldDirector.Biome.FOREST, "animals and nature", "deer"),
        Triple(WorldDirector.Biome.RIVER, "water and river life", "fish"),
        Triple(WorldDirector.Biome.MOUNTAIN, "mountains and geography", "eagle"),
        Triple(WorldDirector.Biome.DESERT, "desert animals and adaptation", "camel"),
        Triple(WorldDirector.Biome.PLATEAU, "wildlife and habitats", "horse"),
        Triple(WorldDirector.Biome.MARKET, "community and road safety", "car"),
        Triple(WorldDirector.Biome.VILLAGE, "family and good manners", "cow"),
        Triple(WorldDirector.Biome.COAST, "marine life and cleanliness", "dolphin"),
        Triple(WorldDirector.Biome.FOREST, "Arabic words in nature", "fox"),
        Triple(WorldDirector.Biome.VILLAGE, "Quran learning and character", "rabbit")
    )

    private val lessonModes = arrayOf(
        "listen_repeat", "find_and_say", "count_and_say", "match_words",
        "sound_and_motion", "arabic_word", "bangla_word", "english_word",
        "kindness_choice", "surah_review"
    )

    // Profiles are immutable for a given level. Cache them so the renderer and
    // encounter system can query curriculum data every frame without rebuilding
    // maps/lists and creating avoidable garbage on lower-memory devices.
    private val profileCache = arrayOfNulls<LevelProfile>(MAX_LEVEL)

    fun profile(level: Int): LevelProfile {
        val safe = level.coerceIn(1, MAX_LEVEL)
        val index = safe - 1
        profileCache[index]?.let { return it }
        val created = buildProfile(safe)
        profileCache[index] = created
        return created
    }

    private fun buildProfile(safe: Int): LevelProfile {
        val chapter = (safe - 1) / LEVELS_PER_CHAPTER
        val theme = chapterThemes[chapter % chapterThemes.size]
        val local = (safe - 1) % LEVELS_PER_CHAPTER
        return LevelProfile(
            level = safe,
            chapter = chapter + 1,
            biome = theme.first,
            focus = theme.second,
            primaryEncounter = theme.third,
            secondaryEncounter = expandedEncounter(theme.third, safe),
            lessonMode = lessonModes[(chapter * 3 + local) % lessonModes.size],
            lessons = lessonPack(theme.third, encounterFor(theme.third, local), local, chapter)
        )
    }


    // Expanded, deterministic encounter bank. Reusing a compact bank keeps the APK
    // small while giving all 500 levels varied, multilingual learning targets.
    private val expandedEncounterBank = arrayOf(
        "lion", "tiger", "elephant", "leopard", "bear", "monkey", "giraffe", "zebra",
        "parrot", "falcon", "owl", "peacock", "ostrich", "buffalo", "gazelle", "stag",
        "boar", "frog", "snake", "crab", "shark", "whale", "seal", "penguin",
        "dolphin", "turtle", "fish", "duck", "horse", "donkey", "goat", "sheep",
        "cow", "chicken", "deer", "fox", "rabbit", "wolf", "camel", "bird"
    )

    private val authoredNearFieldSpecies = arrayOf(
        "lion", "deer", "fox", "horse", "wolf", "boar", "rabbit", "stag", "cow"
    )

    private fun expandedEncounter(primary: String, level: Int): String {
        // Bias a predictable portion of secondary encounters toward species
        // that already have verified near-field animated GLBs. This increases real
        // presentation coverage without pretending that every logical catalog species
        // already has a bundled binary asset.
        if (level % 3 == 0) {
            val authored = authoredNearFieldSpecies[
                Math.floorMod(level * 13 + primary.hashCode(), authoredNearFieldSpecies.size)
            ]
            if (authored != primary) return authored
        }
        val start = Math.floorMod(level * 7 + primary.hashCode(), expandedEncounterBank.size)
        for (offset in expandedEncounterBank.indices) {
            val candidate = expandedEncounterBank[(start + offset) % expandedEncounterBank.size]
            if (candidate != primary) return candidate
        }
        return primary
    }

    private fun lessonPack(primary: String, secondary: String, local: Int, chapter: Int): List<LessonContent> {
        val words = mapOf(
            "deer" to Triple("Deer", "হরিণ", "غزال"), "fox" to Triple("Fox", "শিয়াল", "ثعلب"),
            "rabbit" to Triple("Rabbit", "খরগোশ", "أرنب"), "wolf" to Triple("Wolf", "নেকড়ে", "ذئب"),
            "horse" to Triple("Horse", "ঘোড়া", "حصان"), "cow" to Triple("Cow", "গরু", "بقرة"),
            "fish" to Triple("Fish", "মাছ", "سمك"), "duck" to Triple("Duck", "হাঁস", "بطة"),
            "turtle" to Triple("Turtle", "কচ্ছপ", "سلحفاة"), "dolphin" to Triple("Dolphin", "ডলফিন", "دلفين"),
            "camel" to Triple("Camel", "উট", "جمل"), "eagle" to Triple("Eagle", "ঈগল", "نسر"),
            "car" to Triple("Car", "গাড়ি", "سيارة"), "bird" to Triple("Bird", "পাখি", "طائر"),
            "goat" to Triple("Goat", "ছাগল", "ماعز"), "sheep" to Triple("Sheep", "ভেড়া", "خروف"),
            "chicken" to Triple("Chicken", "মুরগি", "دجاج"),
            "lion" to Triple("Lion", "সিংহ", "أسد"),
            "tiger" to Triple("Tiger", "বাঘ", "نمر"),
            "elephant" to Triple("Elephant", "হাতি", "فيل"),
            "leopard" to Triple("Leopard", "চিতাবাঘ", "نمر مرقط"),
            "bear" to Triple("Bear", "ভাল্লুক", "دب"),
            "monkey" to Triple("Monkey", "বানর", "قرد"),
            "giraffe" to Triple("Giraffe", "জিরাফ", "زرافة"),
            "zebra" to Triple("Zebra", "জেব্রা", "حمار وحشي"),
            "parrot" to Triple("Parrot", "টিয়া", "ببغاء"),
            "falcon" to Triple("Falcon", "বাজ", "صقر"),
            "owl" to Triple("Owl", "পেঁচা", "بومة"),
            "peacock" to Triple("Peacock", "ময়ূর", "طاووس"),
            "ostrich" to Triple("Ostrich", "উটপাখি", "نعامة"),
            "buffalo" to Triple("Buffalo", "মহিষ", "جاموس"),
            "gazelle" to Triple("Gazelle", "গ্যাজেল", "غزال"),
            "boar" to Triple("Boar", "বুনো শূকর", "خنزير بري"),
            "frog" to Triple("Frog", "ব্যাঙ", "ضفدع"),
            "snake" to Triple("Snake", "সাপ", "ثعبان"),
            "crab" to Triple("Crab", "কাঁকড়া", "سرطان البحر"),
            "shark" to Triple("Shark", "হাঙর", "قرش"),
            "whale" to Triple("Whale", "তিমি", "حوت"),
            "seal" to Triple("Seal", "সিল", "فقمة"),
            "penguin" to Triple("Penguin", "পেঙ্গুইন", "بطريق"),
            "donkey" to Triple("Donkey", "গাধা", "حمار"),
            "bus" to Triple("Bus", "বাস", "حافلة"),
            "bike" to Triple("Bike", "বাইক", "دراجة"),
            "train" to Triple("Train", "ট্রেন", "قطار"),
            "truck" to Triple("Truck", "ট্রাক", "شاحنة"),
            "bicycle" to Triple("Bicycle", "সাইকেল", "دراجة هوائية")
        )
        fun make(key: String, mode: String): LessonContent {
            val w = words[key] ?: Triple(key.replaceFirstChar { it.uppercase() }, key, key)
            val prompt = when (mode) {
                "arabic_word" -> "Say the Arabic word"
                "bangla_word" -> "Say it in Bangla"
                "english_word" -> "Say it in English"
                "match_words" -> "Match the three words"
                "sound_and_motion" -> "Watch and name the animal"
                "count_and_say" -> "How many do you see?"
                else -> "Listen and repeat"
            }
            val answer = when (mode) {
                "arabic_word" -> w.third
                "bangla_word" -> w.second
                else -> w.first
            }
            return LessonContent(w.first, w.second, w.third, prompt, answer)
        }
        // Five lesson chapters intentionally match the 90-second / 18-second
        // roadside cadence. Each chapter gets a deterministic mode so the same
        // level can be replayed without collapsing back to three repeated prompts.
        val a = lessonModes[(chapter + local) % lessonModes.size]
        val b = lessonModes[(chapter + local + 3) % lessonModes.size]
        val c = lessonModes[(chapter + local + 6) % lessonModes.size]
        val d = lessonModes[(chapter + local + 9) % lessonModes.size]
        val e = lessonModes[(chapter + local + 2) % lessonModes.size]
        return listOf(
            make(primary, a),
            make(secondary, b),
            make(primary, c),
            make(secondary, d),
            make(primary, e)
        )
    }

    /** Fast CI/runtime contract for the complete 500-level curriculum. */
    fun validate(): Boolean {
        if (MAX_LEVEL != 500 || LEVELS_PER_CHAPTER != 10) return false
        for (level in 1..MAX_LEVEL) {
            val profile = profile(level)
            if (profile.level != level || profile.chapter != ((level - 1) / LEVELS_PER_CHAPTER) + 1) return false
            if (profile.lessons.size != 5) return false
            for (lesson in profile.lessons) {
                if (lesson.english.isBlank() || lesson.bangla.isBlank() || lesson.arabic.isBlank()) return false
                if (lesson.prompt.isBlank() || lesson.answer.isBlank()) return false
            }
        }
        return true
    }

    private fun encounterFor(primary: String, local: Int): String {
        val families = when (primary) {
            "deer" -> arrayOf("fox", "rabbit", "wolf", "horse", "cow")
            "fish" -> arrayOf("duck", "turtle", "crab", "dolphin", "shark")
            "eagle" -> arrayOf("falcon", "owl", "goat", "horse", "deer")
            "camel" -> arrayOf("horse", "donkey", "goat", "fox", "bird")
            "horse" -> arrayOf("deer", "cow", "rabbit", "fox", "wolf")
            "car" -> arrayOf("bus", "bike", "train", "truck", "bicycle")
            "cow" -> arrayOf("goat", "sheep", "horse", "chicken", "rabbit")
            "dolphin" -> arrayOf("turtle", "whale", "fish", "crab", "seal")
            "fox" -> arrayOf("deer", "rabbit", "wolf", "horse", "bird")
            else -> arrayOf("deer", "rabbit", "fox", "bird", "horse")
        }
        return families[local % families.size]
    }
}
