package com.sahaayika.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Indic Text-to-Speech Engine for Sahaayika (सहायिका).
 *
 * Encapsulates Android's [TextToSpeech] with specialized support for Indic languages
 * (Hindi, Tamil, Bengali, Telugu, Marathi, Indian English) and calibrated to a calm,
 * clear, elder-sister pace (speech rate = 0.9f).
 */
open class IndicTextToSpeech(
    context: Context? = null
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "IndicTextToSpeech"

        /**
         * Calm, clear, elder-sister speech pace (0.9x speed) for optimal rural audio comprehension.
         */
        const val SPEECH_RATE_CALM_ELDER_SISTER = 0.9f

        /**
         * Standard Indic languages supported by Sahaayika.
         */
        val SUPPORTED_INDIC_LOCALES = listOf(
            "hi-IN", // Hindi
            "ta-IN", // Tamil
            "bn-IN", // Bengali
            "te-IN", // Telugu
            "mr-IN", // Marathi
            "en-IN"  // Indian English
        )

        /**
         * Resolves a language tag (e.g., "hi-IN", "bn-IN", "ta-IN") into a Java [Locale].
         * Defaults to Hindi (hi-IN) if unspecified or invalid.
         */
        fun resolveLocale(languageTag: String): Locale {
            val normalized = languageTag.trim().replace('_', '-')
            val bcp47Tag = when (normalized.lowercase()) {
                "hi", "hi-in" -> "hi-IN"
                "ta", "ta-in" -> "ta-IN"
                "bn", "bn-in" -> "bn-IN"
                "te", "te-in" -> "te-IN"
                "mr", "mr-in" -> "mr-IN"
                "en", "en-in" -> "en-IN"
                "gu", "gu-in" -> "gu-IN"
                "kn", "kn-in" -> "kn-IN"
                "ml", "ml-in" -> "ml-IN"
                "pa", "pa-in" -> "pa-IN"
                "or", "or-in", "od-in" -> "or-IN"
                else -> {
                    val parts = normalized.split("-")
                    if (parts.size >= 2) {
                        "${parts[0].lowercase()}-${parts[1].uppercase()}"
                    } else if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                        "${parts[0].lowercase()}-IN"
                    } else {
                        "hi-IN"
                    }
                }
            }
            return Locale.forLanguageTag(bcp47Tag)
        }

        private fun logDebug(tag: String, message: String) {
            try {
                android.util.Log.d(tag, message)
            } catch (_: Throwable) {
                // Ignore in JVM unit test stubs
            }
        }

        private fun logWarn(tag: String, message: String) {
            try {
                android.util.Log.w(tag, message)
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

    private var tts: TextToSpeech? = null

    @Volatile
    var isInitialized: Boolean = false
        protected set

    private val utteranceCallbacks = ConcurrentHashMap<String, () -> Unit>()

    init {
        context?.let { ctx ->
            tts = TextToSpeech(ctx.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.apply {
                setSpeechRate(SPEECH_RATE_CALM_ELDER_SISTER)
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        logDebug(TAG, "TTS onStart utterance: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        logDebug(TAG, "TTS onDone utterance: $utteranceId")
                        utteranceId?.let { id ->
                            utteranceCallbacks.remove(id)?.invoke()
                        }
                    }

                    @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, -1)"))
                    override fun onError(utteranceId: String?) {
                        logError(TAG, "TTS onError utterance: $utteranceId")
                        utteranceId?.let { id ->
                            utteranceCallbacks.remove(id)?.invoke()
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        logError(TAG, "TTS onError utterance: $utteranceId with errorCode: $errorCode")
                        utteranceId?.let { id ->
                            utteranceCallbacks.remove(id)?.invoke()
                        }
                    }
                })
            }
            logDebug(TAG, "IndicTextToSpeech initialized with rate: $SPEECH_RATE_CALM_ELDER_SISTER")
        } else {
            isInitialized = false
            logError(TAG, "IndicTextToSpeech init failed with status: $status")
        }
    }

    /**
     * Speaks the given text in the specified Indic language at calm pace.
     *
     * @param text Indic or English text string to speak
     * @param language BCP-47 language tag (e.g. "hi-IN", "bn-IN", "ta-IN")
     * @param onComplete Callback invoked when speech finishes or fails
     */
    open fun speak(
        text: String,
        language: String = "hi-IN",
        onComplete: (() -> Unit)? = null
    ) {
        if (text.isBlank()) {
            onComplete?.invoke()
            return
        }

        val targetLocale = resolveLocale(language)
        tts?.let { engine ->
            val setLangResult = engine.setLanguage(targetLocale)
            if (setLangResult == TextToSpeech.LANG_MISSING_DATA || setLangResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                logWarn(TAG, "Locale $targetLocale not supported on device, falling back to Hindi")
                engine.setLanguage(Locale.forLanguageTag("hi-IN"))
            }

            // Enforce elder-sister speed
            engine.setSpeechRate(SPEECH_RATE_CALM_ELDER_SISTER)

            val utteranceId = UUID.randomUUID().toString()
            if (onComplete != null) {
                utteranceCallbacks[utteranceId] = onComplete
            }

            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                logError(TAG, "Failed to enqueue speech utterance")
                utteranceCallbacks.remove(utteranceId)?.invoke()
            }
        } ?: run {
            logWarn(TAG, "TextToSpeech engine not initialized, executing callback directly")
            onComplete?.invoke()
        }
    }

    /**
     * Stops any currently playing speech.
     */
    open fun stop() {
        utteranceCallbacks.clear()
        try {
            tts?.stop()
        } catch (e: Exception) {
            logError(TAG, "Error stopping TTS", e)
        }
    }

    /**
     * Releases TextToSpeech resources.
     */
    open fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            logError(TAG, "Error shutting down TTS", e)
        }
        tts = null
        isInitialized = false
    }
}
