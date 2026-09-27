package com.learnova.app

/**
 * Variety-first Quran learning flow.
 * Activities change between listening, recognition, ordering and review.
 */
object QuranLearningEngine {
    private val surahIds = listOf(
        "quran_fatiha", "quran_ikhlas", "quran_falaq", "quran_nas",
        "quran_kawthar", "quran_asr", "quran_nasr", "quran_kafirun",
        "quran_masad", "quran_quraish", "quran_fil", "quran_maun",
        "quran_humazah", "quran_takathur", "quran_qariah", "quran_adiyat",
        "quran_zalzalah", "quran_bayyinah", "quran_qadr", "quran_tin",
        "quran_sharh", "quran_duha"
    )

    private val activitySets = arrayOf(
        arrayOf("শুনো • অনুসরণ করো", "সঠিক অংশ খুঁজে নাও", "আজকের অংশ আবার শুনে নাও"),
        arrayOf("শুনো • ছন্দ লক্ষ্য করো", "আয়াতের ক্রম মিলাও", "ছোট রিভিউ করো"),
        arrayOf("শুনো • মন দিয়ে অনুসরণ করো", "সঠিক অংশ বেছে নাও", "রিভিউ রাউন্ড"),
        arrayOf("শুনো • শব্দ চিনে নাও", "মিল খুঁজে নাও", "শেখা অংশ ঝালিয়ে নাও")
    )

    fun activity(id: String, stage: Int): String {
        val index = surahIds.indexOf(id).let { if (it < 0) 0 else it }
        val set = activitySets[index % activitySets.size]
        return set[stage.coerceIn(0, set.lastIndex)]
    }

    fun nextSurahId(id: String): String? {
        val i = surahIds.indexOf(id)
        return if (i >= 0 && i + 1 < surahIds.size) surahIds[i + 1] else null
    }
}
