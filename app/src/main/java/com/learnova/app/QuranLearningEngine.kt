package com.learnova.app

/**
 * Variety-first Quran learning flow.
 *
 * It intentionally teaches recognition and listening rather than turning
 * memorisation into repetitive tapping. Full recitation/audio can be supplied
 * later as reviewed recordings without changing the lesson architecture.
 */
object QuranLearningEngine {
    private val activities = mapOf(
        "quran_fatiha" to arrayOf(
            "শুনো • অনুসরণ করো",
            "সঠিক অংশ খুঁজে নাও",
            "আজকের অংশ আবার শুনে নাও"
        ),
        "quran_ikhlas" to arrayOf(
            "শুনো • শব্দের ছন্দ লক্ষ্য করো",
            "ক্রম মিলাও",
            "ছোট রিভিউ করো"
        ),
        "quran_falaq" to arrayOf(
            "শুনো • অনুসরণ করো",
            "সঠিক অংশ বেছে নাও",
            "শেষে একবার রিভিউ"
        ),
        "quran_nas" to arrayOf(
            "শুনো • মন দিয়ে অনুসরণ করো",
            "সঠিক শব্দ/অংশ চিনে নাও",
            "রিভিউ রাউন্ড"
        ),
        "quran_kawthar" to arrayOf(
            "শুনো • অনুসরণ করো",
            "আয়াতের ক্রম মিলাও",
            "আজকের শেখা ঝালিয়ে নাও"
        ),
        "quran_asr" to arrayOf(
            "শুনো • অনুসরণ করো",
            "ক্রম চিনে নাও",
            "সহজ অর্থ-ধারণা রিভিউ"
        )
    )

    fun activity(id: String, stage: Int): String {
        val set = activities[id] ?: activities["quran_fatiha"]!!
        return set[stage.coerceIn(0, set.lastIndex)]
    }

    fun nextSurahId(id: String): String? {
        val ids = activities.keys.toList()
        val i = ids.indexOf(id)
        return if (i >= 0 && i + 1 < ids.size) ids[i + 1] else null
    }
}
