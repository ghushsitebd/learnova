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
        SmartLesson(
            id = "quran_fatiha",
            domain = "Quran",
            display = "الفاتحة",
            spokenName = "سورة الفاتحة",
            sound = "استماع وترديد",
            example = "سورة الفاتحة",
            visualKey = "quran_fatiha",
            prompt = "Listen, follow, repeat and recognize Surah Al-Fatihah.",
            rtl = true
        )
    )

    val lessons: List<SmartLesson> = english + arabic + quran

    fun lesson(index: Int): SmartLesson = lessons[index.coerceIn(0, lessons.lastIndex)]

    fun lessonForDisplay(value: String): SmartLesson? =
        lessons.firstOrNull { it.display == value }

    fun category(index: Int): String = lesson(index).domain

    fun visualInstruction(index: Int): String = lesson(index).prompt

    fun reinforcement(index: Int, stage: Int): String {
        val l = lesson(index)
        return when (stage.coerceIn(0, 2)) {
            0 -> "Look: " + l.display
            1 -> "Listen: " + l.spokenName
            else -> "Connect: " + l.example
        }
    }
}
