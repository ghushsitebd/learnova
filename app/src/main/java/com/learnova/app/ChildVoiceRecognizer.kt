package com.learnova.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Optional child speech input used at a roadside learning sign.
 *
 * The recognizer is multilingual-first: it tries the requested language and,
 * when the platform reports a recognition error, retries with Bangla and
 * Arabic before giving up. Recognition remains local/offline when the device
 * supports Android's on-device recognizer.
 */
internal class ChildVoiceRecognizer(
    private val activity: Activity,
    private val onResult: (String) -> Unit,
    private val onError: () -> Unit
) {
    companion object { const val REQUEST_CODE = 9417 }

    private var recognizer: SpeechRecognizer? = null
    private var languageQueue: List<String> = emptyList()
    private var languageIndex = 0
    private var retrying = false

    fun start(languageTag: String = "en-US") {
        languageQueue = listOf(
            languageTag,
            "bn-BD",
            "ar-SA"
        ).distinct()
        languageIndex = 0
        retrying = false
        startCurrentLanguage()
    }

    private fun startCurrentLanguage() {
        if (Build.VERSION.SDK_INT >= 23 &&
            activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            onError()
            return
        }

        stopRecognizerOnly()

        recognizer = try {
            if (Build.VERSION.SDK_INT >= 31 &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
            } else {
                SpeechRecognizer.createSpeechRecognizer(activity)
            }
        } catch (_: Throwable) {
            retryOrFail()
            return
        }

        val languageTag = languageQueue.getOrNull(languageIndex) ?: run {
            onError()
            return
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit

            override fun onError(error: Int) {
                retryOrFail()
            }

            override fun onResults(results: android.os.Bundle?) {
                val values = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val heard = values?.firstOrNull().orEmpty()
                if (heard.isBlank()) retryOrFail() else onResult(heard)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        try {
            recognizer?.startListening(intent)
        } catch (_: Throwable) {
            retryOrFail()
        }
    }

    private fun retryOrFail() {
        if (retrying) return
        retrying = true
        stopRecognizerOnly()
        if (languageIndex + 1 < languageQueue.size) {
            languageIndex += 1
            retrying = false
            activity.runOnUiThread { startCurrentLanguage() }
        } else {
            onError()
        }
    }

    private fun stopRecognizerOnly() {
        try { recognizer?.cancel() } catch (_: Throwable) {}
        try { recognizer?.destroy() } catch (_: Throwable) {}
        recognizer = null
    }

    fun stop() {
        languageQueue = emptyList()
        languageIndex = 0
        retrying = false
        stopRecognizerOnly()
    }
}
