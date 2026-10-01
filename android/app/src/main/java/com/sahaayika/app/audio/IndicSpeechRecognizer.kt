package com.sahaayika.app.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Indic Speech-to-Text Recognizer for Sahaayika (सहायिका).
 *
 * Encapsulates Android's [SpeechRecognizer] with preconfigured Indic language intents,
 * RMS audio normalization for voice waveform animations, and safe lifecycle management.
 */
open class IndicSpeechRecognizer(
    private val context: Context? = null
) {

    companion object {
        private const val TAG = "IndicSpeechRecognizer"

        /**
         * Typical quiet floor in dB for Android SpeechRecognizer.
         */
        const val DEFAULT_MIN_RMS_DB = -2.0f

        /**
         * Typical peak voice level in dB for Android SpeechRecognizer.
         */
        const val DEFAULT_MAX_RMS_DB = 10.0f

        /**
         * Normalizes decibel RMS volume level from Android SpeechRecognizer (typically -2 to 10 dB)
         * into a clamped [0.0..1.0] range suitable for pulsing mic UI animations.
         */
        fun normalizeRms(
            rmsDb: Float,
            minDb: Float = DEFAULT_MIN_RMS_DB,
            maxDb: Float = DEFAULT_MAX_RMS_DB
        ): Float {
            if (rmsDb.isNaN() || maxDb <= minDb) return 0.0f
            if (rmsDb <= minDb) return 0.0f
            if (rmsDb >= maxDb) return 1.0f
            return ((rmsDb - minDb) / (maxDb - minDb)).coerceIn(0.0f, 1.0f)
        }

        /**
         * Converts Android SpeechRecognizer error codes into clear, helpful error descriptions.
         */
        fun getErrorDescription(errorCode: Int): String {
            return when (errorCode) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side recognition error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "RECORD_AUDIO permission missing"
                SpeechRecognizer.ERROR_NETWORK -> "Network communication error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout while recognizing speech"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech match recognized"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition service busy"
                SpeechRecognizer.ERROR_SERVER -> "Server side recognition error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected within timeout"
                else -> "Speech recognition error code: $errorCode"
            }
        }

        private fun logDebug(tag: String, message: String) {
            try {
                android.util.Log.d(tag, message)
            } catch (_: Throwable) {
                // Ignore in JVM unit test stubs
            }
        }

        private fun logError(tag: String, message: String, throwable: Throwable? = null) {
            try {
                if (throwable != null) {
                    android.util.Log.e(tag, message, throwable)
                } else {
                    android.util.Log.e(tag, message)
                }
            } catch (_: Throwable) {
                // Ignore in JVM unit test stubs
            }
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Listener interface for speech recognition events.
     */
    interface Listener {
        fun onReadyForSpeech() {}
        fun onBeginningOfSpeech() {}
        fun onRmsChanged(normalizedRms: Float) {}
        fun onEndOfSpeech() {}
        fun onResults(text: String) {}
        fun onError(errorCode: Int, errorMessage: String) {}
    }

    private fun postOnMainThread(action: () -> Unit) {
        try {
            val mainLooper = Looper.getMainLooper()
            if (mainLooper != null && Looper.myLooper() != mainLooper) {
                Handler(mainLooper).post(action)
                return
            }
        } catch (_: Throwable) {
            // MainLooper unavailable in unit tests
        }
        action()
    }

    /**
     * Starts listening for speech in the specified Indic locale.
     *
     * @param language BCP-47 tag of the Indic locale (e.g. "hi-IN", "ta-IN", "bn-IN")
     * @param onReadyForSpeech Callback when audio input is actively ready
     * @param onRmsChanged Callback with normalized volume [0.0..1.0] for animation
     * @param onResults Callback with recognized speech text
     * @param onError Callback with error code and description
     * @param onEndOfSpeech Callback when the user finishes speaking
     */
    open fun startListening(
        language: String = "hi-IN",
        onReadyForSpeech: (() -> Unit)? = null,
        onRmsChanged: ((Float) -> Unit)? = null,
        onResults: ((String) -> Unit)? = null,
        onError: ((Int, String) -> Unit)? = null,
        onEndOfSpeech: (() -> Unit)? = null
    ) {
        postOnMainThread {
            try {
                if (speechRecognizer == null && context != null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, language)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        logDebug(TAG, "SpeechRecognizer onReadyForSpeech")
                        onReadyForSpeech?.invoke()
                    }

                    override fun onBeginningOfSpeech() {
                        logDebug(TAG, "SpeechRecognizer onBeginningOfSpeech")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = normalizeRms(rmsdB)
                        onRmsChanged?.invoke(normalized)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        logDebug(TAG, "SpeechRecognizer onEndOfSpeech")
                        onEndOfSpeech?.invoke()
                    }

                    override fun onError(error: Int) {
                        val errorDescription = getErrorDescription(error)
                        logError(TAG, "SpeechRecognizer onError: $error ($errorDescription)")
                        onError?.invoke(error, errorDescription)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull().orEmpty()
                        logDebug(TAG, "SpeechRecognizer onResults: $text")
                        onResults?.invoke(text)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull().orEmpty()
                        logDebug(TAG, "SpeechRecognizer onPartialResults: $partial")
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                logError(TAG, "Error initiating speech recognition", e)
                onError?.invoke(SpeechRecognizer.ERROR_CLIENT, e.message ?: "Failed to start listening")
            }
        }
    }

    /**
     * Stops listening and initiates transcription of audio recorded so far.
     */
    open fun stopListening() {
        postOnMainThread {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                logError(TAG, "Error stopping SpeechRecognizer", e)
            }
        }
    }

    /**
     * Cancels the current speech recognition session without delivering results.
     */
    open fun cancel() {
        postOnMainThread {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                logError(TAG, "Error cancelling SpeechRecognizer", e)
            }
        }
    }

    /**
     * Destroys the SpeechRecognizer instance and frees audio buffers.
     */
    open fun destroy() {
        postOnMainThread {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                logError(TAG, "Error destroying SpeechRecognizer", e)
            } finally {
                speechRecognizer = null
            }
        }
    }
}
