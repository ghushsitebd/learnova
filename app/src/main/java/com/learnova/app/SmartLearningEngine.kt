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
        quranLesson("quran_fatiha", "الفاتحة", "سورة الفاتحة", 7, "ফাতিহা", "শুনো → অনুসরণ করো → পরিচিত অংশ চিনে নাও।"),
        quranLesson("quran_ikhlas", "الإخلاص", "سورة الإخلاص", 4, "ইখলাস", "শুনো → শব্দের ছন্দ লক্ষ্য করো → ক্রম মিলাও।"),
        quranLesson("quran_falaq", "الفلق", "سورة الفلق", 5, "ফালাক", "শুনো → সঠিক অংশ বেছে নাও → ধীরে পুনরাবৃত্তি করো।"),
        quranLesson("quran_nas", "الناس", "سورة الناس", 6, "নাস", "শুনো → সঠিক অংশ চিনে নাও → ছোট রিভিউ।"),
        quranLesson("quran_kawthar", "الكوثر", "سورة الكوثر", 3, "কাওসার", "শুনে অনুসরণ করো → আয়াতের ক্রম মিলাও → রিভিউ।"),
        quranLesson("quran_asr", "العصر", "سورة العصر", 3, "আসর", "শুনো → ক্রম চিনে নাও → সহজ অর্থ-ধারণা রিভিউ।"),
        quranLesson("quran_nasr", "النصر", "سورة النصر", 3, "নাসর", "শুনো → সঠিক আয়াত বেছে নাও → শেষে পুনরাবৃত্তি।"),
        quranLesson("quran_kafirun", "الكافرون", "سورة الكافرون", 6, "কাফিরুন", "শুনো → মিল খুঁজে নাও → আয়াতের ক্রম সাজাও।"),
        quranLesson("quran_masad", "المسد", "سورة المسد", 5, "মাসাদ", "শুনো → অনুসরণ করো → সঠিক অংশ চিনে নাও।"),
        quranLesson("quran_quraish", "قريش", "سورة قريش", 4, "কুরাইশ", "শুনো → জোড়া মিলাও → ছোট রিভিউ।"),
        quranLesson("quran_fil", "الفيل", "سورة الفيل", 5, "ফীল", "শুনো → আয়াতের ক্রম মিলাও → রিভিউ রাউন্ড।"),
        quranLesson("quran_maun", "الماعون", "سورة الماعون", 7, "মাউন", "শুনো → সঠিক অংশ খুঁজে নাও → শেখা যাচাই করো।"),
        quranLesson("quran_humazah", "الهمزة", "سورة الهمزة", 9, "হুমাযাহ", "শুনো → অংশ চিনে নাও → ক্রম মিলাও → রিভিউ।"),
        quranLesson("quran_takathur", "التكاثر", "سورة التكاثر", 8, "তাকাসুর", "শুনো → মিল খুঁজে নাও → আয়াতের পথ সম্পূর্ণ করো।"),
        quranLesson("quran_qariah", "القارعة", "سورة القارعة", 11, "কারিআহ", "শুনো → সঠিক অংশ বেছে নাও → ক্রম সাজাও।"),
        quranLesson("quran_adiyat", "العاديات", "سورة العاديات", 11, "আদিয়াত", "শুনো → অনুসরণ করো → পরিচিত অংশ খুঁজে নাও।"),
        quranLesson("quran_zalzalah", "الزلزلة", "سورة الزلزلة", 8, "যিলযাল", "শুনো → আয়াতের ক্রম মিলাও → ছোট রিভিউ।"),
        quranLesson("quran_bayyinah", "البينة", "سورة البينة", 8, "বাইয়্যিনাহ", "শুনো → অংশ চিনে নাও → সহজ matching activity।"),
        quranLesson("quran_qadr", "القدر", "سورة القدر", 5, "কদর", "শুনো → সঠিক অংশ বেছে নাও → পুনরাবৃত্তি।"),
        quranLesson("quran_tin", "التين", "سورة التين", 8, "তীন", "শুনো → ক্রম মিলাও → শেখা রিভিউ করো।"),
        quranLesson("quran_sharh", "الشرح", "سورة الشرح", 8, "শরহ", "শুনো → অনুসরণ করো → সঠিক অংশ চিনে নাও।"),
        quranLesson("quran_duha", "الضحى", "سورة الضحى", 11, "দুহা", "শুনো → অংশ মিলাও → শেষে রিভিউ করো।")
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
