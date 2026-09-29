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
        SignLesson("fox", "FOX", "fox", "শিয়াল", "ثعلب", "Say: fox", setOf("fox", "ফক্স", "ثعلب")),
        SignLesson("bear", "BEAR", "bear", "ভাল্লুক", "دب", "Say: bear", setOf("bear", "বিয়ার", "دب")),
        SignLesson("monkey", "MONKEY", "monkey", "বানর", "قرد", "Say: monkey", setOf("monkey", "মাঙ্কি", "قرد")),
        SignLesson("book", "BOOK", null, "বই", "كتاب", "Say: book", setOf("book", "বুক", "كتاب")),
        SignLesson("cat", "CAT", "cat", "বিড়াল", "قطة", "Say: cat", setOf("cat", "ক্যাট", "قطة")),
        SignLesson("dog", "DOG", "dog", "কুকুর", "كلب", "Say: dog", setOf("dog", "ডগ", "كلب")),
        SignLesson("elephant", "ELEPHANT", "elephant", "হাতি", "فيل", "Say: elephant", setOf("elephant", "এলিফ্যান্ট", "فيل")),
        SignLesson("lion", "LION", "lion", "সিংহ", "أسد", "Say: lion", setOf("lion", "লায়ন", "أسد")),
        SignLesson("rabbit", "RABBIT", "rabbit", "খরগোশ", "أرنب", "Say: rabbit", setOf("rabbit", "র‍্যাবিট", "أرنب")),
        SignLesson("fish", "FISH", "fish", "মাছ", "سمكة", "Say: fish", setOf("fish", "ফিশ", "سمكة")),
        SignLesson("tree", "TREE", null, "গাছ", "شجرة", "Say: tree", setOf("tree", "ট্রি", "شجرة")),
        SignLesson("sun", "SUN", null, "সূর্য", "شمس", "Say: sun", setOf("sun", "সান", "شمس")),
        SignLesson("moon", "MOON", null, "চাঁদ", "قمر", "Say: moon", setOf("moon", "মুন", "قمر")),
        SignLesson("car", "CAR", null, "গাড়ি", "سيارة", "Say: car", setOf("car", "কার", "سيارة"))
    )

    private var lastLevel = -1
    private var shown = false

    fun lessonForLevel(level: Int): SignLesson = lessons[(level - 1).mod(lessons.size)]

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
        val normalized = heard.trim().lowercase()
            .replace(Regex("""[^\p{L}\p{N} ]"""), "")
            .replace(Regex("""\s+"""), " ")
        return sign.accepted.any { normalized.contains(it.lowercase()) }
    }
}
