package com.sahaayika.app.ui.hub

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sahaayika.R
import com.example.sahaayika.databinding.ActivityGuidedHubBinding
import com.sahaayika.app.audio.SpeechManager
import com.sahaayika.app.audio.SpeechState
import com.sahaayika.app.data.api.SahaayikaApiService
import com.sahaayika.app.data.model.IntentResponse
import com.sahaayika.app.data.model.Language
import com.sahaayika.app.data.model.SchemeItem
import com.sahaayika.app.data.profile.UserPreferences
import com.sahaayika.app.ui.language.LanguageActivity
import com.sahaayika.app.ui.overlay.SpotlightOverlayService
import kotlinx.coroutines.launch

/**
 * Visual mic ring and status states for GuidedHubActivity.
 */
enum class HubMicState {
    IDLE,
    RECORDING,
    PROCESSING,
    ERROR
}

/**
 * Screen 2: Guided Action & Intent Hub (दीदी गाइडेड हब).
 *
 * Voice-first primary navigation hub for rural citizens:
 * 1. Proactive elder-sister audio greeting on launch.
 * 2. Massive 130dp+ circular pulsing microphone button.
 * 3. Reactive ring color and volume-based ripple animations.
 * 4. Curated scheme recommendation quick-tap cards.
 * 5. Assisted text input bar fallback.
 * 6. Intent resolution via FastAPI backend, overlay permission check, and browser portal launch.
 */
class GuidedHubActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LANGUAGE_CODE = "extra_language_code"
        const val PROMPT_HINDI = "माइक वाले गोल बटन को दबाएं और बताएं कि आपको किस योजना की जानकारी चाहिए।"
        const val PROMPT_ENGLISH = "Please tap the microphone button and tell me which government scheme you need help with."

        fun createIntent(context: Context, languageCode: String? = null): Intent {
            return Intent(context, GuidedHubActivity::class.java).apply {
                if (languageCode != null) {
                    putExtra(EXTRA_LANGUAGE_CODE, languageCode)
                }
            }
        }

        fun getProactiveNarration(languageCode: String): String {
            return when (languageCode.lowercase().substringBefore("-")) {
                "en" -> PROMPT_ENGLISH
                "ta" -> "மைக்கை அழுத்தி உங்களுக்கு என்ன அரசு திட்ட உதவி வேண்டும் என்று சொல்லுங்கள்."
                "bn" -> "মাইক বোতামটি টিপুন এবং বলুন আপনার কোন সরকারি প্রকল্পের সাহায্য দরকার।"
                "te" -> "మైక్ బటన్‌ను నొక్కి మీకు ఏ ప్రభుత్వ పథకం సహాయం కావాలో చెప్పండి."
                "mr" -> "माइकचे बटण दाबा आणि सांगा तुम्हाला कोणत्या सरकारी योजनेची मदत हवी आहे."
                else -> PROMPT_HINDI
            }
        }

        fun getUnrecognizedSpeechMessage(languageCode: String): String {
            return when (languageCode.lowercase().substringBefore("-")) {
                "en" -> "Sorry, I could not identify this scheme. Please tap the mic and try again."
                "ta" -> "மன்னிக்கவும், இந்த திட்டம் புரியவில்லை. மீண்டும் மைக்கை அழுத்தி பேசவும்."
                "bn" -> "দুঃখিত, এই প্রকল্পটি বুঝতে পারিনি। দয়া করে আবার বলুন।"
                "te" -> "క్షమించండి, ఈ పథకం గుర్తించబడలేదు. దయచేసి మళ్ళీ మాట్లాడండి."
                "mr" -> "क्षमस्व, ही योजना ओळखता आली नाही. कृपया पुन्हा बोला."
                else -> "माफ़ कीजिए, मैं इस योजना को पहचान नहीं पाई। कृपया दोबारा बोलें।"
            }
        }

        fun matchSchemeLocally(query: String, languageCode: String = "hi-IN"): SchemeItem? {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return null

            val curated = SchemeCardAdapter.getCuratedSchemesForLanguage(languageCode)

            // 1. Direct name or ID match
            val exactMatch = curated.firstOrNull {
                it.schemeName.lowercase().contains(q) || it.schemeId.lowercase() == q
            }
            if (exactMatch != null) return exactMatch

            // 2. Keyword heuristic matching
            return when {
                q.contains("लाडली") || q.contains("ladli") || q.contains("बहना") || q.contains("behna") ->
                    curated.firstOrNull { it.schemeId == "ladli_behna" }
                q.contains("किसान") || q.contains("kisan") || q.contains("सम्मान") || q.contains("samman") ->
                    curated.firstOrNull { it.schemeId == "pm_kisan" }
                q.contains("राशन") || q.contains("ration") || q.contains("खाद्य") || q.contains("nfsa") ->
                    curated.firstOrNull { it.schemeId == "ration_card" }
                q.contains("आयुष्मान") || q.contains("ayushman") || q.contains("इलाज") || q.contains("गोल्डन") ->
                    curated.firstOrNull { it.schemeId == "ayushman_bharat" }
                q.contains("जननी") || q.contains("janani") || q.contains("प्रसूति") || q.contains("गर्भवती") ->
                    curated.firstOrNull { it.schemeId == "janani_suraksha" }
                q.contains("पेंशन") || q.contains("pension") || q.contains("वृद्धा") || q.contains("विधवा") ->
                    curated.firstOrNull { it.schemeId == "pension" }
                else -> null
            }
        }
    }

    private lateinit var binding: ActivityGuidedHubBinding
    internal lateinit var speechManager: SpeechManager
    internal lateinit var userPreferences: UserPreferences
    internal var apiService: SahaayikaApiService = SahaayikaApiService()

    private var currentLanguageCode: String = UserPreferences.DEFAULT_LANGUAGE
    private var currentMicState: HubMicState = HubMicState.IDLE
    private var idlePulseAnimator: ValueAnimator? = null
    private var isPlayingProactiveGreeting = false

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
        } else {
            updateMicVisualState(HubMicState.ERROR)
            binding.tvStatusText.text = "माइक की अनुमति नहीं मिली"
            binding.tvStatusSubtext.text = "कृपया सेटिंग्स में जाकर माइक की अनुमति दें"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuidedHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!::speechManager.isInitialized) {
            speechManager = SpeechManager(this)
        }
        if (!::userPreferences.isInitialized) {
            userPreferences = UserPreferences(this)
        }

        // Determine active language
        val extraLang = intent.getStringExtra(EXTRA_LANGUAGE_CODE)
        currentLanguageCode = if (!extraLang.isNullOrBlank()) {
            extraLang
        } else {
            userPreferences.getSelectedLanguage()
        }

        setupTopBar()
        setupStatusBanner()
        setupMicButton()
        setupSchemeRecommendations()
        setupBottomTextInput()
    }

    override fun onStart() {
        super.onStart()
        updateMicVisualState(HubMicState.IDLE)
        // Proactively narrate elder-sister audio prompt upon launch
        binding.root.postDelayed({
            if (!isFinishing && !isDestroyed) {
                playProactiveGreeting()
            }
        }, 350)
    }

    private fun setupTopBar() {
        val language = Language.findByCode(currentLanguageCode)
        binding.tvLanguageBadgeIcon.text = language.badge
        binding.tvCurrentLanguage.text = language.nativeName

        // Language change navigation
        binding.cardLanguageBadge.setOnClickListener {
            speechManager.stop()
            val intent = Intent(this, LanguageActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun setupStatusBanner() {
        val greetingText = when (currentLanguageCode.lowercase().substringBefore("-")) {
            "en" -> "Didi, tell me: how can I help you today?"
            "ta" -> "அக்கா, சொல்லுங்கள்: உங்களுக்கு என்ன உதவி வேண்டும்?"
            "bn" -> "দিদি, বলুন: আপনার কী সাহায্য দরকার?"
            "te" -> "అక్కా, చెప్పండి: మీకు ఏమి సహాయం కావాలి?"
            "mr" -> "दीदी, बोला: तुम्हाला काय मदत हवी आहे?"
            else -> "दीदी, बोलकर बताएं: आपको क्या मदद चाहिए?"
        }
        binding.tvStatusText.text = greetingText

        // Tapping status card replays audio guidance
        binding.cardStatusBanner.setOnClickListener {
            playProactiveGreeting()
        }
    }

    private fun setupMicButton() {
        binding.btnMic.setOnClickListener {
            handleMicButtonTapped()
        }

        binding.layoutMicContainer.setOnClickListener {
            handleMicButtonTapped()
        }
    }

    private fun setupSchemeRecommendations() {
        val curatedSchemes = SchemeCardAdapter.getCuratedSchemesForLanguage(currentLanguageCode)
        val adapter = SchemeCardAdapter(
            schemes = curatedSchemes,
            onSchemeSelected = { selectedScheme ->
                resolveIntentQuery(selectedScheme.schemeName)
            }
        )
        binding.rvSchemeRecommendations.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL,
            false
        )
        binding.rvSchemeRecommendations.adapter = adapter
    }

    private fun setupBottomTextInput() {
        binding.btnSubmitText.setOnClickListener {
            submitTypedQuery()
        }

        binding.etQueryInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                submitTypedQuery()
                true
            } else {
                false
            }
        }
    }

    private fun submitTypedQuery() {
        val query = binding.etQueryInput.text?.toString()?.trim() ?: ""
        if (query.isNotBlank()) {
            binding.etQueryInput.text?.clear()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(binding.etQueryInput.windowToken, 0)
            resolveIntentQuery(query)
        }
    }

    /**
     * Plays proactive elder-sister spoken guidance in citizen's vernacular language.
     */
    fun playProactiveGreeting() {
        isPlayingProactiveGreeting = true
        val prompt = getProactiveNarration(currentLanguageCode)
        speechManager.speak(prompt, language = currentLanguageCode) {
            isPlayingProactiveGreeting = false
            if (!isFinishing && !isDestroyed) {
                updateMicVisualState(HubMicState.IDLE)
            }
        }
    }

    /**
     * Toggles microphone listening or cancels ongoing speech.
     */
    private fun handleMicButtonTapped() {
        if (isPlayingProactiveGreeting || speechManager.state.value == SpeechState.SPEAKING) {
            isPlayingProactiveGreeting = false
            speechManager.stop()
        }

        if (currentMicState == HubMicState.RECORDING) {
            speechManager.stop()
            updateMicVisualState(HubMicState.IDLE)
            return
        }

        checkPermissionAndStartListening()
    }

    private fun checkPermissionAndStartListening() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            startListening()
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /**
     * Activates Indic microphone capture via [SpeechManager].
     */
    fun startListening() {
        updateMicVisualState(HubMicState.RECORDING)

        speechManager.listen(
            language = currentLanguageCode,
            onVolumeChanged = { volume ->
                runOnUiThread {
                    if (currentMicState == HubMicState.RECORDING) {
                        updateMicVolumeLevel(volume)
                    }
                }
            },
            onResult = { recognizedText ->
                runOnUiThread {
                    if (recognizedText.isNotBlank()) {
                        resolveIntentQuery(recognizedText)
                    } else {
                        updateMicVisualState(HubMicState.ERROR)
                    }
                }
            },
            onError = { _ ->
                runOnUiThread {
                    updateMicVisualState(HubMicState.ERROR)
                }
            }
        )
    }

    /**
     * Updates scale and ripple glow dynamically in response to citizen voice RMS.
     */
    fun updateMicVolumeLevel(volume: Float) {
        val normVol = volume.coerceIn(0f, 1f)
        val micScale = 1.0f + (normVol * 0.18f)
        binding.btnMic.scaleX = micScale
        binding.btnMic.scaleY = micScale

        val ring1Scale = 1.0f + (normVol * 0.35f)
        binding.viewPulseRing1.scaleX = ring1Scale
        binding.viewPulseRing1.scaleY = ring1Scale
        binding.viewPulseRing1.alpha = (0.3f + normVol * 0.6f).coerceIn(0f, 1f)

        val ring2Scale = 1.0f + (normVol * 0.50f)
        binding.viewPulseRing2.scaleX = ring2Scale
        binding.viewPulseRing2.scaleY = ring2Scale
        binding.viewPulseRing2.alpha = (0.15f + normVol * 0.45f).coerceIn(0f, 1f)
    }

    /**
     * Transition microphone button visuals across IDLE, RECORDING, PROCESSING, and ERROR states.
     */
    fun updateMicVisualState(state: HubMicState) {
        currentMicState = state
        resetMicScale()

        when (state) {
            HubMicState.IDLE -> {
                val colorIdle = ContextCompat.getColor(this, R.color.mic_idle)
                val strokeIdle = ContextCompat.getColor(this, R.color.mic_idle_stroke)
                val rippleIdle = ContextCompat.getColor(this, R.color.mic_idle_ripple)

                binding.btnMic.setCardBackgroundColor(ColorStateList.valueOf(colorIdle))
                binding.btnMic.strokeColor = strokeIdle
                binding.viewPulseRing1.backgroundTintList = ColorStateList.valueOf(rippleIdle)
                binding.viewPulseRing2.backgroundTintList = ColorStateList.valueOf(rippleIdle)

                binding.tvMicPrompt.text = "बोलने के लिए माइक दबाएं (Tap to Speak)"
                startIdlePulseAnimation()
            }
            HubMicState.RECORDING -> {
                stopIdlePulseAnimation()
                val colorRec = ContextCompat.getColor(this, R.color.mic_recording)
                val strokeRec = ContextCompat.getColor(this, R.color.mic_recording_stroke)
                val rippleRec = ContextCompat.getColor(this, R.color.mic_recording_ripple)

                binding.btnMic.setCardBackgroundColor(ColorStateList.valueOf(colorRec))
                binding.btnMic.strokeColor = strokeRec
                binding.viewPulseRing1.backgroundTintList = ColorStateList.valueOf(rippleRec)
                binding.viewPulseRing2.backgroundTintList = ColorStateList.valueOf(rippleRec)

                binding.tvMicPrompt.text = "सुन रही हूँ... बोलिए (Listening...)"
                binding.tvStatusText.text = "दीदी सुन रही हैं: बोलिए..."
                binding.tvStatusSubtext.text = "अपनी आवश्यकता या योजना का नाम बोलें"
            }
            HubMicState.PROCESSING -> {
                stopIdlePulseAnimation()
                val colorProc = ContextCompat.getColor(this, R.color.mic_processing)
                val strokeProc = ContextCompat.getColor(this, R.color.mic_processing_stroke)
                val rippleProc = ContextCompat.getColor(this, R.color.mic_processing_ripple)

                binding.btnMic.setCardBackgroundColor(ColorStateList.valueOf(colorProc))
                binding.btnMic.strokeColor = strokeProc
                binding.viewPulseRing1.backgroundTintList = ColorStateList.valueOf(rippleProc)
                binding.viewPulseRing2.backgroundTintList = ColorStateList.valueOf(rippleProc)

                binding.tvMicPrompt.text = "समझ रही हूँ... (Understanding...)"
                binding.tvStatusText.text = "योजना की खोज हो रही है..."
                binding.tvStatusSubtext.text = "कृपया प्रतीक्षा करें"
            }
            HubMicState.ERROR -> {
                stopIdlePulseAnimation()
                val colorErr = ContextCompat.getColor(this, R.color.mic_error)
                val strokeErr = ContextCompat.getColor(this, R.color.mic_error_stroke)
                val rippleErr = ContextCompat.getColor(this, R.color.mic_error_ripple)

                binding.btnMic.setCardBackgroundColor(ColorStateList.valueOf(colorErr))
                binding.btnMic.strokeColor = strokeErr
                binding.viewPulseRing1.backgroundTintList = ColorStateList.valueOf(rippleErr)
                binding.viewPulseRing2.backgroundTintList = ColorStateList.valueOf(rippleErr)

                binding.tvMicPrompt.text = "दोबारा बोलने के लिए माइक दबाएं (Tap to try again)"
                binding.tvStatusText.text = "आवाज़ सुनाई नहीं दी"
                binding.tvStatusSubtext.text = "कृपया माइक दबाकर दोबारा बोलें"
            }
        }
    }

    private fun resetMicScale() {
        binding.btnMic.scaleX = 1.0f
        binding.btnMic.scaleY = 1.0f
        binding.viewPulseRing1.scaleX = 1.0f
        binding.viewPulseRing1.scaleY = 1.0f
        binding.viewPulseRing2.scaleX = 1.0f
        binding.viewPulseRing2.scaleY = 1.0f
    }

    private fun startIdlePulseAnimation() {
        idlePulseAnimator?.cancel()
        idlePulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                val ring1Scale = 1.0f + (fraction * 0.12f)
                val ring2Scale = 1.0f + (fraction * 0.20f)
                binding.viewPulseRing1.scaleX = ring1Scale
                binding.viewPulseRing1.scaleY = ring1Scale
                binding.viewPulseRing1.alpha = 0.25f + (fraction * 0.20f)
                binding.viewPulseRing2.scaleX = ring2Scale
                binding.viewPulseRing2.scaleY = ring2Scale
                binding.viewPulseRing2.alpha = 0.10f + (fraction * 0.15f)
            }
            start()
        }
    }

    private fun stopIdlePulseAnimation() {
        idlePulseAnimator?.cancel()
        idlePulseAnimator = null
    }

    /**
     * Resolves voice or text citizen inquiry via FastAPI backend or local fallback.
     */
    fun resolveIntentQuery(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return

        speechManager.stop()
        isPlayingProactiveGreeting = false
        updateMicVisualState(HubMicState.PROCESSING)
        binding.tvStatusText.text = "“$cleanQuery” की खोज जारी है..."

        lifecycleScope.launch {
            val result = apiService.resolveIntent(cleanQuery, currentLanguageCode)
            if (result.isSuccess) {
                handleIntentResolved(result.getOrNull()!!)
            } else {
                // Defensive local fallback for offline / spotty rural network
                val localMatch = matchSchemeLocally(cleanQuery, currentLanguageCode)
                if (localMatch != null) {
                    val fallbackResponse = IntentResponse(
                        scheme_id = localMatch.schemeId,
                        scheme_name = localMatch.schemeName,
                        portal_url = localMatch.portalUrl,
                        confirmation_speech = localMatch.confirmationSpeech,
                        initial_prompt = localMatch.initialPrompt
                    )
                    handleIntentResolved(fallbackResponse)
                } else {
                    updateMicVisualState(HubMicState.ERROR)
                    val errorMsg = getUnrecognizedSpeechMessage(currentLanguageCode)
                    binding.tvStatusText.text = errorMsg
                    binding.tvStatusSubtext.text = "कृपया पुनः प्रयास करें"
                    speechManager.speak(errorMsg, language = currentLanguageCode)
                }
            }
        }
    }

    /**
     * Executes the post-resolution flow: speaks confirmation, checks overlay permission,
     * starts SpotlightOverlayService, and opens portal URL.
     */
    internal fun handleIntentResolved(response: IntentResponse) {
        updateMicVisualState(HubMicState.IDLE)
        binding.tvStatusText.text = response.schemeName
        binding.tvStatusSubtext.text = "पोर्टल खोला जा रहा है..."

        // 1. Spoken confirmation
        if (response.confirmationSpeech.isNotBlank()) {
            speechManager.speak(response.confirmationSpeech, language = currentLanguageCode)
        }

        // 2. Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            val overlaySettingsIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(overlaySettingsIntent)
            } catch (e: Exception) {
                // Defensive handling if activity is unavailable on custom ROM
            }
        }

        // 3. Start SpotlightOverlayService
        SpotlightOverlayService.start(
            context = this,
            schemeId = response.schemeId,
            portalUrl = response.portalUrl,
            initialPrompt = response.initialPrompt,
            language = currentLanguageCode
        )

        // 4. Open portal URL in external browser
        if (response.portalUrl.isNotBlank()) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(response.portalUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(browserIntent)
            } catch (e: Exception) {
                // Handle missing browser intent handler
            }
        }
    }

    override fun onStop() {
        super.onStop()
        isPlayingProactiveGreeting = false
        speechManager.stop()
        stopIdlePulseAnimation()
    }

    override fun onDestroy() {
        super.onDestroy()
        isPlayingProactiveGreeting = false
        speechManager.destroy()
        stopIdlePulseAnimation()
    }
}
