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
        Triple(WorldDirector.Biome.FOREST, "Quran and Islamic foundations", "deer"),
        Triple(WorldDirector.Biome.RIVER, "wudu and cleanliness", "fish"),
        Triple(WorldDirector.Biome.MOUNTAIN, "prophets and Islamic history", "eagle"),
        Triple(WorldDirector.Biome.DESERT, "salah and daily worship", "camel"),
        Triple(WorldDirector.Biome.PLATEAU, "dua and dhikr", "horse"),
        Triple(WorldDirector.Biome.MARKET, "halal living and honest dealings", "car"),
        Triple(WorldDirector.Biome.VILLAGE, "Islamic manners and family rights", "cow"),
        Triple(WorldDirector.Biome.COAST, "Quranic surahs and remembrance", "dolphin"),
        Triple(WorldDirector.Biome.FOREST, "Arabic for Quran learning", "fox"),
        Triple(WorldDirector.Biome.VILLAGE, "Islamic character and kindness", "rabbit")
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

    /**
     * Core study is Islamic learning only. Animals and vehicles remain as
     * scenery/gameplay companions; they are not the curriculum subject.
     * Quran passages are not fabricated here: recitation content must come
     * from a verified Quran source/catalog.
     */
    private val islamicLessons = listOf(
        LessonContent("Bismillah", "আল্লাহর নামে শুরু", "بِسْمِ اللَّهِ", "শেখো: কাজের শুরুতে বিসমিল্লাহ বলা", "Bismillah"),
        LessonContent("Alhamdulillah", "সকল প্রশংসা আল্লাহর", "الْحَمْدُ لِلَّهِ", "শেখো: আল্লাহর প্রশংসা ও কৃতজ্ঞতা", "Alhamdulillah"),
        LessonContent("Allahu Akbar", "আল্লাহ সর্বশ্রেষ্ঠ", "اللَّهُ أَكْبَرُ", "শেখো: আল্লাহু আকবারের অর্থ", "Allahu Akbar"),
        LessonContent("SubhanAllah", "আল্লাহ পবিত্র", "سُبْحَانَ اللَّهِ", "শেখো: সুবহানাল্লাহ কখন বলা হয়", "SubhanAllah"),
        LessonContent("Astaghfirullah", "আল্লাহর কাছে ক্ষমা চাই", "أَسْتَغْفِرُ اللَّهَ", "শেখো: ইস্তিগফারের অর্থ", "Astaghfirullah"),
        LessonContent("Assalamu Alaikum", "আপনার ওপর শান্তি বর্ষিত হোক", "السَّلَامُ عَلَيْكُمْ", "শেখো: সালামের আদব", "Assalamu Alaikum"),
        LessonContent("Al-Fatiha", "সূরা আল-ফাতিহা", "سورة الفاتحة", "শেখো: সূরা ফাতিহার নাম ও নামাজে এর গুরুত্ব", "Al-Fatiha"),
        LessonContent("Al-Ikhlas", "সূরা আল-ইখলাস", "سورة الإخلاص", "শেখো: সূরা ইখলাসের পরিচয়", "Al-Ikhlas"),
        LessonContent("Al-Falaq", "সূরা আল-ফালাক", "سورة الفلق", "শেখো: সূরা ফালাকের পরিচয়", "Al-Falaq"),
        LessonContent("An-Nas", "সূরা আন-নাস", "سورة الناس", "শেখো: সূরা নাসের পরিচয়", "An-Nas"),
        LessonContent("Quran", "পবিত্র কুরআন", "القرآن الكريم", "শেখো: কুরআনের আদব ও যত্ন", "Quran"),
        LessonContent("Wudu", "অজু", "الوضوء", "শেখো: অজুর ধাপ ও পরিচ্ছন্নতা", "Wudu"),
        LessonContent("Salah", "নামাজ", "الصلاة", "শেখো: নামাজের সময়, প্রস্তুতি ও আদব", "Salah"),
        LessonContent("Masjid", "মসজিদ", "المسجد", "শেখো: মসজিদের আদব", "Masjid"),
        LessonContent("Sawm", "রোজা", "الصوم", "শেখো: রমজানের রোজার পরিচয়", "Sawm"),
        LessonContent("Zakah", "যাকাত", "الزكاة", "শেখো: যাকাতের উদ্দেশ্য", "Zakah"),
        LessonContent("Sadaqah", "সদকা", "الصدقة", "শেখো: দান ও সাহায্যের গুরুত্ব", "Sadaqah"),
        LessonContent("Tawheed", "তাওহিদ: আল্লাহ এক", "التوحيد", "শেখো: তাওহিদের মৌলিক ধারণা", "Tawheed"),
        LessonContent("Adab", "ইসলামি শিষ্টাচার", "الأدب", "শেখো: কথা ও আচরণে সুন্দর আদব", "Adab"),
        LessonContent("Truthfulness", "সত্যবাদিতা", "الصدق", "শেখো: সত্য কথা বলা ও আমানত রক্ষা", "Truthfulness"),
        LessonContent("Parents", "মা-বাবার প্রতি সদাচরণ", "بر الوالدين", "শেখো: মা-বাবার সঙ্গে উত্তম আচরণ", "Parents"),
        LessonContent("Cleanliness", "পরিচ্ছন্নতা", "النظافة", "শেখো: শরীর, পোশাক ও জায়গা পরিষ্কার রাখা", "Cleanliness"),
        LessonContent("Halal", "হালাল", "حلال", "শেখো: হালাল-হারাম বিষয়ে বড়দের/বিশ্বস্ত আলেমের নির্দেশনা মানা", "Halal"),
        LessonContent("Prophet Muhammad ﷺ", "নবী মুহাম্মদ ﷺ", "محمد رسول الله", "শেখো: নবীজির ﷺ জীবনী ও উত্তম চরিত্র", "Prophet Muhammad ﷺ"),
        LessonContent("Ayat al-Kursi", "আয়াতুল কুরসি", "آية الكرسي", "শেখো: আয়াতুল কুরসির পরিচয়; তিলাওয়াত যাচাইকৃত উৎস থেকে", "Ayat al-Kursi"),
        LessonContent("Dua", "দোয়া", "الدعاء", "শেখো: দোয়া করা ও আল্লাহর কাছে চাওয়া", "Dua")
    )

    private fun lessonPack(primary: String, secondary: String, local: Int, chapter: Int): List<LessonContent> {
        // Repeat the verified Islamic topic bank deterministically across all 500 levels.
        // Wildlife/vehicle encounters remain visual gameplay, not secular study subjects.
        val seed = Math.floorMod((chapter * LEVELS_PER_CHAPTER) + local, islamicLessons.size)
        return (0 until 5).map { offset -> islamicLessons[(seed + offset * 3) % islamicLessons.size] }
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
