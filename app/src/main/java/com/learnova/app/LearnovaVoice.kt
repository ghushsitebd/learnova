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
        tts.setSpeechRate(0.82f)
        tts.setPitch(1.0f)
        pendingText?.let {
            speak(it, pendingLocale)
            pendingText = null
        }
    }

    fun speakLesson(lesson: String) {
        val arabic = lesson.any { it in '\u0600'..'\u06FF' }
        val locale = if (arabic) Locale("ar") else Locale.US
        speak(lesson, locale)
    }

    fun speakInstruction(running: Boolean) {
        speak(
            if (running) "Tap to stop. Tap again to continue."
            else "Tap once to start.",
            Locale.US
        )
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
