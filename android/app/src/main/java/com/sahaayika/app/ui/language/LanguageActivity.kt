package com.sahaayika.app.ui.language

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sahaayika.databinding.ActivityLanguageBinding
import com.sahaayika.app.audio.SpeechManager
import com.sahaayika.app.data.model.Language
import com.sahaayika.app.data.profile.DidiBatuaManager
import com.sahaayika.app.data.profile.UserPreferences
import com.sahaayika.app.ui.hub.GuidedHubActivity

/**
 * Screen 1: Language Onboarding Activity (अपनी भाषा चुनें).
 *
 * Welcomes rural citizens with proactive bilingual Indic audio narration on launch,
 * presents large high-contrast touch targets for core Indic languages, and navigates
 * to GuidedHubActivity upon language selection.
 */
class LanguageActivity : AppCompatActivity() {

    companion object {
        const val WELCOME_HINDI = "नमस्ते! नीचे दी गई सूची में से अपनी भाषा पर उँगली रखकर चुनें।"
        const val WELCOME_ENGLISH = "Welcome. Please tap your language."
    }

    private lateinit var binding: ActivityLanguageBinding
    internal lateinit var speechManager: SpeechManager
    private lateinit var userPreferences: UserPreferences
    private lateinit var didiBatuaManager: DidiBatuaManager
    private var bounceAnimator: ObjectAnimator? = null
    private var isSpeakingWelcome = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!::speechManager.isInitialized) {
            speechManager = SpeechManager(this)
        }
        userPreferences = UserPreferences(this)
        didiBatuaManager = DidiBatuaManager(this)

        setupRecyclerView()
        setupBottomBanner()
        setupListeners()
    }

    override fun onStart() {
        super.onStart()
        startBouncingIndicator()
        // Proactively speak bilingual welcome audio on mount
        binding.root.postDelayed({
            if (!isFinishing && !isDestroyed) {
                speakWelcomeAudio()
            }
        }, 300)
    }

    private fun setupRecyclerView() {
        val adapter = LanguageAdapter(
            languages = Language.CORE_LANGUAGES,
            onLanguageSelected = { selectedLanguage ->
                handleLanguageSelected(selectedLanguage)
            },
            onSpeakerAssist = { selectedLanguage ->
                speechManager.speak(
                    text = "${selectedLanguage.nativeName}. ${selectedLanguage.englishName}.",
                    language = selectedLanguage.code
                )
            }
        )

        binding.rvLanguages.apply {
            layoutManager = LinearLayoutManager(this@LanguageActivity)
            this.adapter = adapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy > 4 || recyclerView.computeVerticalScrollOffset() > 20) {
                        dismissBottomBanner()
                    }
                }
            })
        }
    }

    private fun setupBottomBanner() {
        binding.layoutBottomBanner.setOnClickListener {
            // Scroll down a bit to show more languages
            binding.rvLanguages.smoothScrollBy(0, 300)
        }
    }

    private fun setupListeners() {
        // Tapping subtitle replays bilingual welcome audio
        binding.layoutSubtitle.setOnClickListener {
            speakWelcomeAudio()
        }
    }

    private fun startBouncingIndicator() {
        bounceAnimator?.cancel()
        bounceAnimator = ObjectAnimator.ofFloat(binding.ivBouncingArrow, "translationY", 0f, 14f).apply {
            duration = 750
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun dismissBottomBanner() {
        if (binding.layoutBottomBanner.visibility == View.VISIBLE && binding.layoutBottomBanner.alpha > 0f) {
            binding.layoutBottomBanner.animate()
                .alpha(0f)
                .setDuration(250)
                .withEndAction {
                    binding.layoutBottomBanner.visibility = View.GONE
                    bounceAnimator?.cancel()
                }
                .start()
        }
    }

    /**
     * Speaks bilingual welcome greeting: Hindi prompt first, followed by English prompt.
     */
    fun speakWelcomeAudio() {
        isSpeakingWelcome = true
        speechManager.speak(WELCOME_HINDI, language = "hi-IN") {
            if (!isFinishing && !isDestroyed && isSpeakingWelcome) {
                speechManager.speak(WELCOME_ENGLISH, language = "en-IN") {
                    isSpeakingWelcome = false
                }
            }
        }
    }

    /**
     * Halts TTS immediately, saves language preference, and proceeds to GuidedHubActivity.
     */
    fun handleLanguageSelected(language: Language) {
        isSpeakingWelcome = false
        speechManager.stop()

        userPreferences.saveSelectedLanguage(language.code)
        didiBatuaManager.saveLanguage(language.code)

        val intent = GuidedHubActivity.createIntent(this, language.code)
        startActivity(intent)
        finish()
    }

    override fun onStop() {
        super.onStop()
        isSpeakingWelcome = false
        speechManager.stop()
        bounceAnimator?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        isSpeakingWelcome = false
        speechManager.destroy()
        bounceAnimator?.cancel()
    }
}
