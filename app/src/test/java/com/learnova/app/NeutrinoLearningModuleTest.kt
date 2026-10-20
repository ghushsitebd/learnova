package com.learnova.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeutrinoLearningModuleTest {
    @Test fun missionHasThreeScienceCardsInAllSupportedLanguages() {
        for (language in NeutrinoLearningModule.Language.values()) {
            val mission = NeutrinoLearningModule.mission(language)
            assertEquals(3, mission.cards.size)
            assertTrue(mission.title.isNotBlank())
            assertTrue(mission.completionMessage.isNotBlank())
            assertTrue(mission.cards.all { it.title.isNotBlank() && it.explanation.isNotBlank() && it.activity.isNotBlank() })
        }
    }

    @Test fun quizAcceptsOnlyTheCorrectAnswerIndex() {
        for (language in NeutrinoLearningModule.Language.values()) {
            val question = NeutrinoLearningModule.mission(language).question
            assertTrue(NeutrinoLearningModule.isValidAnswer(question, question.correctAnswerIndex))
            assertFalse(NeutrinoLearningModule.isValidAnswer(question, -1))
            assertFalse(NeutrinoLearningModule.isValidAnswer(question, question.answers.size))
            assertFalse(NeutrinoLearningModule.isValidAnswer(question, (question.correctAnswerIndex + 1) % question.answers.size))
        }
    }

    @Test fun simulatedDetectorOutcomeIsBoundedAndClearlyToyModel() {
        assertEquals("SIMULATED_RARE_INTERACTION", NeutrinoLearningModule.simulatedDetectorOutcome(0))
        assertEquals("PASSED_THROUGH", NeutrinoLearningModule.simulatedDetectorOutcome(1))
        assertEquals("PASSED_THROUGH", NeutrinoLearningModule.simulatedDetectorOutcome(999))
        assertEquals("SIMULATED_RARE_INTERACTION", NeutrinoLearningModule.simulatedDetectorOutcome(-10))
        assertEquals("PASSED_THROUGH", NeutrinoLearningModule.simulatedDetectorOutcome(5000))
    }
}
