package com.sahaayika.app

import com.sahaayika.app.audio.IndicSpeechRecognizer
import com.sahaayika.app.audio.IndicTextToSpeech
import com.sahaayika.app.audio.SpeechManager
import com.sahaayika.app.audio.SpeechState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class SpeechManagerTest {

    private lateinit var fakeTts: FakeIndicTextToSpeech
    private lateinit var fakeRecognizer: FakeIndicSpeechRecognizer
    private lateinit var speechManager: SpeechManager

    @Before
    fun setUp() {
        fakeTts = FakeIndicTextToSpeech()
        fakeRecognizer = FakeIndicSpeechRecognizer()
        speechManager = SpeechManager(fakeTts, fakeRecognizer)
    }

    // ==========================================
    // 1. Locale Tag Resolution Tests
    // ==========================================

    @Test
    fun testLocaleResolution_Hindi() {
        val locale = IndicTextToSpeech.resolveLocale("hi-IN")
        assertEquals(Locale.forLanguageTag("hi-IN"), locale)
        assertEquals("hi", locale.language)
        assertEquals("IN", locale.country)
    }

    @Test
    fun testLocaleResolution_Bengali() {
        val locale = IndicTextToSpeech.resolveLocale("bn-IN")
        assertEquals(Locale.forLanguageTag("bn-IN"), locale)
        assertEquals("bn", locale.language)
        assertEquals("IN", locale.country)
    }

    @Test
    fun testLocaleResolution_Tamil() {
        val locale = IndicTextToSpeech.resolveLocale("ta-IN")
        assertEquals(Locale.forLanguageTag("ta-IN"), locale)
        assertEquals("ta", locale.language)
        assertEquals("IN", locale.country)
    }

    @Test
    fun testLocaleResolution_Telugu() {
        val locale = IndicTextToSpeech.resolveLocale("te-IN")
        assertEquals(Locale.forLanguageTag("te-IN"), locale)
        assertEquals("te", locale.language)
        assertEquals("IN", locale.country)
    }

    @Test
    fun testLocaleResolution_Marathi() {
        val locale = IndicTextToSpeech.resolveLocale("mr-IN")
        assertEquals(Locale.forLanguageTag("mr-IN"), locale)
        assertEquals("mr", locale.language)
        assertEquals("IN", locale.country)
    }

    @Test
    fun testLocaleResolution_IndianEnglish() {
        val locale = IndicTextToSpeech.resolveLocale("en-IN")
        assertEquals(Locale.forLanguageTag("en-IN"), locale)
        assertEquals("en", locale.language)
        assertEquals("IN", locale.country)
    }

    @Test
    fun testLocaleResolution_ShortLanguageCodes() {
        assertEquals(Locale.forLanguageTag("hi-IN"), IndicTextToSpeech.resolveLocale("hi"))
        assertEquals(Locale.forLanguageTag("bn-IN"), IndicTextToSpeech.resolveLocale("bn"))
        assertEquals(Locale.forLanguageTag("ta-IN"), IndicTextToSpeech.resolveLocale("ta"))
        assertEquals(Locale.forLanguageTag("te-IN"), IndicTextToSpeech.resolveLocale("te"))
        assertEquals(Locale.forLanguageTag("mr-IN"), IndicTextToSpeech.resolveLocale("mr"))
        assertEquals(Locale.forLanguageTag("en-IN"), IndicTextToSpeech.resolveLocale("en"))
    }

    @Test
    fun testLocaleResolution_CaseInsensitiveAndUnderscore() {
        assertEquals(Locale.forLanguageTag("hi-IN"), IndicTextToSpeech.resolveLocale("HI-in"))
        assertEquals(Locale.forLanguageTag("ta-IN"), IndicTextToSpeech.resolveLocale("ta_IN"))
        assertEquals(Locale.forLanguageTag("bn-IN"), IndicTextToSpeech.resolveLocale("BN_in"))
    }

    @Test
    fun testLocaleResolution_OtherIndicLanguages() {
        assertEquals(Locale.forLanguageTag("gu-IN"), IndicTextToSpeech.resolveLocale("gu-IN"))
        assertEquals(Locale.forLanguageTag("kn-IN"), IndicTextToSpeech.resolveLocale("kn-IN"))
        assertEquals(Locale.forLanguageTag("ml-IN"), IndicTextToSpeech.resolveLocale("ml-IN"))
        assertEquals(Locale.forLanguageTag("pa-IN"), IndicTextToSpeech.resolveLocale("pa-IN"))
        assertEquals(Locale.forLanguageTag("or-IN"), IndicTextToSpeech.resolveLocale("or-IN"))
    }

    @Test
    fun testLocaleResolution_FallbackOnEmpty() {
        val fallback = IndicTextToSpeech.resolveLocale("")
        assertEquals(Locale.forLanguageTag("hi-IN"), fallback)
    }

    // ==========================================
    // 2. Volume RMS Normalization Tests
    // ==========================================

    @Test
    fun testNormalizeRms_BelowOrEqualMinimum() {
        assertEquals(0.0f, IndicSpeechRecognizer.normalizeRms(-5.0f), 0.001f)
        assertEquals(0.0f, IndicSpeechRecognizer.normalizeRms(-2.0f), 0.001f)
        assertEquals(0.0f, IndicSpeechRecognizer.normalizeRms(-10.0f), 0.001f)
    }

    @Test
    fun testNormalizeRms_AboveOrEqualMaximum() {
        assertEquals(1.0f, IndicSpeechRecognizer.normalizeRms(10.0f), 0.001f)
        assertEquals(1.0f, IndicSpeechRecognizer.normalizeRms(15.0f), 0.001f)
        assertEquals(1.0f, IndicSpeechRecognizer.normalizeRms(100.0f), 0.001f)
    }

    @Test
    fun testNormalizeRms_MidpointLinearScaling() {
        // min = -2.0, max = 10.0 -> total span = 12.0
        // rmsDb = 4.0 -> (4.0 - (-2.0)) / 12.0 = 6.0 / 12.0 = 0.5f
        val midRms = IndicSpeechRecognizer.normalizeRms(4.0f)
        assertEquals(0.5f, midRms, 0.001f)

        // rmsDb = 1.0 -> (1.0 - (-2.0)) / 12.0 = 3.0 / 12.0 = 0.25f
        val quarterRms = IndicSpeechRecognizer.normalizeRms(1.0f)
        assertEquals(0.25f, quarterRms, 0.001f)

        // rmsDb = 7.0 -> (7.0 - (-2.0)) / 12.0 = 9.0 / 12.0 = 0.75f
        val threeQuarterRms = IndicSpeechRecognizer.normalizeRms(7.0f)
        assertEquals(0.75f, threeQuarterRms, 0.001f)
    }

    @Test
    fun testNormalizeRms_EdgeCases() {
        // NaN should yield 0.0f
        assertEquals(0.0f, IndicSpeechRecognizer.normalizeRms(Float.NaN), 0.001f)

        // Custom min/max range
        assertEquals(0.5f, IndicSpeechRecognizer.normalizeRms(5.0f, minDb = 0f, maxDb = 10f), 0.001f)

        // Inverted or invalid range
        assertEquals(0.0f, IndicSpeechRecognizer.normalizeRms(5.0f, minDb = 10f, maxDb = 5f), 0.001f)
    }

    // ==========================================
    // 3. SpeechState and Transitions
    // ==========================================

    @Test
    fun testInitialState_IsIdle() {
        assertEquals(SpeechState.IDLE, speechManager.state.value)
    }

    @Test
    fun testSpeak_TransitionToSpeakingAndCompletionToIdle() {
        var completed = false
        speechManager.speak("नमस्ते, मैं आपकी क्या सहायता कर सकती हूँ?", "hi-IN") {
            completed = true
        }

        assertEquals(SpeechState.SPEAKING, speechManager.state.value)
        assertEquals("नमस्ते, मैं आपकी क्या सहायता कर सकती हूँ?", fakeTts.lastSpokenText)
        assertEquals("hi-IN", fakeTts.lastLanguage)
        assertFalse(completed)

        // Simulate TTS completion
        fakeTts.completeCurrentSpeech()

        assertEquals(SpeechState.IDLE, speechManager.state.value)
        assertTrue(completed)
    }

    @Test
    fun testSpeak_BlankTextRemainsIdle() {
        var completed = false
        speechManager.speak("   ", "hi-IN") {
            completed = true
        }

        assertEquals(SpeechState.IDLE, speechManager.state.value)
        assertTrue(completed)
        assertEquals(0, fakeTts.speakCount)
    }

    @Test
    fun testListen_TransitionToListeningAndResultsToIdle() {
        var receivedResult = ""
        var lastVolume = 0.0f

        speechManager.listen(
            language = "hi-IN",
            onVolumeChanged = { volume -> lastVolume = volume },
            onResult = { text -> receivedResult = text }
        )

        assertEquals(SpeechState.LISTENING, speechManager.state.value)
        assertEquals("hi-IN", fakeRecognizer.lastLanguage)

        // Simulate volume animation update
        fakeRecognizer.simulateRms(0.75f)
        assertEquals(0.75f, lastVolume, 0.001f)

        // Simulate end of speech (voice activity stopped, transcribing)
        fakeRecognizer.simulateEndOfSpeech()
        assertEquals(SpeechState.PROCESSING, speechManager.state.value)

        // Simulate recognition results delivered
        fakeRecognizer.simulateResults("लाडली बहना योजना")
        assertEquals(SpeechState.IDLE, speechManager.state.value)
        assertEquals("लाडली बहना योजना", receivedResult)
    }

    @Test
    fun testListen_TransitionToErrorState() {
        var receivedError = ""
        speechManager.listen(
            language = "hi-IN",
            onResult = {},
            onError = { err -> receivedError = err }
        )

        assertEquals(SpeechState.LISTENING, speechManager.state.value)

        fakeRecognizer.simulateError(3, "Audio recording error")
        assertEquals(SpeechState.ERROR, speechManager.state.value)
        assertEquals("Audio recording error", receivedError)
    }

    // ==========================================
    // 4. Mutual Exclusion Tests
    // ==========================================

    @Test
    fun testMutualExclusion_ListenStopsSpeaking() {
        speechManager.speak("सहायिका बोल रही है", "hi-IN")
        assertEquals(SpeechState.SPEAKING, speechManager.state.value)
        assertEquals(0, fakeTts.stopCount)

        // User or system starts listening while TTS was speaking
        speechManager.listen(
            language = "hi-IN",
            onResult = {}
        )

        // TTS must be forcibly stopped
        assertEquals(1, fakeTts.stopCount)
        assertEquals(SpeechState.LISTENING, speechManager.state.value)
    }

    @Test
    fun testMutualExclusion_SpeakStopsListening() {
        speechManager.listen(
            language = "hi-IN",
            onResult = {}
        )
        assertEquals(SpeechState.LISTENING, speechManager.state.value)
        assertEquals(0, fakeRecognizer.cancelCount)

        // System speaks while mic was listening
        speechManager.speak("उत्तर सुनिए", "hi-IN")

        // Recognizer must be cancelled
        assertEquals(1, fakeRecognizer.cancelCount)
        assertEquals(SpeechState.SPEAKING, speechManager.state.value)
    }

    // ==========================================
    // 5. SpeakThenListen Auto-Chaining Tests
    // ==========================================

    @Test
    fun testSpeakThenListen_AutoChainsCorrectly() {
        var finalResult = ""

        speechManager.speakThenListen(
            promptText = "अपना समग्र आईडी बताइए",
            language = "hi-IN",
            onResult = { result -> finalResult = result }
        )

        // Step 1: speaking prompt
        assertEquals(SpeechState.SPEAKING, speechManager.state.value)
        assertEquals("अपना समग्र आईडी बताइए", fakeTts.lastSpokenText)
        assertEquals(0, fakeRecognizer.startListeningCount)

        // Step 2: TTS finishes utterance -> auto triggers listening
        fakeTts.completeCurrentSpeech()
        assertEquals(SpeechState.LISTENING, speechManager.state.value)
        assertEquals(1, fakeRecognizer.startListeningCount)
        assertEquals("hi-IN", fakeRecognizer.lastLanguage)

        // Step 3: Citizen speaks ID
        fakeRecognizer.simulateResults("123456789")
        assertEquals("123456789", finalResult)
        assertEquals(SpeechState.IDLE, speechManager.state.value)
    }

    // ==========================================
    // 6. Stop and Destroy Tests
    // ==========================================

    @Test
    fun testStop_ResetsToIdleAndCancelsEngines() {
        speechManager.speak("कुछ भी", "hi-IN")
        assertEquals(SpeechState.SPEAKING, speechManager.state.value)

        speechManager.stop()
        assertEquals(SpeechState.IDLE, speechManager.state.value)
        assertEquals(1, fakeTts.stopCount)
        assertEquals(1, fakeRecognizer.cancelCount)
    }

    @Test
    fun testDestroy_ShutsDownEngines() {
        speechManager.destroy()
        assertEquals(SpeechState.IDLE, speechManager.state.value)
        assertEquals(1, fakeTts.shutdownCount)
        assertEquals(1, fakeRecognizer.destroyCount)
    }
}

/**
 * Fake implementation of [IndicTextToSpeech] for JVM unit tests without Android mock overhead.
 */
class FakeIndicTextToSpeech : IndicTextToSpeech(context = null) {
    var lastSpokenText: String? = null
    var lastLanguage: String? = null
    var speakCount: Int = 0
    var stopCount: Int = 0
    var shutdownCount: Int = 0

    private var pendingOnComplete: (() -> Unit)? = null

    override fun speak(text: String, language: String, onComplete: (() -> Unit)?) {
        speakCount++
        lastSpokenText = text
        lastLanguage = language
        pendingOnComplete = onComplete
    }

    fun completeCurrentSpeech() {
        val callback = pendingOnComplete
        pendingOnComplete = null
        callback?.invoke()
    }

    override fun stop() {
        stopCount++
        pendingOnComplete = null
    }

    override fun shutdown() {
        shutdownCount++
        pendingOnComplete = null
    }
}

/**
 * Fake implementation of [IndicSpeechRecognizer] for JVM unit tests without Android mock overhead.
 */
class FakeIndicSpeechRecognizer : IndicSpeechRecognizer(context = null) {
    var lastLanguage: String? = null
    var startListeningCount: Int = 0
    var stopListeningCount: Int = 0
    var cancelCount: Int = 0
    var destroyCount: Int = 0

    private var onReadyForSpeechCallback: (() -> Unit)? = null
    private var onRmsChangedCallback: ((Float) -> Unit)? = null
    private var onResultsCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((Int, String) -> Unit)? = null
    private var onEndOfSpeechCallback: (() -> Unit)? = null

    override fun startListening(
        language: String,
        onReadyForSpeech: (() -> Unit)?,
        onRmsChanged: ((Float) -> Unit)?,
        onResults: ((String) -> Unit)?,
        onError: ((Int, String) -> Unit)?,
        onEndOfSpeech: (() -> Unit)?
    ) {
        startListeningCount++
        lastLanguage = language
        this.onReadyForSpeechCallback = onReadyForSpeech
        this.onRmsChangedCallback = onRmsChanged
        this.onResultsCallback = onResults
        this.onErrorCallback = onError
        this.onEndOfSpeechCallback = onEndOfSpeech
    }

    fun simulateReady() {
        onReadyForSpeechCallback?.invoke()
    }

    fun simulateRms(normalizedRms: Float) {
        onRmsChangedCallback?.invoke(normalizedRms)
    }

    fun simulateEndOfSpeech() {
        onEndOfSpeechCallback?.invoke()
    }

    fun simulateResults(text: String) {
        onResultsCallback?.invoke(text)
    }

    fun simulateError(code: Int, message: String) {
        onErrorCallback?.invoke(code, message)
    }

    override fun stopListening() {
        stopListeningCount++
    }

    override fun cancel() {
        cancelCount++
    }

    override fun destroy() {
        destroyCount++
    }
}
