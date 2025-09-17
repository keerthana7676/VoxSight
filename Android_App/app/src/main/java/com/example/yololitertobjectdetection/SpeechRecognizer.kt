package com.example.yololitertobjectdetection

import android.content.Context
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import java.util.Locale

class SpeechRecognizerHelper(
    private val context: Context,
    private val onCommandRecognized: (String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit = {},
    private val onError: (String) -> Unit = {}
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val handler = Handler(Looper.getMainLooper())
    private var restartRunnable: Runnable? = null

    fun startListening() {
        if (isListening) return

        // Check microphone permission
        if (!hasMicrophonePermission()) {
            Log.e("SpeechRecognizer", "Microphone permission not granted")
            onError("Microphone permission not granted")
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }

            isListening = true
            onListeningStateChanged(true)
            restartListening()
            Log.d("SpeechRecognizer", "Started listening for commands")
        } catch (e: Exception) {
            Log.e("SpeechRecognizer", "Failed to create speech recognizer: ${e.message}")
            isListening = false
            onError("Failed to create speech recognizer")
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechRecognizer", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechRecognizer", "Speech beginning detected")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Optional: Visual feedback for speech level
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Not used
            }

            override fun onEndOfSpeech() {
                Log.d("SpeechRecognizer", "Speech ended")
            }

            override fun onError(error: Int) {
                val errorMsg = getErrorDescription(error)
                Log.e("SpeechRecognizer", "Error: $error - $errorMsg")
                onError(errorMsg)

                // Handle specific errors
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        Log.e("SpeechRecognizer", "Microphone permission not granted")
                        isListening = false
                        onListeningStateChanged(false)
                        return
                    }
                    SpeechRecognizer.ERROR_NO_MATCH -> {
                        // No speech recognized, restart quickly
                        restartWithDelay(500)
                    }
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // No speech input, restart quickly
                        restartWithDelay(500)
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        // Recognizer busy, wait a bit longer
                        restartWithDelay(1000)
                    }
                    else -> {
                        // Other errors, wait a bit before restarting
                        restartWithDelay(1500)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                    if (matches.isNotEmpty()) {
                        val recognizedText = matches[0].lowercase().trim()
                        Log.d("SpeechRecognizer", "Recognized: '$recognizedText'")
                        onCommandRecognized(recognizedText)
                    }
                }

                // Restart listening continuously
                if (isListening) {
                    restartWithDelay(1000)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Optional: Handle partial results for real-time feedback
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                    if (matches.isNotEmpty()) {
                        val partialText = matches[0]
                        Log.d("SpeechRecognizer", "Partial: '$partialText'")
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Not used
            }
        }
    }

    private fun restartListening() {
        if (!isListening) return

        // Check microphone permission again
        if (!hasMicrophonePermission()) {
            Log.e("SpeechRecognizer", "Microphone permission lost")
            stopListening()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your command")
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: SecurityException) {
            Log.e("SpeechRecognizer", "Security exception: ${e.message}")
            stopListening()
        } catch (e: Exception) {
            Log.e("SpeechRecognizer", "Failed to start listening: ${e.message}")
            restartWithDelay(2000)
        }
    }

    private fun restartWithDelay(delayMillis: Long) {
        // Cancel any pending restart
        restartRunnable?.let { handler.removeCallbacks(it) }

        restartRunnable = Runnable {
            if (isListening) {
                restartListening()
            }
        }
        handler.postDelayed(restartRunnable!!, delayMillis)
    }

    private fun getErrorDescription(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error ($errorCode)"
        }
    }

    fun stopListening() {
        isListening = false
        onListeningStateChanged(false)

        // Cancel any pending restart
        restartRunnable?.let { handler.removeCallbacks(it) }
        restartRunnable = null

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("SpeechRecognizer", "Error stopping recognizer: ${e.message}")
        }
        speechRecognizer = null

        Log.d("SpeechRecognizer", "Stopped listening")
    }

    fun isListening(): Boolean = isListening

    fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }
}