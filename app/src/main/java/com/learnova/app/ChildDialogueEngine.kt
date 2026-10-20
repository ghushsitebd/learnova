package com.learnova.app

data class ChildDialogue(
    val id: String,
    val speaker: String,
    val assetPath: String,
    val locale: String = "bn-BD",
    val transcript: String
)

/**
 * A scripted, deterministic dialogue library for passive, voice-first learning.
 * The transcripts are for TTS/accessibility and asset authoring; the game should
 * play the two speakers in sequence without asking the child to press anything.
 */
data class AnimalDialogueTurn(
    val id: String,
    val speakerAnimal: String,
    val listenerAnimal: String,
    val locale: String,
    val transcript: String,
    val audioAssetPath: String
)

data class AnimalDialogueScene(
    val id: String,
    val firstAnimal: String,
    val secondAnimal: String,
    val turns: List<AnimalDialogueTurn>,
    val visualAnimalKey: String
)

object ChildDialogueEngine {
    val salam = listOf(
        ChildDialogue("salam_greeting_01", "child_a", "voice/children/bn/salam_greeting_01.ogg", transcript = "আসসালামু আলাইকুম!"),
        ChildDialogue("salam_reply_01", "child_b", "voice/children/bn/salam_reply_01.ogg", transcript = "ওয়ালাইকুমুস সালাম!")
    )

    private val animalScenes = listOf(
        AnimalDialogueScene(
            id = "lion_identity",
            firstAnimal = "fox",
            secondAnimal = "lion",
            visualAnimalKey = "lion",
            turns = listOf(
                AnimalDialogueTurn("lion_bn_q", "fox", "lion", "bn-BD", "বন্ধু, তুমি কে?", "voice/animals/bn/lion_question.ogg"),
                AnimalDialogueTurn("lion_bn_a", "lion", "fox", "bn-BD", "আমি সিংহ।", "voice/animals/bn/lion_answer.ogg"),
                AnimalDialogueTurn("lion_ar_q", "fox", "lion", "ar", "مَنْ أَنْتَ؟", "voice/animals/ar/lion_question.ogg"),
                AnimalDialogueTurn("lion_ar_a", "lion", "fox", "ar", "أَنَا أَسَدٌ.", "voice/animals/ar/lion_answer.ogg")
            )
        ),
        AnimalDialogueScene(
            id = "elephant_identity",
            firstAnimal = "rabbit",
            secondAnimal = "elephant",
            visualAnimalKey = "elephant",
            turns = listOf(
                AnimalDialogueTurn("elephant_bn_q", "rabbit", "elephant", "bn-BD", "বন্ধু, তুমি কে?", "voice/animals/bn/elephant_question.ogg"),
                AnimalDialogueTurn("elephant_bn_a", "elephant", "rabbit", "bn-BD", "আমি হাতি।", "voice/animals/bn/elephant_answer.ogg"),
                AnimalDialogueTurn("elephant_ar_q", "rabbit", "elephant", "ar", "مَنْ أَنْتَ؟", "voice/animals/ar/elephant_question.ogg"),
                AnimalDialogueTurn("elephant_ar_a", "elephant", "rabbit", "ar", "أَنَا فِيلٌ.", "voice/animals/ar/elephant_answer.ogg")
            )
        ),
        AnimalDialogueScene(
            id = "cat_identity",
            firstAnimal = "bear",
            secondAnimal = "cat",
            visualAnimalKey = "cat",
            turns = listOf(
                AnimalDialogueTurn("cat_bn_q", "bear", "cat", "bn-BD", "বন্ধু, তুমি কে?", "voice/animals/bn/cat_question.ogg"),
                AnimalDialogueTurn("cat_bn_a", "cat", "bear", "bn-BD", "আমি বিড়াল।", "voice/animals/bn/cat_answer.ogg"),
                AnimalDialogueTurn("cat_ar_q", "bear", "cat", "ar", "مَنْ أَنْتَ؟", "voice/animals/ar/cat_question.ogg"),
                AnimalDialogueTurn("cat_ar_a", "cat", "bear", "ar", "أَنَا قِطٌّ.", "voice/animals/ar/cat_answer.ogg")
            )
        ),
        AnimalDialogueScene(
            id = "bird_identity",
            firstAnimal = "monkey",
            secondAnimal = "bird",
            visualAnimalKey = "bird",
            turns = listOf(
                AnimalDialogueTurn("bird_bn_q", "monkey", "bird", "bn-BD", "বন্ধু, তুমি কে?", "voice/animals/bn/bird_question.ogg"),
                AnimalDialogueTurn("bird_bn_a", "bird", "monkey", "bn-BD", "আমি পাখি।", "voice/animals/bn/bird_answer.ogg"),
                AnimalDialogueTurn("bird_ar_q", "monkey", "bird", "ar", "مَنْ أَنْتَ؟", "voice/animals/ar/bird_question.ogg"),
                AnimalDialogueTurn("bird_ar_a", "bird", "monkey", "ar", "أَنَا طَائِرٌ.", "voice/animals/ar/bird_answer.ogg")
            )
        )
    )

    /** Stable level-to-scene selection; no randomness or child input is required. */
    fun animalScene(level: Int): AnimalDialogueScene =
        animalScenes[Math.floorMod(level.coerceAtLeast(1) - 1, animalScenes.size)]

    /** Return all voice lines in the authored order: Bangla Q/A, then Arabic Q/A. */
    fun animalConversation(level: Int): List<AnimalDialogueTurn> =
        animalScene(level).turns

    fun lesson(lesson: SmartLesson): List<ChildDialogue> = listOf(
        ChildDialogue(
            id = "lesson_${lesson.id}_observe",
            speaker = "child_a",
            assetPath = "voice/children/bn/lesson_${lesson.id}_observe.ogg",
            transcript = "দেখো, ${lesson.display}।"
        ),
        ChildDialogue(
            id = "lesson_${lesson.id}_learn",
            speaker = "child_b",
            assetPath = "voice/children/bn/lesson_${lesson.id}_learn.ogg",
            transcript = when (lesson.domain) {
                "Arabic" -> "${lesson.spokenName}। ${lesson.example}।"
                "English" -> "${lesson.display}। ${lesson.example}।"
                else -> lesson.spokenName
            }
        )
    )
}
