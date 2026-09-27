package com.learnova.app

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Lightweight offline-first voice helper.
 * Uses the phone's installed TTS engine, so no audio files are bundled.
 */
class LearnovaVoice(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pendingText: String? = null
    private var pendingLocale: Locale = Locale.US

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        ready = true
        tts.setSpeechRate(0.72f)
        // Child-friendly delivery: slightly brighter pitch and slower pacing.
        // The actual voice remains the device TTS voice; no bundled voice engine is required.
        tts.setPitch(1.18f)
        
        pendingText?.let {
            speak(it, pendingLocale)
            pendingText = null
        }
    }

    fun speakEnglishLesson(letter: String, word: String) {
        speak("Letter $letter. $word.", Locale.US)
    }

    fun speakLesson(lesson: String) {
        SmartLearningEngine.lessonForDisplay(lesson)?.let { smart ->
            speakSmartLesson(smart)
            return
        }
        speak(lesson, if (lesson.any { it in '\u0600'..'\u06FF' }) Locale("ar") else Locale.US)
    }

    fun speakSmartLesson(lesson: SmartLesson) {
        val spoken = when (lesson.domain) {
            "English" -> "Letter " + lesson.display + ". " + lesson.example + "."
            "Arabic" -> lesson.display + ". " + lesson.spokenName + ". " + lesson.example + "."
            "Quran" -> lesson.spokenName + ". " + lesson.sound + "."
            else -> lesson.display + ". " + lesson.example + "."
        }
        val locale = if (lesson.rtl) Locale("ar") else Locale.US
        speak(spoken, locale)
    }

    fun speakInstruction(running: Boolean) {
        speak(if (running) "Tap to stop." else "Tap to drive.", Locale.US)
    }

    fun speakVehicle(name: String) {
        speak(name, Locale.US)
    }

    private fun speak(value: String, locale: Locale) {
        if (!ready) {
            pendingText = value
            pendingLocale = locale
            return
        }
        val availability = tts.isLanguageAvailable(locale)
        if (availability < TextToSpeech.LANG_AVAILABLE) return
        tts.language = locale
        tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, "learnova-lesson")
    }

    fun stop() {
        if (ready) tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
