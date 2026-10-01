package com.sahaayika.app

import android.content.Context
import android.graphics.RectF
import android.view.WindowManager
import com.sahaayika.app.data.model.ScreenAnalysisResponse
import com.sahaayika.app.grounding.CoordinateTransformer
import com.sahaayika.app.ui.overlay.FloatingDidiAvatarView
import com.sahaayika.app.ui.overlay.SpotlightCanvasView
import com.sahaayika.app.ui.overlay.SpotlightOverlayService
import com.sahaayika.app.ui.overlay.SubtitleBannerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive Unit Tests for Screen 3:
 * System Overlay, Custom Canvas, Floating Avatar & Inactivity Watchdog.
 *
 * Verifies:
 * 1. WindowManager LayoutParams types, flags and touch pass-through configuration.
 * 2. 7-second Inactivity Watchdog timeout constants and multi-lingual vernacular prompts.
 * 3. Didi Batua Aadhaar/ID auto-fill prompt texts across Indic languages.
 * 4. Conversational Push-to-Talk Didi inquiry prompts.
 * 5. Multimodal visual grounding coordinate conversion pipelines.
 * 6. Visual styling and paint color constants for Spotlight Canvas, Floating Avatar, and Subtitle Banner.
 */
class OverlayServiceTest {

    // ==========================================
    // 1. WindowManager Overlay Configuration Tests
    // ==========================================

    @Test
    fun testOverlayWindowType_IsApplicationOverlay() {
        assertEquals(
            "Overlay window type must be TYPE_APPLICATION_OVERLAY for Android O+",
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SpotlightOverlayService.OVERLAY_WINDOW_TYPE
        )
    }

    @Test
    fun testOverlayLayoutFlags_IncludesNotFocusableNotTouchModalAndLayoutInScreen() {
        val flags = SpotlightOverlayService.OVERLAY_LAYOUT_FLAGS

        val hasNotFocusable = (flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0
        val hasNotTouchModal = (flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) != 0
        val hasLayoutInScreen = (flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN) != 0

        assertTrue("Overlay flags must contain FLAG_NOT_FOCUSABLE so browser retains focus", hasNotFocusable)
        assertTrue("Overlay flags must contain FLAG_NOT_TOUCH_MODAL so taps outside reach underlying window", hasNotTouchModal)
        assertTrue("Overlay flags must contain FLAG_LAYOUT_IN_SCREEN for full viewport coverage", hasLayoutInScreen)
    }

    // ==========================================
    // 2. 7-Second Inactivity Watchdog Tests
    // ==========================================

    @Test
    fun testInactivityWatchdogTimeout_IsExactlySevenSeconds() {
        assertEquals(
            "Inactivity watchdog must be configured to exactly 7000 milliseconds (7s)",
            7000L,
            SpotlightOverlayService.INACTIVITY_TIMEOUT_MS
        )
    }

    @Test
    fun testInactivityReminderText_HindiPrompt() {
        val reminder = SpotlightOverlayService.getVernacularInactivityReminder("hi-IN")
        assertEquals(SpotlightOverlayService.REMINDER_HINDI, reminder)
        assertTrue(reminder.contains("पीला घेरा"))
        assertTrue(reminder.contains("उँगली दबाएं"))
    }

    @Test
    fun testInactivityReminderText_MultiLingualSupport() {
        val english = SpotlightOverlayService.getVernacularInactivityReminder("en-IN")
        assertTrue(english.contains("yellow circle"))

        val tamil = SpotlightOverlayService.getVernacularInactivityReminder("ta-IN")
        assertTrue(tamil.contains("மஞ்சள் வட்டத்தை"))

        val bengali = SpotlightOverlayService.getVernacularInactivityReminder("bn-IN")
        assertTrue(bengali.contains("হলুদ বৃত্তটি"))

        val telugu = SpotlightOverlayService.getVernacularInactivityReminder("te-IN")
        assertTrue(telugu.contains("పసుపు రంగు"))

        val marathi = SpotlightOverlayService.getVernacularInactivityReminder("mr-IN")
        assertTrue(marathi.contains("पिवळ्या वर्तुळावर"))

        val gujarati = SpotlightOverlayService.getVernacularInactivityReminder("gu-IN")
        assertTrue(gujarati.contains("પીળું કુંડાળું"))

        // Fallback for unknown language returns Hindi
        val unknown = SpotlightOverlayService.getVernacularInactivityReminder("unknown-code")
        assertEquals(SpotlightOverlayService.REMINDER_HINDI, unknown)
    }

    // ==========================================
    // 3. Didi Batua Auto-Fill Voice Prompt Tests
    // ==========================================

    @Test
    fun testAadhaarVoicePrompt_HindiPrompt() {
        val prompt = SpotlightOverlayService.getAadhaarVoicePrompt("hi-IN")
        assertEquals(SpotlightOverlayService.AADHAAR_PROMPT_HINDI, prompt)
        assertTrue(prompt.contains("आधार नंबर भर दूँ"))
    }

    @Test
    fun testAadhaarVoicePrompt_MultiLingualSupport() {
        val english = SpotlightOverlayService.getAadhaarVoicePrompt("en-IN")
        assertTrue(english.contains("Aadhaar"))

        val tamil = SpotlightOverlayService.getAadhaarVoicePrompt("ta-IN")
        assertTrue(tamil.contains("ஆதார்"))

        val bengali = SpotlightOverlayService.getAadhaarVoicePrompt("bn-IN")
        assertTrue(bengali.contains("আধার"))

        val telugu = SpotlightOverlayService.getAadhaarVoicePrompt("te-IN")
        assertTrue(telugu.contains("ఆధార్"))

        val marathi = SpotlightOverlayService.getAadhaarVoicePrompt("mr-IN")
        assertTrue(marathi.contains("आधार क्रमांक"))
    }

    // ==========================================
    // 4. Push-to-Talk Didi Question Prompt Tests
    // ==========================================

    @Test
    fun testAvatarHelpPrompt_HindiPrompt() {
        val help = SpotlightOverlayService.getAvatarHelpPrompt("hi-IN")
        assertEquals(SpotlightOverlayService.DIDI_HELP_PROMPT_HINDI, help)
        assertTrue(help.contains("क्या मदद चाहिए"))
    }

    @Test
    fun testAvatarHelpPrompt_MultiLingualSupport() {
        val english = SpotlightOverlayService.getAvatarHelpPrompt("en-IN")
        assertTrue(english.contains("help you"))

        val tamil = SpotlightOverlayService.getAvatarHelpPrompt("ta-IN")
        assertTrue(tamil.contains("உதவி"))

        val bengali = SpotlightOverlayService.getAvatarHelpPrompt("bn-IN")
        assertTrue(bengali.contains("সাহায্য"))

        val telugu = SpotlightOverlayService.getAvatarHelpPrompt("te-IN")
        assertTrue(telugu.contains("సహాయం"))

        val marathi = SpotlightOverlayService.getAvatarHelpPrompt("mr-IN")
        assertTrue(marathi.contains("मदत"))
    }

    // ==========================================
    // 5. Visual Grounding & Coordinate Pipeline Tests
    // ==========================================

    @Test
    fun testScreenAnalysisToHighlightPipeline() {
        val screenWidth = 1080
        val screenHeight = 2400

        // Simulated Gemini API visual grounding response
        val response = ScreenAnalysisResponse(
            action = "HIGHLIGHT",
            box_2d = listOf(350, 150, 450, 850), // ymin, xmin, ymax, xmax
            spoken_guidance = "कृपया यहाँ अपना आधार नंबर दर्ज करें।",
            subtitle_text = "आधार नंबर दर्ज करें",
            field_type = "aadhaar_input"
        )

        assertEquals("HIGHLIGHT", response.action)
        assertEquals(4, response.box2d.size)

        val pixelRect = CoordinateTransformer.transform(
            box2d = response.box2d,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            paddingPx = 16f
        )

        assertNotNull(pixelRect)
        pixelRect!!

        // Normalized: left = 0.150 * 1080 = 162f, with 16px padding -> 146f
        assertEquals(146f, pixelRect.left, 0.01f)
        // Normalized: top = 0.350 * 2400 = 840f, with 16px padding -> 824f
        assertEquals(824f, pixelRect.top, 0.01f)
        // Normalized: right = 0.850 * 1080 = 918f, with 16px padding -> 934f
        assertEquals(934f, pixelRect.right, 0.01f)
        // Normalized: bottom = 0.450 * 2400 = 1080f, with 16px padding -> 1096f
        assertEquals(1096f, pixelRect.bottom, 0.01f)

        // Pure Kotlin PixelRect dimension and center checks
        assertEquals(788f, pixelRect.width, 0.01f)
        assertEquals(272f, pixelRect.height, 0.01f)
        assertEquals(540f, pixelRect.centerX, 0.01f)
        assertEquals(960f, pixelRect.centerY, 0.01f)
        assertNotNull(pixelRect.toRectF())
    }

    @Test
    fun testScreenAnalysisNonHighlight_ReturnsNull() {
        val screenWidth = 1080
        val screenHeight = 2400

        val response = ScreenAnalysisResponse(
            action = "WAIT_FOR_SUBMIT",
            box_2d = emptyList(),
            spoken_guidance = "कृपया आगे बढ़ने के लिए प्रतीक्षा करें।",
            subtitle_text = "प्रतीक्षा करें..."
        )

        val pixelRect = CoordinateTransformer.transform(
            box2d = response.box2d,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
        assertNull(pixelRect)
    }

    // ==========================================
    // 6. Visual Component Constants Tests
    // ==========================================

    @Test
    fun testSpotlightCanvasConstants_ColorsAndBorders() {
        // Scrim Color: 70% transparent black (#B3000000)
        assertEquals(0xB3000000.toInt(), SpotlightCanvasView.COLOR_SCRIM)

        // Border Color: Gold / Yellow (#FFD700)
        assertEquals(0xFFFFD700.toInt(), SpotlightCanvasView.COLOR_BORDER_GOLD)

        // Border width limits
        assertEquals(5f, SpotlightCanvasView.DEFAULT_BORDER_WIDTH_DP, 0.001f)
        assertEquals(4f, SpotlightCanvasView.MIN_BORDER_WIDTH_DP, 0.001f)
        assertEquals(7f, SpotlightCanvasView.MAX_BORDER_WIDTH_DP, 0.001f)
        assertEquals(12f, SpotlightCanvasView.CORNER_RADIUS_DP, 0.001f)
    }

    @Test
    fun testSubtitleBannerConstants_ColorsAndPillRadius() {
        // Dark slate pill background (#1E1E1E with 0.95 alpha)
        assertEquals(0xF21E1E1E.toInt(), SubtitleBannerView.COLOR_BACKGROUND_SLATE)

        // High contrast yellow text (#FFEB3B)
        assertEquals(0xFFFFEB3B.toInt(), SubtitleBannerView.COLOR_TEXT_YELLOW)

        // Rounded pill corner radius
        assertEquals(24f, SubtitleBannerView.CORNER_RADIUS_DP, 0.001f)
    }

    @Test
    fun testFloatingDidiAvatarConstants_DimensionsAndColors() {
        assertEquals(64f, FloatingDidiAvatarView.AVATAR_SIZE_DP, 0.001f)
        assertEquals(78f, FloatingDidiAvatarView.RIPPLE_SIZE_DP, 0.001f)
        assertEquals(0xFFFFD700.toInt(), FloatingDidiAvatarView.GOLD_COLOR)
        assertEquals(0xFFE65100.toInt(), FloatingDidiAvatarView.SAFFRON_COLOR)
    }
}
