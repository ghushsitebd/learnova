package com.learnova.app

/**
 * Child-first roadside learning director.
 *
 * The 500 levels are content addresses, not 500 duplicated scenes. This director
 * resolves every level/activity through LevelContentDirector so the sign, voice,
 * multilingual vocabulary and near-field creature stay on the same curriculum key.
 */
internal class RoadsideLearningDirector {

    data class SignLesson(
        val key: String,
        val english: String,
        val visualKey: String?,
        val bangla: String,
        val arabic: String,
        val prompt: String,
        val accepted: Set<String>
    )

    private val fallback = SignLesson(
        "deer", "DEER", "deer", "হরিণ", "غزال", "Say: deer",
        setOf("deer", "ডিয়ার", "হরিণ", "غزال")
    )

    // Only these species are promoted to the authored near-field GLB path.
    // Other vocabulary remains valid learning content but uses the lightweight
    // presentation until its production asset is verified and packaged.
    private val verifiedVisuals = setOf(
        "deer", "fox", "horse", "wolf", "cow", "boar", "rabbit", "stag", "lion"
    )

    private val translations = mapOf(
        "deer" to Triple("Deer", "হরিণ", "غزال"),
        "fox" to Triple("Fox", "শিয়াল", "ثعلب"),
        "horse" to Triple("Horse", "ঘোড়া", "حصان"),
        "wolf" to Triple("Wolf", "নেকড়ে", "ذئب"),
        "cow" to Triple("Cow", "গরু", "بقرة"),
        "boar" to Triple("Boar", "বুনো শূকর", "خنزير بري"),
        "rabbit" to Triple("Rabbit", "খরগোশ", "أرنب"),
        "stag" to Triple("Stag", "পুরুষ হরিণ", "أيل"),
        "lion" to Triple("Lion", "সিংহ", "أسد"),
        "bear" to Triple("Bear", "ভাল্লুক", "دب"),
        "monkey" to Triple("Monkey", "বানর", "قرد"),
        "tiger" to Triple("Tiger", "বাঘ", "نمر"),
        "parrot" to Triple("Parrot", "টিয়া", "ببغاء"),
        "camel" to Triple("Camel", "উট", "جمل"),
        "giraffe" to Triple("Giraffe", "জিরাফ", "زرافة"),
        "zebra" to Triple("Zebra", "জেব্রা", "حمار وحشي"),
        "gazelle" to Triple("Gazelle", "গ্যাজেল", "غزال"),
        "falcon" to Triple("Falcon", "বাজ", "صقر"),
        "cat" to Triple("Cat", "বিড়াল", "قطة"),
        "dog" to Triple("Dog", "কুকুর", "كلب"),
        "elephant" to Triple("Elephant", "হাতি", "فيل"),
        "fish" to Triple("Fish", "মাছ", "سمكة"),
        "tree" to Triple("Tree", "গাছ", "شجرة"),
        "sun" to Triple("Sun", "সূর্য", "شمس"),
        "moon" to Triple("Moon", "চাঁদ", "قمر"),
        "car" to Triple("Car", "গাড়ি", "سيارة")
    )

    private var lastLevel = -1
    private var shown = false

    fun lessonForLevel(level: Int): SignLesson = lessonFromContent(LevelContentDirector.profile(level).lessons.firstOrNull())

    fun lessonForLearningSession(level: Int, activityIndex: Int): SignLesson {
        val profile = LevelContentDirector.profile(level)
        val lesson = profile.lessons[activityIndex.mod(profile.lessons.size)]
        return lessonFromContent(lesson)
    }

    fun maybeStop(level: Int, progress: Float): SignLesson? {
        if (level != lastLevel) {
            lastLevel = level
            shown = false
        }
        if (shown) return null
        val threshold = 0.34f + ((level * 17) % 28) / 100f
        if (progress >= threshold) {
            shown = true
            return lessonForLevel(level)
        }
        return null
    }

    fun resetForLevel(level: Int) {
        lastLevel = level
        shown = false
    }

    fun matches(sign: SignLesson, heard: String): Boolean {
        val normalized = normalizeForMatch(heard)
        return sign.accepted.any {
            normalizeForMatch(it) == normalized || normalized.contains(normalizeForMatch(it))
        }
    }

    private fun lessonFromContent(content: LevelContentDirector.LessonContent?): SignLesson {
        if (content == null) return fallback
        val key = content.english.trim().lowercase()
        val words = translations[key] ?: Triple(content.english, content.bangla, content.arabic)
        val visual = key.takeIf { it in verifiedVisuals }
        val accepted = setOf(
            content.english, content.bangla, content.arabic,
            words.first, words.second, words.third
        ).filter { it.isNotBlank() }.toSet()
        return SignLesson(
            key = key,
            english = words.first.uppercase(),
            visualKey = visual,
            bangla = words.second,
            arabic = words.third,
            prompt = content.prompt,
            accepted = accepted
        )
    }

    private fun normalizeForMatch(value: String): String {
        return value.trim()
            .lowercase()
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .replace(Regex("[^\\p{L}\\p{N} ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
