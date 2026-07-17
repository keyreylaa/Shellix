package com.rk.shellix.ui.screens.terminal

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.ActivityCompat
import java.util.Locale

/**
 * Wraps Android's built-in [SpeechRecognizer] to convert speech to text.
 * Recognized text is delivered via [onResult]; the caller decides what to do
 * with it (e.g. paste into the terminal, editable before Enter).
 */
object VoiceInput {
    private const val TIMEOUT_MS = 10_000L

    /**
     * Starts speech recognition.
     *
     * @param activity the host activity (used for permission + recognizer).
     * @param onResult called with the recognized text (best match).
     * @param onError called with a human-readable error description.
     */
    fun recognize(
        activity: Activity,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
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

        val recognizer = SpeechRecognizer.createSpeechRecognizer(activity)
        val timeout = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            recognizer.stopListening()
            onError("Voice input timed out")
        }
        timeout.postDelayed(timeoutRunnable, TIMEOUT_MS)

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                timeout.removeCallbacks(timeoutRunnable)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (text.isNullOrBlank()) onError("No speech recognized") else onResult(text)
                recognizer.destroy()
            }
            override fun onError(error: Int) {
                timeout.removeCallbacks(timeoutRunnable)
                onError("Voice error: $error")
                recognizer.destroy()
            }
            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer.startListening(intent)
    }
}
