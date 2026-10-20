package com.learnova.app

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import android.speech.tts.Voice
import java.util.Locale

/**
 * Voice controller.
 *
 * Child dialogue prefers bundled human-recorded audio assets.
 * If an asset is not bundled yet, a higher-pitched, slower TTS fallback keeps
 * the learning flow audible instead of silently failing.
 */
class LearnovaVoice(private val context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pendingText: String? = null
    private var pendingLocale: Locale = Locale.US
    private var childPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dialogueGeneration = 0
    private var activeAnimalDialogue: AnimalDialogueDirector.Dialogue? = null
    private var animalDialogueIndex = 0
    private var pendingAnimalDialogue: AnimalDialogueDirector.Dialogue? = null
    private var pendingAnimalLineCallback: ((AnimalDialogueDirector.SpokenLine) -> Unit)? = null
    private var activeAnimalLineCallback: ((AnimalDialogueDirector.SpokenLine) -> Unit)? = null
    private var animalDialogueCompletion: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        ready = true
        tts.setSpeechRate(0.72f)
        tts.setPitch(1.18f)
        pendingText?.let {
            speak(it, pendingLocale)
            pendingText = null
        }
    }


    /**
     * Plays an automatic animal-to-animal dialogue. Transcripts are speech-only:
     * callers must not render question text, answer options, or captions on screen.
     * The callback can drive speaker/listener animation cues in the 3D scene.
     */
    fun playAnimalDialogue(
        dialogue: AnimalDialogueDirector.Dialogue,
        onLineStarted: ((AnimalDialogueDirector.SpokenLine) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        stopChildAudio()
        if (!ready) {
            pendingAnimalDialogue = dialogue
            pendingAnimalLineCallback = onLineStarted
            animalDialogueCompletion = onComplete
            return
        }
        tts.stop()
        dialogueGeneration += 1
        activeAnimalDialogue = dialogue
        animalDialogueIndex = 0
        activeAnimalLineCallback = onLineStarted
        animalDialogueCompletion = onComplete
        speakNextAnimalDialogueLine(dialogueGeneration)
    }

    private fun speakNextAnimalDialogueLine(generation: Int) {
        if (generation != dialogueGeneration) return
        val dialogue = activeAnimalDialogue ?: return
        if (animalDialogueIndex >= dialogue.lines.size) {
            activeAnimalDialogue = null
            activeAnimalLineCallback = null
            val completion = animalDialogueCompletion
            animalDialogueCompletion = null
            completion?.invoke()
            return
        }
        val line = dialogue.lines[animalDialogueIndex]
        val locale = when (line.language) {
            AnimalDialogueDirector.SpeechLanguage.BANGLA -> Locale("bn", "BD")
            AnimalDialogueDirector.SpeechLanguage.ARABIC -> Locale("ar")
        }
        if (tts.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) {
            animalDialogueIndex += 1
            speakNextAnimalDialogueLine(generation)
            return
        }
        tts.language = locale
        val animalPitch = when (line.animal.lowercase()) {
            "lion", "tiger", "bear" -> 0.92f
            "fox", "monkey", "rabbit" -> 1.20f
            else -> 1.05f
        }
        tts.setPitch(animalPitch)
        tts.setSpeechRate(if (line.language == AnimalDialogueDirector.SpeechLanguage.ARABIC) 0.76f else 0.82f)
        activeAnimalLineCallback?.invoke(line)
        tts.speak(
            line.audioText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "learnova-animal-dialogue-$generation-$animalDialogueIndex"
        )
    }

    fun speakEnglishLesson(letter: String, word: String) {
        speak("Letter $letter. $word.", Locale.US)
    }

    fun speakLesson(lesson: String) {
        SmartLearningEngine.lessonForDisplay(lesson)?.let { smart ->
            playChildLesson(smart)
            return
        }
        speak(lesson, if (lesson.any { it in '\u0600'..'\u06FF' }) Locale("ar") else Locale.US)
    }

    /**
     * Character dialogue is intentionally not generated with TTS.
     * It plays real child recordings when the matching asset exists.
     */
    fun speakSmartLesson(lesson: SmartLesson) {
        playChildLesson(lesson)
    }

    fun playChildLesson(lesson: SmartLesson) {
        stopChildAudio()
        val lines = ChildDialogueEngine.lesson(lesson)
        playChildSequence(lines)
    }

    fun playSalamExchange() {
        stopChildAudio()
        playChildSequence(ChildDialogueEngine.salam)
    }

    private fun playChildSequence(lines: List<ChildDialogue>) {
        if (lines.isEmpty()) return
        playChildLine(lines, 0)
    }

    private fun playChildLine(lines: List<ChildDialogue>, index: Int) {
        if (index >= lines.size) return
        val dialogue = lines[index]
        val player = try {
            val descriptor = context.assets.openFd(dialogue.assetPath)
            try {
                MediaPlayer().apply {
                    setDataSource(
                        descriptor.fileDescriptor,
                        descriptor.startOffset,
                        descriptor.length
                    )
                    prepare()
                }
            } finally {
                descriptor.close()
            }
        } catch (_: Exception) {
            // The current repository does not yet contain the recorded .ogg files.
            // Do not fail silently: use a child-style TTS fallback until those
            // recordings are added. The real recordings will automatically take
            // priority once their assets exist.
            speakChildFallback(dialogue.transcript, dialogue.locale)
            return
        }

        childPlayer = player
        player.setOnCompletionListener {
            it.release()
            if (childPlayer === it) childPlayer = null
            playChildLine(lines, index + 1)
        }
        player.setOnErrorListener { mp, _, _ ->
            mp.release()
            if (childPlayer === mp) childPlayer = null
            true
        }
        player.start()
    }

    fun speakCharacter(character: String, line: String) {
        if (!ready) return
        val profile = CharacterVoiceProfile.forName(character)
        val locale = profile.locale
        if (tts.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) return
        tts.language = locale
        selectCharacterVoice(profile)
        tts.setSpeechRate(profile.rate)
        tts.setPitch(profile.pitch)
        tts.speak(line, TextToSpeech.QUEUE_FLUSH, null, "learnova-character-${profile.id}")
        tts.setSpeechRate(0.72f)
        tts.setPitch(1.18f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId?.startsWith("learnova-animal-dialogue-") == true) {
                    mainHandler.post {
                        val generation = utteranceId.substringAfter("learnova-animal-dialogue-").substringBefore("-").toIntOrNull()
                        if (generation == dialogueGeneration) {
                            animalDialogueIndex += 1
                            speakNextAnimalDialogueLine(generation)
                        }
                    }
                }
            }

            @Deprecated("Deprecated in Android API")
            override fun onError(utteranceId: String?) {
                if (utteranceId?.startsWith("learnova-animal-dialogue-") == true) {
                    mainHandler.post {
                        val generation = utteranceId.substringAfter("learnova-animal-dialogue-").substringBefore("-").toIntOrNull()
                        if (generation == dialogueGeneration) {
                            animalDialogueIndex += 1
                            speakNextAnimalDialogueLine(generation)
                        }
                    }
                }
            }
        })
        pendingAnimalDialogue?.let { dialogue ->
            val callback = pendingAnimalLineCallback
            val completion = animalDialogueCompletion
            pendingAnimalDialogue = null
            pendingAnimalLineCallback = null
            animalDialogueCompletion = null
            playAnimalDialogue(dialogue, callback, completion)
        }
    }

    fun characterDiscover(character: String, word: String) {
        val line = when (character.lowercase()) {
            "bear" -> "Oh! Look, a $word!"
            "monkey" -> "Wow! A $word! Come and see!"
            "rabbit" -> "Look! A $word! Let's go!"
            "panda" -> "Look! A $word!"
            "tiger" -> "Look! A $word. Amazing!"
            "lion" -> "Wow! Look at the $word!"
            else -> "Look! A $word!"
        }
        speakCharacter(character, line)
    }

    fun characterPraise(character: String, word: String) {
        val line = when (character.lowercase()) {
            "bear" -> "Very good! You said $word."
            "monkey" -> "Yes! $word! Great!"
            "rabbit" -> "Great job! $word!"
            "panda" -> "Wonderful! $word!"
            else -> "Yes! $word! Very good!"
        }
        speakCharacter(character, line)
    }

    private fun selectCharacterVoice(profile: CharacterVoiceProfile) {
        val voices = runCatching { tts.voices ?: emptySet() }.getOrDefault(emptySet())
        val candidates = voices.filter { it.locale.language == profile.locale.language && !it.isNetworkConnectionRequired }
        val preferred = candidates.firstOrNull { v ->
            val n = v.name.lowercase()
            profile.voiceHints.any { n.contains(it) }
        } ?: candidates.firstOrNull()
        runCatching { tts.voice = preferred }
    }

    fun speakQuranStage(surahName: String, mode: String, stage: Int) {
        val line = when (stage.coerceIn(0, 2)) {
            0 -> "Quran time. $surahName. Listen and repeat."
            1 -> "$surahName. $mode. Touch the answer you know."
            else -> "Great. Say $surahName once more, then continue."
        }
        speak(line, Locale.US)
    }

    fun speakInstruction(running: Boolean) {
        speak(if (running) "Tap to stop." else "Tap to drive.", Locale.US)
    }

    fun speakVehicle(name: String) {
        speak(name, Locale.US)
    }

    fun speak(value: String, locale: Locale) {
        if (!ready) {
            pendingText = value
            pendingLocale = locale
            return
        }
        val availability = tts.isLanguageAvailable(locale)
        if (availability < TextToSpeech.LANG_AVAILABLE) return
        tts.language = locale
        tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, "learnova-system")
    }

    private fun speakChildFallback(value: String, localeTag: String) {
        if (!ready) return
        val locale = when {
            localeTag.equals("bn-BD", ignoreCase = true) -> Locale("bn", "BD")
            localeTag.startsWith("ar", ignoreCase = true) -> Locale("ar")
            else -> Locale.US
        }
        if (tts.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) return
        tts.language = locale
        tts.setSpeechRate(0.80f)
        tts.setPitch(1.30f)
        tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, "learnova-child-fallback")
        tts.setSpeechRate(0.72f)
        tts.setPitch(1.18f)
    }

    private fun stopChildAudio() {
        childPlayer?.let {
            runCatching { it.stop() }
            it.release()
        }
        childPlayer = null
    }

    fun stop() {
        stopChildAudio()
        dialogueGeneration += 1
        activeAnimalDialogue = null
        pendingAnimalDialogue = null
        activeAnimalLineCallback = null
        pendingAnimalLineCallback = null
        animalDialogueCompletion = null
        if (ready) tts.stop()
    }

    fun shutdown() {
        stopChildAudio()
        tts.stop()
        tts.shutdown()
    }
}
