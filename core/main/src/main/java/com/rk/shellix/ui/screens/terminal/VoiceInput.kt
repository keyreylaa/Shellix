package com.rk.shellix.ui.screens.terminal

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.ActivityCompat
import java.util.Locale

/**
 * Wraps Android's built-in [SpeechRecognizer] as a TOGGLE mic: tap once to start
 * listening, tap again to stop and process the result.
 *
 * Language: Android's [SpeechRecognizer] does not offer reliable, universal
 * auto-language-detection (it depends on the installed Google app / OS version and
 * has no stable public API for it). So instead of falsely claiming auto-detect, we
 * recognize in the device's current system language (BCP-47). If your phone is set
 * to Indonesian, speech is transcribed as Indonesian.
 */
object VoiceInput {

    @Volatile private var recognizer: SpeechRecognizer? = null
    @Volatile var isListening: Boolean = false
        private set

    /**
     * Toggle listening. If idle, start; if already listening, stop and let the
     * pending result callback fire. [onListening] reports the on/off state so the
     * UI can show a proper toggled indicator.
     */
    fun toggle(
        activity: Activity,
        onListening: (Boolean) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isListening) {
            stop()
            onListening(false)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            onError("Voice input unavailable")
            return
        }
        if (ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity, arrayOf(android.Manifest.permission.RECORD_AUDIO), 1001
            )
            onError("Microphone permission required")
            return
        }

        val sr = SpeechRecognizer.createSpeechRecognizer(activity)
        recognizer = sr

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                isListening = false
                onListening(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (text.isNullOrBlank()) onError("No speech recognized") else onResult(text)
                destroy()
            }
            override fun onError(error: Int) {
                isListening = false
                onListening(false)
                // ERROR_NO_MATCH after a manual stop is benign; report others.
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    onError("Voice error: $error")
                }
                destroy()
            }
            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })

        // Recognize in the device's current language (BCP-47, e.g. "id-ID" / "en-US").
        val lang = Locale.getDefault().toLanguageTag()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        isListening = true
        onListening(true)
        sr.startListening(intent)
    }

    /** Stop listening; the final result (if any) still arrives via onResults. */
    fun stop() {
        recognizer?.stopListening()
    }

    private fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}
