package com.sahaayika.app.ui.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.sahaayika.R
import com.sahaayika.app.audio.SpeechManager
import com.sahaayika.app.data.api.SahaayikaApiService
import com.sahaayika.app.data.model.ScreenAnalysisResponse
import com.sahaayika.app.data.profile.DidiBatuaManager
import com.sahaayika.app.grounding.CoordinateTransformer
import com.sahaayika.app.service.SahaayikaAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * System Overlay Service orchestrating Screen 3 of Sahaayika (सहायिका).
 *
 * Manages the full-screen visual spotlight aperture, draggable Didi avatar, and high-contrast
 * subtitle banner on top of external government scheme portals.
 *
 * Responsibilities:
 * 1. WindowManager overlay lifecycle with [WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE],
 *    [WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL], and [WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN].
 * 2. Silent native-res screenshot ingestion via [SahaayikaAccessibilityService] and Gemini visual grounding.
 * 3. 7-second Inactivity Watchdog prompting citizens in vernacular speech if confused.
 * 4. Didi Batua wallet integration prompting automated Aadhaar/ID auto-fill.
 * 5. Push-to-Talk conversational Q&A via Didi persona.
 */
class SpotlightOverlayService : Service() {

    companion object {
        const val EXTRA_SCHEME_ID = "extra_scheme_id"
        const val EXTRA_PORTAL_URL = "extra_portal_url"
        const val EXTRA_INITIAL_PROMPT = "extra_initial_prompt"
        const val EXTRA_LANGUAGE = "extra_language"

        const val ACTION_START_SPOTLIGHT = "com.sahaayika.app.ACTION_START_SPOTLIGHT"
        const val ACTION_STOP_SPOTLIGHT = "com.sahaayika.app.ACTION_STOP_SPOTLIGHT"
        const val ACTION_CAPTURE_SCREEN = "com.sahaayika.app.ACTION_CAPTURE_SCREEN"

        const val NOTIFICATION_CHANNEL_ID = "sahaayika_spotlight_overlay_channel"
        const val NOTIFICATION_ID = 2001

        const val INACTIVITY_TIMEOUT_MS = 7000L

        // Overlay WindowManager Configurations
        const val OVERLAY_WINDOW_TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        const val OVERLAY_LAYOUT_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        // Vernacular Prompts
        const val REMINDER_HINDI = "दीदी, स्क्रीन पर जो पीला घेरा चमक रहा है, उसपर एक बार उँगली दबाएं।"
        const val AADHAAR_PROMPT_HINDI = "दीदी, क्या मैं आपका आधार नंबर भर दूँ?"
        const val DIDI_HELP_PROMPT_HINDI = "दीदी, क्या मदद चाहिए?"

        fun getVernacularInactivityReminder(language: String): String {
            val lang = language.lowercase()
            return when {
                lang.startsWith("ta") -> "அக்கா, திரையில் ஒளிரும் மஞ்சள் வட்டத்தை ஒருமுறை தட்டவும்."
                lang.startsWith("bn") -> "দিদি, স্ক্রিনে যে হলুদ বৃত্তটি জ্বলছে, সেখানে একবার স্পর্শ করুন।"
                lang.startsWith("te") -> "అక్కా, స్క్రీన్‌పై మెరుస్తున్న పసుపు రంగు వలయాన్ని ఒక్కసారి తాకండి."
                lang.startsWith("mr") -> "दीदी, स्क्रीनवर चमकणाऱ्या पिवळ्या वर्तुळावर एकदा बोट दाबा."
                lang.startsWith("gu") -> "દીદી, સ્ક્રીન પર જે પીળું કુંડાળું ચમકે છે, તેના પર એક વાર આંગળી દબાવો."
                lang.startsWith("en") -> "Didi, tap once on the shining yellow circle on the screen."
                else -> REMINDER_HINDI
            }
        }

        fun getAadhaarVoicePrompt(language: String): String {
            val lang = language.lowercase()
            return when {
                lang.startsWith("ta") -> "அக்கா, உங்கள் ஆதார் எண்ணை நான் நிரப்பவா?"
                lang.startsWith("bn") -> "দিদি, আমি কি আপনার আধার নম্বরটি পূরণ করব?"
                lang.startsWith("te") -> "అక్కా, మీ ఆధార్ నంబర్‌ను ನಾನು నమోదు చేయమంటారా?"
                lang.startsWith("mr") -> "दीदी, मी तुमचा आधार क्रमांक भरू का?"
                lang.startsWith("gu") -> "દીદી, શું હું તમારો આધાર નંબર ભરી દઉં?"
                lang.startsWith("en") -> "Didi, should I fill in your Aadhaar number?"
                else -> AADHAAR_PROMPT_HINDI
            }
        }

        fun getAvatarHelpPrompt(language: String): String {
            val lang = language.lowercase()
            return when {
                lang.startsWith("ta") -> "அக்கா, என்ன உதவி வேண்டும்?"
                lang.startsWith("bn") -> "দিদি, কী সাহায্য লাগবে?"
                lang.startsWith("te") -> "అక్కా, ఏమి సహాయం కావాలి?"
                lang.startsWith("mr") -> "दीदी, काय मदत हवी आहे?"
                lang.startsWith("gu") -> "દીદી, શું મદદ જોઈએ છે?"
                lang.startsWith("en") -> "Didi, how can I help you?"
                else -> DIDI_HELP_PROMPT_HINDI
            }
        }

        fun createStartIntent(
            context: Context,
            schemeId: String,
            portalUrl: String,
            initialPrompt: String? = null,
            language: String = "hi-IN"
        ): Intent {
            return Intent(context, SpotlightOverlayService::class.java).apply {
                action = ACTION_START_SPOTLIGHT
                putExtra(EXTRA_SCHEME_ID, schemeId)
                putExtra(EXTRA_PORTAL_URL, portalUrl)
                putExtra(EXTRA_LANGUAGE, language)
                if (initialPrompt != null) {
                    putExtra(EXTRA_INITIAL_PROMPT, initialPrompt)
                }
            }
        }

        fun start(
            context: Context,
            schemeId: String,
            portalUrl: String,
            initialPrompt: String? = null,
            language: String = "hi-IN"
        ) {
            val intent = createStartIntent(context, schemeId, portalUrl, initialPrompt, language)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Defensive handling if service start is restricted by background limits
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SpotlightOverlayService::class.java).apply {
                action = ACTION_STOP_SPOTLIGHT
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // Ignored
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null

    // Overlay View Hierarchy
    lateinit var spotlightCanvasView: SpotlightCanvasView
    lateinit var didiAvatarView: FloatingDidiAvatarView
    lateinit var subtitleBannerView: SubtitleBannerView

    // Layout Params for overlay views
    private var canvasParams: WindowManager.LayoutParams? = null
    private var avatarParams: WindowManager.LayoutParams? = null
    private var bannerParams: WindowManager.LayoutParams? = null

    // Injected / Configured Dependencies
    var apiService: SahaayikaApiService = SahaayikaApiService()
    var speechManager: SpeechManager? = null
    var didiBatuaManager: DidiBatuaManager? = null

    // Operational State
    var currentSchemeId: String = ""
    var currentPortalUrl: String = ""
    var currentLanguage: String = "hi-IN"
    var currentStep: String? = null

    private var watchdogJob: Job? = null
    private var isViewsAttached: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        speechManager = SpeechManager(this)
        didiBatuaManager = DidiBatuaManager(this)

        initViews()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SPOTLIGHT) {
            stopSelf()
            return START_NOT_STICKY
        }

        intent?.let {
            currentSchemeId = it.getStringExtra(EXTRA_SCHEME_ID) ?: currentSchemeId
            currentPortalUrl = it.getStringExtra(EXTRA_PORTAL_URL) ?: currentPortalUrl
            currentLanguage = it.getStringExtra(EXTRA_LANGUAGE) ?: currentLanguage
            val initialPrompt = it.getStringExtra(EXTRA_INITIAL_PROMPT)

            attachOverlayViews()

            if (!initialPrompt.isNullOrBlank()) {
                subtitleBannerView.setSubtitle(initialPrompt)
                speechManager?.speak(initialPrompt, currentLanguage) {
                    resetInactivityWatchdog()
                }
            }

            // Trigger initial screen analysis after portal loads
            serviceScope.launch {
                delay(1500L)
                captureAndProcessScreen()
            }
        }

        if (intent?.action == ACTION_CAPTURE_SCREEN) {
            captureAndProcessScreen()
        }

        return START_STICKY
    }

    private fun initViews() {
        spotlightCanvasView = SpotlightCanvasView(this).apply {
            onUserInteraction = {
                resetInactivityWatchdog()
            }
        }

        didiAvatarView = FloatingDidiAvatarView(this).apply {
            onAvatarClicked = {
                handleAvatarClicked()
            }
        }

        subtitleBannerView = SubtitleBannerView(this)

        // Setup accessibility event listener to react to page navigation
        SahaayikaAccessibilityService.onWindowStateChangedListener = {
            resetInactivityWatchdog()
            serviceScope.launch {
                delay(1200L)
                captureAndProcessScreen()
            }
        }
    }

    private fun attachOverlayViews() {
        val wm = windowManager ?: return
        if (isViewsAttached) return

        // 1. Fullscreen Spotlight Canvas View
        canvasParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OVERLAY_WINDOW_TYPE,
            OVERLAY_LAYOUT_FLAGS,
            PixelFormat.TRANSLUCENT
        )

        // 2. Floating Didi Avatar View (interactive, draggable, push-to-talk)
        avatarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OVERLAY_WINDOW_TYPE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(16f).toInt()
            y = dpToPx(180f).toInt()
        }
        didiAvatarView.attachToWindowManager(wm, avatarParams!!)

        // 3. Subtitle Banner View (bottom-centered, does not consume touch)
        bannerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OVERLAY_WINDOW_TYPE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dpToPx(72f).toInt()
        }

        try {
            wm.addView(spotlightCanvasView, canvasParams)
            wm.addView(didiAvatarView, avatarParams)
            wm.addView(subtitleBannerView, bannerParams)
            isViewsAttached = true
        } catch (e: Exception) {
            // Permission or WindowManager error
        }
    }

    private fun detachOverlayViews() {
        val wm = windowManager ?: return
        if (!isViewsAttached) return

        try {
            wm.removeViewImmediate(spotlightCanvasView)
        } catch (e: Exception) { /* Ignored */ }

        try {
            wm.removeViewImmediate(didiAvatarView)
        } catch (e: Exception) { /* Ignored */ }

        try {
            wm.removeViewImmediate(subtitleBannerView)
        } catch (e: Exception) { /* Ignored */ }

        isViewsAttached = false
    }

    /**
     * Captures a silent native-resolution screen snapshot via the accessibility service and processes it.
     */
    fun captureAndProcessScreen() {
        didiAvatarView.setThinkingState(true)
        SahaayikaAccessibilityService.captureSnapshot(
            onSuccess = { jpegBytes ->
                processScreenSnapshot(jpegBytes)
            },
            onError = {
                didiAvatarView.setThinkingState(false)
            }
        )
    }

    /**
     * Core multimodal grounding pipeline:
     * - Sends screen capture to Gemini backend
     * - Paints spotlight cutout on targetRect
     * - Speaks vernacular guidance & displays subtitles
     * - Handles CAPTCHAs and prompts for Didi Batua auto-fill
     */
    fun processScreenSnapshot(jpegBytes: ByteArray) {
        didiAvatarView.setThinkingState(true)

        serviceScope.launch {
            val responseResult = apiService.analyzeScreen(
                imageBytes = jpegBytes,
                schemeId = currentSchemeId,
                currentStep = currentStep,
                language = currentLanguage
            )

            didiAvatarView.setThinkingState(false)

            if (responseResult.isSuccess) {
                val response = responseResult.getOrThrow()
                handleScreenAnalysisResult(response)
            } else {
                // If API fails, stop thinking state and reset watchdog
                resetInactivityWatchdog()
            }
        }
    }

    private fun handleScreenAnalysisResult(response: ScreenAnalysisResponse) {
        // 1. Visual Grounding & Spotlight Highlight
        if (response.action.equals("HIGHLIGHT", ignoreCase = true) && response.box2d.size >= 4) {
            val metrics = resources.displayMetrics
            val pixelRect = CoordinateTransformer.transform(
                box2d = response.box2d,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels
            )
            spotlightCanvasView.setTargetRect(pixelRect?.toRectF())
        } else {
            spotlightCanvasView.clearTarget()
        }

        // 2. High-Contrast Subtitle Display
        subtitleBannerView.setSubtitle(response.subtitleText)

        // 3. Vernacular Spoken Guidance
        if (response.spokenGuidance.isNotBlank()) {
            speechManager?.speak(response.spokenGuidance, currentLanguage) {
                resetInactivityWatchdog()
            }
        } else {
            resetInactivityWatchdog()
        }

        // 4. CAPTCHA Assistance
        if (!response.captchaCode.isNullOrBlank()) {
            val captchaMsg = when {
                currentLanguage.startsWith("ta") -> "கேப்ட்சா குறியீடு ${response.captchaCode}."
                currentLanguage.startsWith("en") -> "The CAPTCHA code is ${response.captchaCode}."
                else -> "कैप्चा कोड है ${response.captchaCode}।"
            }
            speechManager?.speak(captchaMsg, currentLanguage)
        }

        // 5. Didi Batua Aadhaar/ID auto-fill prompt
        val field = response.fieldType?.lowercase() ?: ""
        val isAadhaarField = field.contains("aadhaar") || field.contains("samagra") || field.contains("id")
        val batua = didiBatuaManager

        if (isAadhaarField && batua != null && batua.hasProfile()) {
            val promptText = getAadhaarVoicePrompt(currentLanguage)
            speechManager?.speakThenListen(promptText, currentLanguage) { answer ->
                if (answer.contains("हाँ") || answer.contains("ha") || answer.contains("yes", ignoreCase = true)) {
                    val savedAadhaar = batua.getAadhaar()
                    if (!savedAadhaar.isNullOrBlank()) {
                        val formatted = savedAadhaar.chunked(4).joinToString(" ")
                        val confirmMsg = when {
                            currentLanguage.startsWith("en") -> "Your Aadhaar number is $formatted."
                            else -> "आपका आधार नंबर है: $formatted"
                        }
                        speechManager?.speak(confirmMsg, currentLanguage)
                    }
                }
            }
        }
    }

    /**
     * Inactivity Watchdog: 7-second coroutine delay.
     * If citizen does not interact with the screen, speaks a reassuring vernacular reminder.
     */
    fun resetInactivityWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            delay(INACTIVITY_TIMEOUT_MS)
            if (spotlightCanvasView.getTargetRect() != null) {
                val reminder = getVernacularInactivityReminder(currentLanguage)
                subtitleBannerView.setSubtitle(reminder)
                speechManager?.speak(reminder, currentLanguage) {
                    resetInactivityWatchdog()
                }
            }
        }
    }

    /**
     * Push-to-Talk action triggered when citizen taps floating Didi avatar.
     */
    private fun handleAvatarClicked() {
        speechManager?.stop()
        watchdogJob?.cancel()

        val prompt = getAvatarHelpPrompt(currentLanguage)
        subtitleBannerView.setSubtitle(prompt)

        speechManager?.speakThenListen(prompt, currentLanguage) { userQuestion ->
            if (userQuestion.isNotBlank()) {
                didiAvatarView.setThinkingState(true)
                serviceScope.launch {
                    val didiResult = apiService.askDidi(userQuestion, currentLanguage)
                    didiAvatarView.setThinkingState(false)
                    if (didiResult.isSuccess) {
                        val askResponse = didiResult.getOrThrow()
                        subtitleBannerView.setSubtitle(askResponse.subtitleText)
                        speechManager?.speak(askResponse.spokenAnswer, currentLanguage) {
                            resetInactivityWatchdog()
                        }
                    } else {
                        val fallback = when {
                            currentLanguage.startsWith("en") -> "Sorry, I could not understand. Please ask again."
                            else -> "माफ़ कीजिए, मैं समझ नहीं पाई। कृपया दोबारा पूछें।"
                        }
                        subtitleBannerView.setSubtitle(fallback)
                        speechManager?.speak(fallback, currentLanguage) {
                            resetInactivityWatchdog()
                        }
                    }
                }
            } else {
                resetInactivityWatchdog()
            }
        }
    }

    private fun startForegroundNotification() {
        createNotificationChannel()

        val stopIntent = Intent(this, SpotlightOverlayService::class.java).apply {
            action = ACTION_STOP_SPOTLIGHT
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_didi_assist)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.overlay_notification_stop),
                stopPendingIntent
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "सहायिका दीदी गाइड (Sahaayika Guide)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Government scheme visual spotlight and voice guidance"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        watchdogJob?.cancel()
        serviceScope.cancel()

        SahaayikaAccessibilityService.onWindowStateChangedListener = null
        detachOverlayViews()

        speechManager?.destroy()
        speechManager = null
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }
}
