package com.learnova.app

/**
 * Audio-first animal-to-animal Islamic learning dialogue.
 *
 * This is a dialogue script model, not a UI: callers should send lines to
 * speech/audio and drive the speaker/listener animation cues. Do not render
 * these transcripts as on-screen questions, captions, buttons, or answer cards.
 * Quran recitation/ayat must come from a separately verified bundled catalog.
 */
internal object AnimalDialogueDirector {

    enum class SpeechLanguage(val localeTag: String) {
        BANGLA("bn-BD"),
        ARABIC("ar")
    }

    enum class AnimationCue {
        ASKING, ANSWERING, EXPLAINING, LISTENING, CELEBRATING
    }

    data class SpokenLine(
        val animal: String,
        val language: SpeechLanguage,
        val audioText: String,
        val animationCue: AnimationCue
    )

    data class Dialogue(
        val level: Int,
        val topic: String,
        val lines: List<SpokenLine>
    )

    private data class Lesson(
        val topic: String,
        val questionBn: String,
        val answerAr: String,
        val explanationBn: String
    )

    private val animals = listOf(
        "Lion", "Fox", "Bear", "Monkey", "Elephant",
        "Deer", "Rabbit", "Horse", "Tiger", "Camel"
    )

    private val lessons = listOf(
        Lesson("Bismillah", "ভালো কাজ শুরু করার আগে আমরা কী বলি?",
            "بِسْمِ اللَّهِ", "এর অর্থ: আল্লাহর নামে শুরু করছি।"),
        Lesson("Alhamdulillah", "আল্লাহর নেয়ামতের জন্য কৃতজ্ঞতা জানাতে কী বলি?",
            "الْحَمْدُ لِلَّهِ", "এর অর্থ: সমস্ত প্রশংসা আল্লাহর।"),
        Lesson("Salam", "মুসলিমরা একে অপরকে কীভাবে সালাম দেন?",
            "السَّلَامُ عَلَيْكُمْ", "এর অর্থ: আপনার ওপর শান্তি বর্ষিত হোক।"),
        Lesson("Salam response", "সালামের উত্তর কীভাবে দেওয়া যায়?",
            "وَعَلَيْكُمُ السَّلَامُ", "এর অর্থ: আপনার ওপরও শান্তি বর্ষিত হোক।"),
        Lesson("Takbir", "আল্লাহর মহত্ত্ব প্রকাশ করতে কী বলি?",
            "اللَّهُ أَكْبَرُ", "এর অর্থ: আল্লাহ সর্বশ্রেষ্ঠ।"),
        Lesson("Tasbih", "আল্লাহর পবিত্রতা ঘোষণা করতে কী বলি?",
            "سُبْحَانَ اللَّهِ", "এর অর্থ: আল্লাহ পবিত্র।"),
        Lesson("Seeking forgiveness", "আল্লাহর কাছে ক্ষমা চাইতে কী বলি?",
            "أَسْتَغْفِرُ اللَّهَ", "এর অর্থ: আমি আল্লাহর কাছে ক্ষমা চাই।"),
        Lesson("Quran", "মুসলিমদের পবিত্র কিতাবের নাম কী?",
            "الْقُرْآنُ الْكَرِيمُ", "এর অর্থ: সম্মানিত কুরআন।"),
        Lesson("Al-Fatiha", "কুরআনের প্রথম সূরার নাম কী?",
            "سُورَةُ الْفَاتِحَةِ", "এর নাম সূরা আল-ফাতিহা।"),
        Lesson("Al-Ikhlas", "قُلْ هُوَ اللَّهُ أَحَدٌ — এটি কোন সূরার শুরু?",
            "سُورَةُ الْإِخْلَاصِ", "এটি সূরা আল-ইখলাসের শুরু।"),
        Lesson("Dua", "আমরা দোয়া কার কাছে করি?",
            "أَدْعُو اللَّهَ", "এর অর্থ: আমি আল্লাহর কাছে দোয়া করি।"),
        Lesson("Wudu", "নামাজের আগে পবিত্রতা অর্জনের জন্য কী করি?",
            "أَتَوَضَّأُ", "এর অর্থ: আমি অজু করি।"),
        Lesson("Salah", "মুসলিমরা নিয়মিত কোন ইবাদত আদায় করেন?",
            "أُصَلِّي الصَّلَاةَ", "এর অর্থ: আমি নামাজ আদায় করি।"),
        Lesson("Masjid", "মুসলিমরা কোথায় জামাতে নামাজ পড়েন?",
            "فِي الْمَسْجِدِ", "এর অর্থ: মসজিদে।"),
        Lesson("Ramadan", "রমজান মাসে মুসলিমরা কোন ইবাদত পালন করেন?",
            "نَصُومُ فِي رَمَضَانَ", "এর অর্থ: আমরা রমজানে রোজা রাখি।"),
        Lesson("Kindness", "অন্যের সঙ্গে কেমন আচরণ করা ভালো?",
            "أُحْسِنُ إِلَى النَّاسِ", "এর অর্থ: আমি মানুষের সঙ্গে ভালো আচরণ করি।"),
        Lesson("Truthfulness", "আমাদের কী বলা উচিত—সত্য না মিথ্যা?",
            "أَقُولُ الْحَقَّ", "এর অর্থ: আমি সত্য কথা বলি।"),
        Lesson("Parents", "মা-বাবার সঙ্গে কেমন আচরণ করব?",
            "أُحْسِنُ إِلَى وَالِدَيَّ", "এর অর্থ: আমি মা-বাবার সঙ্গে সদাচরণ করি।"),
        Lesson("Cleanliness", "নিজেকে ও নিজের জায়গা কেমন রাখব?",
            "أُحَافِظُ عَلَى النَّظَافَةِ", "এর অর্থ: আমি পরিচ্ছন্নতা বজায় রাখি।"),
        Lesson("Tawheed", "ইসলামের মূল বিশ্বাস—আল্লাহ কতজন?",
            "اللَّهُ وَاحِدٌ", "এর অর্থ: আল্লাহ এক।")
    )

    fun dialogueForLevel(level: Int): Dialogue {
        val safeLevel = level.coerceIn(1, 500)
        val lesson = lessons[(safeLevel - 1) % lessons.size]
        val asker = animals[Math.floorMod((safeLevel - 1) * 3, animals.size)]
        val responder = animals[(Math.floorMod((safeLevel - 1) * 3, animals.size) + 1) % animals.size]
        return Dialogue(
            level = safeLevel,
            topic = lesson.topic,
            lines = listOf(
                SpokenLine(asker, SpeechLanguage.BANGLA, lesson.questionBn, AnimationCue.ASKING),
                SpokenLine(responder, SpeechLanguage.ARABIC, lesson.answerAr, AnimationCue.ANSWERING),
                SpokenLine(responder, SpeechLanguage.BANGLA, lesson.explanationBn, AnimationCue.EXPLAINING)
            )
        )
    }

    fun validateAllLevels(): Boolean = (1..500).all { level ->
        val dialogue = dialogueForLevel(level)
        dialogue.level == level &&
            dialogue.lines.size == 3 &&
            dialogue.lines[0].animal != dialogue.lines[1].animal &&
            dialogue.lines[0].language == SpeechLanguage.BANGLA &&
            dialogue.lines[1].language == SpeechLanguage.ARABIC &&
            dialogue.lines[2].language == SpeechLanguage.BANGLA &&
            dialogue.lines.all { it.audioText.isNotBlank() }
    }
}
