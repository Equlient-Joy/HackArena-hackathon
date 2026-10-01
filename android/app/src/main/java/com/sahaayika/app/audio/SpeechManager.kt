package com.sahaayika.app.audio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State enum representing the active lifecycle of the Sahaayika voice system.
 */
enum class SpeechState {
    /** Neither speaking nor listening. Ready for input. */
    IDLE,
    /** Text-to-Speech is actively speaking Indic audio. */
    SPEAKING,
    /** Microphone is open and capturing user's Indic speech. */
    LISTENING,
    /** Audio captured, awaiting backend or offline speech transcription. */
    PROCESSING,
    /** Encountered an audio recording, recognition, or TTS synthesis error. */
    ERROR
}

/**
 * Unified Speech Orchestrator for Sahaayika (सहायिका).
 *
 * Manages the coordinated interplay of Indic Text-to-Speech and Indic Speech-to-Text:
 * 1. Enforces strict mutual exclusion: TTS never speaks while mic is actively listening.
 * 2. Exposes real-time [SpeechState] via [StateFlow] for UI state binding.
 * 3. Provides high-level conversational flows such as [speakThenListen].
 */
class SpeechManager(
    private val tts: IndicTextToSpeech,
    private val recognizer: IndicSpeechRecognizer
) {

    /**
     * Standard constructor initializing real Android audio engines with [Context].
     */
    constructor(context: Context) : this(
        IndicTextToSpeech(context),
        IndicSpeechRecognizer(context)
    )

    companion object {
        const val DEFAULT_LANGUAGE = "hi-IN"
    }

    private val _state = MutableStateFlow(SpeechState.IDLE)

    /**
     * Observable state flow representing current voice engine status.
     */
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    /**
     * Speaks the specified text in an Indic language with elder-sister pace.
     * Enforces mutual exclusion: if mic was listening, it is immediately cancelled.
     *
     * @param text Text string to speak
     * @param language Indic language code (e.g. "hi-IN", "ta-IN", "bn-IN")
     * @param onComplete Callback invoked when speech finishes
     */
    fun speak(
        text: String,
        language: String = DEFAULT_LANGUAGE,
        onComplete: (() -> Unit)? = null
    ) {
        // Enforce mutual exclusion: stop mic before speaking
        if (_state.value == SpeechState.LISTENING || _state.value == SpeechState.PROCESSING) {
            recognizer.cancel()
        } else if (_state.value == SpeechState.SPEAKING) {
            tts.stop()
        }

        if (text.isBlank()) {
            _state.value = SpeechState.IDLE
            onComplete?.invoke()
            return
        }

        _state.value = SpeechState.SPEAKING
        tts.speak(text, language) {
            if (_state.value == SpeechState.SPEAKING) {
                _state.value = SpeechState.IDLE
            }
            onComplete?.invoke()
        }
    }

    /**
     * Begins listening to citizen speech in the specified Indic language.
     * Enforces mutual exclusion: if TTS was speaking, it is immediately stopped.
     *
     * @param language Indic language code (e.g. "hi-IN", "te-IN")
     * @param onVolumeChanged Normalized volume callback [0.0..1.0] for ripple/waveform animations
     * @param onResult Callback delivering recognized speech transcription
     * @param onError Callback delivering error description on failure
     */
    fun listen(
        language: String = DEFAULT_LANGUAGE,
        onVolumeChanged: (Float) -> Unit = {},
        onResult: (String) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        // Enforce mutual exclusion: stop TTS before opening mic
        if (_state.value == SpeechState.SPEAKING) {
            tts.stop()
        } else if (_state.value == SpeechState.LISTENING || _state.value == SpeechState.PROCESSING) {
            recognizer.cancel()
        }

        _state.value = SpeechState.LISTENING

        recognizer.startListening(
            language = language,
            onReadyForSpeech = {
                if (_state.value == SpeechState.LISTENING) {
                    // Ready to capture speech
                }
            },
            onRmsChanged = { volume ->
                if (_state.value == SpeechState.LISTENING) {
                    onVolumeChanged(volume)
                }
            },
            onEndOfSpeech = {
                if (_state.value == SpeechState.LISTENING) {
                    _state.value = SpeechState.PROCESSING
                }
            },
            onResults = { resultText ->
                if (_state.value == SpeechState.LISTENING || _state.value == SpeechState.PROCESSING) {
                    _state.value = SpeechState.IDLE
                }
                onResult(resultText)
            },
            onError = { _, errorDesc ->
                if (_state.value == SpeechState.LISTENING || _state.value == SpeechState.PROCESSING) {
                    _state.value = SpeechState.ERROR
                }
                onError(errorDesc)
            }
        )
    }

    /**
     * Chains a spoken audio prompt followed by immediate microphone listening upon completion.
     * Designed for conversational turn-taking with rural citizens.
     *
     * @param promptText Spoken question or instruction from Sahaayika
     * @param language Indic language code (default "hi-IN")
     * @param onResult Callback delivering citizen's spoken response
     */
    fun speakThenListen(
        promptText: String,
        language: String = DEFAULT_LANGUAGE,
        onResult: (String) -> Unit
    ) {
        speak(promptText, language) {
            listen(
                language = language,
                onVolumeChanged = {},
                onResult = onResult,
                onError = {}
            )
        }
    }

    /**
     * Overload of [speakThenListen] with volume change and error handling callbacks.
     */
    fun speakThenListen(
        promptText: String,
        language: String = DEFAULT_LANGUAGE,
        onVolumeChanged: (Float) -> Unit,
        onError: (String) -> Unit,
        onResult: (String) -> Unit
    ) {
        speak(promptText, language) {
            listen(
                language = language,
                onVolumeChanged = onVolumeChanged,
                onResult = onResult,
                onError = onError
            )
        }
    }

    /**
     * Stops both speech synthesis and speech recognition immediately, restoring state to [SpeechState.IDLE].
     */
    fun stop() {
        tts.stop()
        recognizer.cancel()
        _state.value = SpeechState.IDLE
    }

    /**
     * Releases all TTS and SpeechRecognizer resources.
     */
    fun destroy() {
        stop()
        tts.shutdown()
        recognizer.destroy()
        _state.value = SpeechState.IDLE
    }
}
