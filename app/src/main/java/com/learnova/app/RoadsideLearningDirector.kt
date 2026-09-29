package com.learnova.app

/**
 * Child-first roadside learning director.
 *
 * A journey contains one calm learning stop. The stop is presented as a
 * roadside sign rather than a white information card. The same system can
 * scale to thousands of levels without storing thousands of scenes.
 */
internal class RoadsideLearningDirector {

    data class SignLesson(
        val key: String,
        val english: String,
        val bangla: String,
        val arabic: String,
        val prompt: String,
        val accepted: Set<String>
    )

    private val lessons = listOf(
        SignLesson("fox","FOX","শিয়াল","ثعلب","Say: fox",setOf("fox","ফক্স")),
        SignLesson("bear","BEAR","ভাল্লুক","دب","Say: bear",setOf("bear","বিয়ার")),
        SignLesson("monkey","MONKEY","বানর","قرد","Say: monkey",setOf("monkey","মাঙ্কি")),
        SignLesson("book","BOOK","বই","كتاب","Say: book",setOf("book","বুক")),
        SignLesson("cat","CAT","বিড়াল","قطة","Say: cat",setOf("cat","ক্যাট")),
        SignLesson("dog","DOG","কুকুর","كلب","Say: dog",setOf("dog","ডগ")),
        SignLesson("elephant","ELEPHANT","হাতি","فيل","Say: elephant",setOf("elephant","এলিফ্যান্ট")),
        SignLesson("lion","LION","সিংহ","أسد","Say: lion",setOf("lion","লায়ন")),
        SignLesson("rabbit","RABBIT","খরগোশ","أرنب","Say: rabbit",setOf("rabbit","র‍্যাবিট")),
        SignLesson("fish","FISH","মাছ","سمكة","Say: fish",setOf("fish","ফিশ")),
        SignLesson("tree","TREE","গাছ","شجرة","Say: tree",setOf("tree","ট্রি")),
        SignLesson("sun","SUN","সূর্য","شمس","Say: sun",setOf("sun","সান")),
        SignLesson("moon","MOON","চাঁদ","قمر","Say: moon",setOf("moon","মুন")),
        SignLesson("car","CAR","গাড়ি","سيارة","Say: car",setOf("car","কার"))
    )

    private var lastLevel = -1
    private var shown = false

    fun lessonForLevel(level: Int): SignLesson = lessons[(level - 1).mod(lessons.size)]

    /**
     * Returns a sign exactly once per level, around the middle of the journey.
     * The threshold moves slightly between levels so the world does not feel
     * mechanically identical.
     */
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
