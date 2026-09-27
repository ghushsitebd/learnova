package com.learnova.app

data class ChildDialogue(
    val id: String,
    val speaker: String,
    val assetPath: String,
    val locale: String = "bn-BD",
    val transcript: String
)

object ChildDialogueEngine {
    val salam = listOf(
        ChildDialogue("salam_greeting_01", "child_a", "voice/children/bn/salam_greeting_01.ogg", transcript = "আসসালামু আলাইকুম!"),
        ChildDialogue("salam_reply_01", "child_b", "voice/children/bn/salam_reply_01.ogg", transcript = "ওয়ালাইকুমুস সালাম!")
    )

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
