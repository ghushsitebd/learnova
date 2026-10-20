package com.learnova.app

/**
 * Animals are friendly exam partners, not school subjects.
 * They ask children questions only about Quran and Islamic learning.
 * Quran verses/recitations must be supplied by the verified Quran catalog;
 * this file uses short concept questions and does not fabricate ayat.
 */
internal object AnimalIslamicExamDirector {

    data class Challenge(
        val level: Int,
        val animal: String,
        val topic: String,
        val prompt: String,
        val choices: List<String>,
        val correctAnswer: String,
        val successMessage: String,
        val retryMessage: String
    )

    private data class Question(
        val topic: String,
        val prompt: String,
        val choices: List<String>,
        val answerIndex: Int
    )

    private val animals = listOf(
        "Lion", "Fox", "Bear", "Monkey", "Elephant",
        "Deer", "Rabbit", "Horse", "Tiger", "Giraffe",
        "Parrot", "Owl", "Camel", "Dolphin", "Turtle"
    )

    private val questions = listOf(
        Question("Quran", "কুরআন মুসলিমদের কী?", listOf("আল্লাহর বাণী", "একটি খেলনা", "একটি গাড়ি"), 0),
        Question("Surah Al-Fatiha", "কোন সূরা দিয়ে কুরআন শুরু হয়?", listOf("সূরা আন-নাস", "সূরা আল-ফাতিহা", "সূরা আল-ফালাক"), 1),
        Question("Bismillah", "কোন কথাটি আল্লাহর নামে শুরু করতে বলা হয়?", listOf("বিসমিল্লাহ", "শুভরাত্রি", "বিদায়"), 0),
        Question("Alhamdulillah", "কৃতজ্ঞতা প্রকাশের একটি ইসলামি বাক্য কোনটি?", listOf("আলহামদুলিল্লাহ", "আবার দেখা হবে", "শুভ সকাল"), 0),
        Question("Salam", "মুসলিমরা দেখা হলে কোন সালাম বলেন?", listOf("আসসালামু আলাইকুম", "শুভ রাত্রি", "স্বাগতম বন্ধু"), 0),
        Question("Wudu", "নামাজের আগে সাধারণত কী করতে হয়?", listOf("অজু", "দৌড়", "ঘুম"), 0),
        Question("Salah", "সালাত/নামাজ কী?", listOf("ইবাদত", "খেলা", "ভ্রমণ"), 0),
        Question("Masjid", "মসজিদে কীসের আদব রক্ষা করতে হয়?", listOf("শান্তি ও সম্মান", "চিৎকার", "দৌড়াদৌড়ি"), 0),
        Question("Truthfulness", "ইসলামে কোন আচরণটি ভালো?", listOf("সত্য বলা", "মিথ্যা বলা", "প্রতারণা"), 0),
        Question("Parents", "মা-বাবার সঙ্গে কেমন আচরণ করা উচিত?", listOf("সম্মান ও সদাচরণ", "রূঢ় ব্যবহার", "উপেক্ষা"), 0),
        Question("Cleanliness", "পরিচ্ছন্নতা সম্পর্কে কোনটি ভালো অভ্যাস?", listOf("নিজেকে ও স্থান পরিষ্কার রাখা", "আবর্জনা ফেলা", "ময়লা ছড়ানো"), 0),
        Question("Sadaqah", "সদকার একটি উদাহরণ কোনটি?", listOf("সাধ্যমতো সাহায্য করা", "অন্যকে কষ্ট দেওয়া", "জিনিস নষ্ট করা"), 0),
        Question("Dhikr", "আল্লাহকে স্মরণ করাকে কী বলা হয়?", listOf("যিকির", "বাজার", "যাত্রা"), 0),
        Question("Astaghfirullah", "ভুল হলে আল্লাহর কাছে কী চাইতে পারি?", listOf("ক্ষমা", "আরও ভুল", "ঝগড়া"), 0),
        Question("Tawheed", "তাওহিদের মূল শিক্ষা কী?", listOf("আল্লাহ এক", "অনেক উপাস্য", "কোনো উপাস্য নেই"), 0),
        Question("Ramadan", "রমজান মাসে মুসলিমরা কোন ইবাদত পালন করেন?", listOf("রোজা", "শুধু ভ্রমণ", "শুধু খেলাধুলা"), 0),
        Question("Zakah", "যাকাতের সঙ্গে কোন ধারণাটি জড়িত?", listOf("নির্ধারিত দান", "অপচয়", "প্রতারণা"), 0),
        Question("Halal", "খাবার সম্পর্কে সন্দেহ হলে শিশুর কী করা উচিত?", listOf("বিশ্বস্ত বড়দের জিজ্ঞেস করা", "না জেনে সিদ্ধান্ত চাপানো", "অন্যকে ঠাট্টা করা"), 0),
        Question("Prophet Muhammad ﷺ", "মুসলিমরা শেষ নবী মুহাম্মদ ﷺ-কে কীভাবে স্মরণ করেন?", listOf("সম্মান ও দরুদসহ", "অবজ্ঞা করে", "উপহাস করে"), 0),
        Question("Dua", "দোয়া কাকে করা হয়?", listOf("আল্লাহর কাছে", "গাড়ির কাছে", "খেলনার কাছে"), 0)
    )

    fun challengeForLevel(level: Int): Challenge {
        val safeLevel = level.coerceIn(1, 500)
        val question = questions[(safeLevel - 1) % questions.size]
        val animal = animals[Math.floorMod((safeLevel - 1) * 7, animals.size)]
        val choices = question.choices
        return Challenge(
            level = safeLevel,
            animal = animal,
            topic = question.topic,
            prompt = question.prompt,
            choices = choices,
            correctAnswer = choices[question.answerIndex],
            successMessage = "মাশাআল্লাহ! $animal তোমাকে অভিনন্দন জানাচ্ছে।",
            retryMessage = "$animal বলছে: আবার চেষ্টা করো—ধীরে ধীরে শিখে নেব!"
        )
    }

    fun validateAllLevels(): Boolean {
        for (level in 1..500) {
            val challenge = challengeForLevel(level)
            if (challenge.level != level) return false
            if (challenge.animal.isBlank() || challenge.topic.isBlank() || challenge.prompt.isBlank()) return false
            if (challenge.choices.size != 3 || challenge.choices.distinct().size != 3) return false
            if (challenge.correctAnswer !in challenge.choices) return false
            if (challenge.successMessage.isBlank() || challenge.retryMessage.isBlank()) return false
        }
        return true
    }
}
