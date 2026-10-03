package com.learnova.app

/**
 * Child-first roadside learning director.
 *
 * A journey contains one calm learning stop. The stop is presented as a
 * roadside sign rather than a white information card. The same system can
 * scale to hundreds of levels without storing hundreds of scenes.
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

    private val lessons = listOf(
        SignLesson("fox", "FOX", "fox", "শিয়াল", "ثعلب", "Say: fox", setOf("fox", "ফক্স", "শিয়াল", "ثعلب")),
        SignLesson("deer", "DEER", "deer", "হরিণ", "أيل", "Say: deer", setOf("deer", "ডিয়ার", "হরিণ", "أيل")),
        SignLesson("horse", "HORSE", "horse", "ঘোড়া", "حصان", "Say: horse", setOf("horse", "হর্স", "ঘোড়া", "حصان")),
        SignLesson("wolf", "WOLF", "wolf", "নেকড়ে", "ذئب", "Say: wolf", setOf("wolf", "উলফ", "নেকড়ে", "ذئب")),
        SignLesson("bear", "BEAR", "bear", "ভাল্লুক", "دب", "Say: bear", setOf("bear", "বিয়ার", "ভাল্লুক", "دب")),
        SignLesson("monkey", "MONKEY", "monkey", "বানর", "قرد", "Say: monkey", setOf("monkey", "মাঙ্কি", "বানর", "قرد")),
        SignLesson("tiger", "TIGER", "tiger", "বাঘ", "نمر", "Say: tiger", setOf("tiger", "টাইগার", "বাঘ", "نمر")),
        SignLesson("parrot", "PARROT", "parrot", "টিয়া", "ببغاء", "Say: parrot", setOf("parrot", "প্যারট", "টিয়া", "ببغاء")),
        SignLesson("camel", "CAMEL", "camel", "উট", "جمل", "Say: camel", setOf("camel", "ক্যামেল", "উট", "جمل")),
        SignLesson("giraffe", "GIRAFFE", "giraffe", "জিরাফ", "زرافة", "Say: giraffe", setOf("giraffe", "জিরাফ", "زرافة")),
        SignLesson("zebra", "ZEBRA", "zebra", "জেব্রা", "حمار وحشي", "Say: zebra", setOf("zebra", "জেব্রা", "حمار وحشي")),
        SignLesson("gazelle", "GAZELLE", "gazelle", "গ্যাজেল", "غزال", "Say: gazelle", setOf("gazelle", "গ্যাজেল", "غزال")),
        SignLesson("falcon", "FALCON", "falcon", "বাজ", "صقر", "Say: falcon", setOf("falcon", "ফ্যালকন", "বাজ", "صقر")),
        SignLesson("book", "BOOK", null, "বই", "كتاب", "Say: book", setOf("book", "বুক", "বই", "كتاب")),
        SignLesson("cat", "CAT", "cat", "বিড়াল", "قطة", "Say: cat", setOf("cat", "ক্যাট", "বিড়াল", "قطة")),
        SignLesson("dog", "DOG", "dog", "কুকুর", "كلب", "Say: dog", setOf("dog", "ডগ", "কুকুর", "كلب")),
        SignLesson("elephant", "ELEPHANT", "elephant", "হাতি", "فيل", "Say: elephant", setOf("elephant", "এলিফ্যান্ট", "হাতি", "فيل")),
        SignLesson("lion", "LION", "lion", "সিংহ", "أسد", "Say: lion", setOf("lion", "লায়ন", "সিংহ", "أسد")),
        SignLesson("rabbit", "RABBIT", "rabbit", "খরগোশ", "أرنب", "Say: rabbit", setOf("rabbit", "র‍্যাবিট", "খরগোশ", "أرنب")),
        SignLesson("fish", "FISH", "fish", "মাছ", "سمكة", "Say: fish", setOf("fish", "ফিশ", "মাছ", "سمكة")),
        SignLesson("tree", "TREE", null, "গাছ", "شجرة", "Say: tree", setOf("tree", "ট্রি", "গাছ", "شجرة")),
        SignLesson("sun", "SUN", null, "সূর্য", "شمس", "Say: sun", setOf("sun", "সান", "সূর্য", "شمس")),
        SignLesson("moon", "MOON", null, "চাঁদ", "قمر", "Say: moon", setOf("moon", "মুন", "চাঁদ", "قمر")),
        SignLesson("car", "CAR", null, "গাড়ি", "سيارة", "Say: car", setOf("car", "কার", "গাড়ি", "سيارة"))
    )

    private var lastLevel = -1
    private var shown = false

    fun lessonForLevel(level: Int): SignLesson = lessons[(level - 1).mod(lessons.size)]

    fun lessonForLearningSession(level: Int, activityIndex: Int): SignLesson =
        lessons[(level - 1 + activityIndex * 5).mod(lessons.size)]

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
        return sign.accepted.any { normalizeForMatch(it) == normalized || normalized.contains(normalizeForMatch(it)) }
    }

    private fun normalizeForMatch(value: String): String {
        return value.trim()
            .lowercase()
            // Arabic tashkeel/harakat should not make an otherwise correct answer fail.
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .replace(Regex("""[^\p{L}\p{N} ]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
