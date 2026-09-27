package com.learnova.app

data class SmartLesson(
    val id: String,
    val domain: String,
    val display: String,
    val spokenName: String,
    val sound: String,
    val example: String,
    val visualKey: String,
    val prompt: String,
    val rtl: Boolean = false
)

object SmartLearningEngine {
    private val english = listOf(
        "A" to "Apple", "B" to "Ball", "C" to "Cat", "D" to "Dog",
        "E" to "Elephant", "F" to "Fish", "G" to "Grapes", "H" to "House",
        "I" to "Ice cream", "J" to "Juice", "K" to "Kite", "L" to "Lion",
        "M" to "Moon", "N" to "Nest", "O" to "Orange", "P" to "Parrot",
        "Q" to "Queen", "R" to "Rabbit", "S" to "Sun", "T" to "Tiger",
        "U" to "Umbrella", "V" to "Van", "W" to "Whale", "X" to "Xylophone",
        "Y" to "Yak", "Z" to "Zebra"
    ).map { pair ->
        SmartLesson(
            id = "en_" + pair.first.lowercase(),
            domain = "English",
            display = pair.first,
            spokenName = "Letter " + pair.first,
            sound = pair.first,
            example = pair.second,
            visualKey = pair.second.lowercase().replace(" ", "_"),
            prompt = "See " + pair.first + ". Hear it. Find " + pair.second + "."
        )
    }

    private val arabicData = arrayOf(
        arrayOf("ا","ألف","aa","أسد","lion","alif"),
        arrayOf("ب","باء","b","باب","door","baa"),
        arrayOf("ت","تاء","t","تفاح","apple","taa"),
        arrayOf("ث","ثاء","th","ثعلب","fox","thaa"),
        arrayOf("ج","جيم","j","جمل","camel","jeem"),
        arrayOf("ح","حاء","ḥ","حوت","whale","haa"),
        arrayOf("خ","خاء","kh","خبز","bread","khaa"),
        arrayOf("د","دال","d","دب","bear","daal"),
        arrayOf("ذ","ذال","dh","ذهب","gold","dhaal"),
        arrayOf("ر","راء","r","رمان","pomegranate","raa"),
        arrayOf("ز","زاي","z","زرافة","giraffe","zaay"),
        arrayOf("س","سين","s","سمك","fish","seen"),
        arrayOf("ش","شين","sh","شمس","sun","sheen"),
        arrayOf("ص","صاد","ṣ","صقر","falcon","saad"),
        arrayOf("ض","ضاد","ḍ","ضفدع","frog","daad"),
        arrayOf("ط","طاء","ṭ","طائر","bird","taa_emphatic"),
        arrayOf("ظ","ظاء","ẓ","ظبي","gazelle","thaa_emphatic"),
        arrayOf("ع","عين","ʿ","عين","eye","ayn"),
        arrayOf("غ","غين","gh","غزال","gazelle","ghayn"),
        arrayOf("ف","فاء","f","فيل","elephant","faa"),
        arrayOf("ق","قاف","q","قمر","moon","qaaf"),
        arrayOf("ك","كاف","k","كتاب","book","kaaf"),
        arrayOf("ل","لام","l","ليمون","lemon","laam"),
        arrayOf("م","ميم","m","موز","banana","meem"),
        arrayOf("ن","نون","n","نمر","tiger","noon"),
        arrayOf("ه","هاء","h","هلال","crescent","haa"),
        arrayOf("و","واو","w","وردة","flower","waaw"),
        arrayOf("ي","ياء","y","يد","hand","yaa")
    )

    private val arabic = arabicData.mapIndexed { index, d ->
        SmartLesson(
            id = "ar_" + (index + 1),
            domain = "Arabic",
            display = d[0],
            spokenName = d[1],
            sound = d[2],
            example = d[3],
            visualKey = d[5],
            prompt = d[0] + " — " + d[1] + " — " + d[3],
            rtl = true
        )
    }

    private val quran = listOf(
        quranLesson("quran_fatiha", "الفاتحة", "سورة الفاتحة", 7, "ফাতিহা", "শুনো, অনুসরণ করো, তারপর পরিচিত আয়াত/শব্দ চিনে নাও।"),
        quranLesson("quran_ikhlas", "الإخلاص", "سورة الإخلاص", 4, "ইখলাস", "শোনো → শব্দ/আয়াতের অংশ চিনে নাও → ক্রম মিলাও।"),
        quranLesson("quran_falaq", "الفلق", "سورة الفلق", 5, "ফালাক", "শোনো → সঠিক অংশ বেছে নাও → ধীরে পুনরাবৃত্তি করো।"),
        quranLesson("quran_nas", "الناس", "سورة الناس", 6, "নাস", "শোনো → সঠিক শব্দ/অংশ খুঁজে নাও → শেষে ছোট রিভিউ।"),
        quranLesson("quran_kawthar", "الكوثر", "سورة الكوثر", 3, "কাওসার", "শুনে অনুসরণ করো → আয়াতের ক্রম মিলাও → রিভিউ করো।"),
        quranLesson("quran_asr", "العصر", "سورة العصر", 3, "আসর", "শোনো → ক্রম চিনে নাও → শেষে অর্থের সহজ ধারণা দেখো।")
    )

    private fun quranLesson(
        id: String,
        display: String,
        spokenName: String,
        ayahCount: Int,
        banglaName: String,
        activity: String
    ) = SmartLesson(
        id = id,
        domain = "Quran",
        display = display,
        spokenName = spokenName,
        sound = "استماع وترديد",
        example = "$banglaName • $ayahCount আয়াত",
        visualKey = id,
        prompt = activity,
        rtl = true
    )

    val lessons: List<SmartLesson> = english + arabic + quran

    fun lesson(index: Int): SmartLesson = lessons[index.coerceIn(0, lessons.lastIndex)]

    fun lessonForDisplay(value: String): SmartLesson? =
        lessons.firstOrNull { it.display == value }

    fun category(index: Int): String = lesson(index).domain

    fun visualInstruction(index: Int): String = lesson(index).prompt

    fun reinforcement(index: Int, stage: Int): String {
        val l = lesson(index)
        if (l.domain == "Quran") {
            return QuranLearningEngine.activity(l.id, stage)
        }
        return when (stage.coerceIn(0, 2)) {
            0 -> "Look: " + l.display
            1 -> "Listen: " + l.spokenName
            else -> "Connect: " + l.example
        }
    }
}
