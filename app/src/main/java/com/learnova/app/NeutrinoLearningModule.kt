package com.learnova.app

/**
 * Offline, child-safe neutrino science content for Learnova.
 *
 * This is a learning simulation only: it does not claim to detect real neutrinos
 * or use neutrinos for communication, tracking, or vehicle control.
 */
internal object NeutrinoLearningModule {
    enum class Language { ENGLISH, BANGLA, ARABIC }

    data class LessonCard(
        val title: String,
        val explanation: String,
        val activity: String
    )

    data class QuizQuestion(
        val prompt: String,
        val answers: List<String>,
        val correctAnswerIndex: Int,
        val explanation: String
    )

    data class Mission(
        val title: String,
        val cards: List<LessonCard>,
        val question: QuizQuestion,
        val completionMessage: String
    )

    fun mission(language: Language = Language.ENGLISH): Mission = when (language) {
        Language.ENGLISH -> englishMission()
        Language.BANGLA -> banglaMission()
        Language.ARABIC -> arabicMission()
    }

    /** Deterministic toy model: interaction is rare, never a real detector prediction. */
    fun simulatedDetectorOutcome(interactionRoll: Int): String {
        val roll = interactionRoll.coerceIn(0, 999)
        return if (roll == 0) "SIMULATED_RARE_INTERACTION" else "PASSED_THROUGH"
    }

    fun isValidAnswer(question: QuizQuestion, selectedIndex: Int): Boolean =
        selectedIndex in question.answers.indices && selectedIndex == question.correctAnswerIndex

    private fun englishMission() = Mission(
        title = "Neutrino Explorer",
        cards = listOf(
            LessonCard(
                "Tiny cosmic messenger",
                "A neutrino is a tiny particle with no electric charge. Many neutrinos come from the Sun and pass through Earth.",
                "Tap the glowing trail to follow a neutrino through a pretend Earth."
            ),
            LessonCard(
                "Hard to catch",
                "Neutrinos rarely interact with matter, so scientists use very large, carefully shielded detectors to look for rare interactions.",
                "Watch the detector: most simulated neutrinos pass through; a rare flash represents a possible interaction."
            ),
            LessonCard(
                "Science, not magic",
                "Neutrino detectors can help scientists study the Sun, exploding stars and distant cosmic events. They cannot read a person's thoughts or identify a person.",
                "Collect three science badges: charge, passage and detector."
            )
        ),
        question = QuizQuestion(
            "Why are neutrinos difficult to detect?",
            listOf("They rarely interact with matter", "They are tiny cars", "They send messages into our brains"),
            0,
            "Correct: neutrinos rarely interact with matter. The activity is a simulation, not a real detector."
        ),
        completionMessage = "Mission complete! You explored a real idea from particle physics."
    )

    private fun banglaMission() = Mission(
        title = "নিউট্রিনো অভিযাত্রী",
        cards = listOf(
            LessonCard(
                "ক্ষুদ্র মহাজাগতিক কণা",
                "নিউট্রিনো হলো বৈদ্যুতিক আধানহীন একটি অতিক্ষুদ্র কণা। সূর্য থেকে আসা বহু নিউট্রিনো পৃথিবীর ভেতর দিয়ে চলে যায়।",
                "জ্বলজ্বলে পথটিতে চাপ দিয়ে কল্পিত পৃথিবীর ভেতর নিউট্রিনোর যাত্রা দেখো।"
            ),
            LessonCard(
                "শনাক্ত করা কঠিন",
                "নিউট্রিনো পদার্থের সঙ্গে খুব কমই ক্রিয়া করে। তাই বিজ্ঞানীরা বিরল ক্রিয়া ধরতে বড় ও বিশেষ ডিটেক্টর ব্যবহার করেন।",
                "সিমুলেশনে বেশির ভাগ নিউট্রিনো চলে যায়; বিরল আলোর ঝলক একটি সম্ভাব্য ক্রিয়া বোঝায়।"
            ),
            LessonCard(
                "বিজ্ঞান, জাদু নয়",
                "নিউট্রিনো ডিটেক্টর দিয়ে সূর্য, বিস্ফোরিত নক্ষত্র ও মহাবিশ্ব সম্পর্কে গবেষণা করা যায়। এগুলো মানুষের চিন্তা পড়তে বা কাউকে শনাক্ত করতে পারে না।",
                "আধান, অতিক্রম ও ডিটেক্টর—এই তিনটি বিজ্ঞান ব্যাজ সংগ্রহ করো।"
            )
        ),
        question = QuizQuestion(
            "নিউট্রিনো শনাক্ত করা কঠিন কেন?",
            listOf("পদার্থের সঙ্গে খুব কম ক্রিয়া করে", "এগুলো ছোট গাড়ি", "এগুলো মস্তিষ্কে বার্তা পাঠায়"),
            0,
            "ঠিক উত্তর: নিউট্রিনো পদার্থের সঙ্গে খুব কম ক্রিয়া করে। এটি একটি সিমুলেশন, বাস্তব ডিটেক্টর নয়।"
        ),
        completionMessage = "অভিযান সম্পন্ন! তুমি কণা-পদার্থবিজ্ঞানের একটি বাস্তব ধারণা শিখেছ।"
    )

    private fun arabicMission() = Mission(
        title = "مستكشف النيوترينو",
        cards = listOf(
            LessonCard(
                "جسيم كوني صغير",
                "النيوترينو جسيم صغير لا يحمل شحنة كهربائية. تمر أعداد كبيرة منه عبر الأرض.",
                "اضغط على المسار المضيء لمشاهدة رحلة افتراضية عبر الأرض."
            ),
            LessonCard(
                "صعب الاكتشاف",
                "نادراً ما يتفاعل النيوترينو مع المادة، لذلك يستخدم العلماء كواشف كبيرة ومتخصصة لرصد التفاعلات النادرة.",
                "في المحاكاة تمر معظم الجسيمات؛ وتمثل الومضة تفاعلاً نادراً محتملاً."
            ),
            LessonCard(
                "علم وليس سحراً",
                "تساعد كواشف النيوترينو العلماء على دراسة الشمس والنجوم. وهي لا تقرأ الأفكار ولا تحدد هوية الأشخاص.",
                "اجمع ثلاث شارات: الشحنة والعبور والكاشف."
            )
        ),
        question = QuizQuestion(
            "لماذا يصعب اكتشاف النيوترينو؟",
            listOf("لأنه نادراً ما يتفاعل مع المادة", "لأنه سيارة صغيرة", "لأنه يرسل الأفكار"),
            0,
            "إجابة صحيحة: يتفاعل النيوترينو مع المادة نادراً. هذا نشاط محاكاة وليس كاشفاً حقيقياً."
        ),
        completionMessage = "اكتملت المهمة! لقد استكشفت فكرة حقيقية من فيزياء الجسيمات."
    )
}
