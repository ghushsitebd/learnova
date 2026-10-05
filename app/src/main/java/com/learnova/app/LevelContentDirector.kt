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

    data class LevelProfile(
        val level: Int,
        val chapter: Int,
        val biome: WorldDirector.Biome,
        val focus: String,
        val primaryEncounter: String,
        val secondaryEncounter: String,
        val lessonMode: String
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
            lessonMode = lessonModes[(chapter * 3 + local) % lessonModes.size]
        )
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
