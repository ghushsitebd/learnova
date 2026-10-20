package com.learnova.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimalDialogueDirectorTest {

    @Test
    fun everyLevelHasAutomaticBanglaQuestionArabicAnswerAndBanglaMeaning() {
        assertTrue(AnimalDialogueDirector.validateAllLevels())
        for (level in 1..500) {
            val dialogue = AnimalDialogueDirector.dialogueForLevel(level)
            assertEquals(level, dialogue.level)
            assertEquals(3, dialogue.lines.size)
            assertEquals(AnimalDialogueDirector.SpeechLanguage.BANGLA, dialogue.lines[0].language)
            assertEquals(AnimalDialogueDirector.SpeechLanguage.ARABIC, dialogue.lines[1].language)
            assertEquals(AnimalDialogueDirector.SpeechLanguage.BANGLA, dialogue.lines[2].language)
            assertNotEquals(dialogue.lines[0].animal, dialogue.lines[1].animal)
        }
    }

    @Test
    fun dialogueContainsNoChoiceUiAndDoesNotRequireNetwork() {
        for (level in 1..500) {
            val dialogue = AnimalDialogueDirector.dialogueForLevel(level)
            assertTrue(dialogue.lines.all { it.audioText.isNotBlank() })
            assertTrue(dialogue.lines.none { it.audioText.contains("http://") || it.audioText.contains("https://") })
            assertTrue(dialogue.lines.none { it.audioText.contains("A) ") || it.audioText.contains("B) ") })
        }
    }

    @Test
    fun levelsOutsideRangeAreClamped() {
        assertEquals(1, AnimalDialogueDirector.dialogueForLevel(0).level)
        assertEquals(500, AnimalDialogueDirector.dialogueForLevel(501).level)
    }
}
