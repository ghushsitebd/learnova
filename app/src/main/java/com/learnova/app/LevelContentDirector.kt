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

    fun profile(level: Int): LevelProfile {
        val safe = level.coerceIn(1, MAX_LEVEL)
        val chapter = (safe - 1) / LEVELS_PER_CHAPTER
        val theme = chapterThemes[chapter % chapterThemes.size]
        val local = (safe - 1) % LEVELS_PER_CHAPTER
        return LevelProfile(
            level = safe,
            chapter = chapter + 1,
            biome = theme.first,
            focus = theme.second,
            primaryEncounter = theme.third,
            secondaryEncounter = encounterFor(theme.third, local),
            lessonMode = lessonModes[(chapter * 3 + local) % lessonModes.size],
            lessons = lessonPack(theme.third, encounterFor(theme.third, local), local, chapter)
        )
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
            "chicken" to Triple("Chicken", "মুরগি", "دجاج")
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
        val a = lessonModes[(chapter + local) % lessonModes.size]
        val b = lessonModes[(chapter + local + 3) % lessonModes.size]
        val d = lessonModes[(chapter + local + 6) % lessonModes.size]
        return listOf(make(primary, a), make(secondary, b), make(primary, d))
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
