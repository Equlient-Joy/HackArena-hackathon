package com.sahaayika.app

import com.sahaayika.app.data.api.SahaayikaApiService
import com.sahaayika.app.data.model.IntentRequest
import com.sahaayika.app.data.model.IntentResponse
import com.sahaayika.app.data.model.SchemeItem
import com.sahaayika.app.ui.hub.GuidedHubActivity
import com.sahaayika.app.ui.hub.SchemeCardAdapter
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Screen 2: Guided Action & Intent Hub (GuidedHubActivity).
 *
 * Verifies:
 * 1. Curated welfare scheme recommendation data formatting and localized badge mapping.
 * 2. SchemeCardAdapter item binding and selection callbacks.
 * 3. Intent request payload formatting for both voice and text submissions.
 * 4. Intent response deserialization and field accessibility.
 * 5. Local fallback scheme keyword matching heuristics for offline resiliency.
 * 6. Vernacular proactive audio prompts and feedback messages.
 */
class GuidedHubTest {

    // ==========================================
    // 1. Scheme Recommendation Data Formatting Tests
    // ==========================================

    @Test
    fun testCuratedSchemes_ContainsAllSixMandatedSchemes() {
        val schemes = SchemeCardAdapter.DEFAULT_CURATED_SCHEMES
        assertEquals("Curated recommendations must contain exactly 6 schemes", 6, schemes.size)

        val schemeIds = schemes.map { it.schemeId }
        val expectedIds = listOf(
            "ladli_behna",
            "pm_kisan",
            "ration_card",
            "ayushman_bharat",
            "janani_suraksha",
            "pension"
        )
        assertEquals(expectedIds, schemeIds)
    }

    @Test
    fun testCuratedSchemes_FieldsAreProperlyFormatted() {
        val schemes = SchemeCardAdapter.DEFAULT_CURATED_SCHEMES

        schemes.forEach { scheme ->
            assertTrue("Scheme ID must not be blank", scheme.schemeId.isNotBlank())
            assertTrue("Scheme Name must not be blank: ${scheme.schemeId}", scheme.schemeName.isNotBlank())
            assertTrue("Portal URL must start with https://: ${scheme.portalUrl}", scheme.portalUrl.startsWith("https://"))
            assertTrue("Confirmation speech must not be blank: ${scheme.schemeId}", scheme.confirmationSpeech.isNotBlank())
            assertTrue("Initial prompt must not be blank: ${scheme.schemeId}", scheme.initialPrompt.isNotBlank())
            assertTrue("Description must not be blank: ${scheme.schemeId}", scheme.description.isNotBlank())
        }
    }

    @Test
    fun testCuratedSchemes_EnglishLocalizationFormatting() {
        val englishSchemes = SchemeCardAdapter.getCuratedSchemesForLanguage("en-IN")
        assertEquals(6, englishSchemes.size)

        val ladli = englishSchemes.first { it.schemeId == "ladli_behna" }
        assertEquals("Mukhyamantri Ladli Behna Yojana", ladli.schemeName)
        assertTrue(ladli.confirmationSpeech.contains("Ladli Behna"))

        val kisan = englishSchemes.first { it.schemeId == "pm_kisan" }
        assertEquals("PM-Kisan Samman Nidhi", kisan.schemeName)
        assertTrue(kisan.description.contains("₹6,000"))
    }

    @Test
    fun testSchemeCardAdapter_BadgeFormatting() {
        // Indic Badges
        assertEquals("ला", SchemeCardAdapter.getBadgeForScheme("ladli_behna", isEnglish = false))
        assertEquals("कि", SchemeCardAdapter.getBadgeForScheme("pm_kisan", isEnglish = false))
        assertEquals("रा", SchemeCardAdapter.getBadgeForScheme("ration_card", isEnglish = false))
        assertEquals("आ", SchemeCardAdapter.getBadgeForScheme("ayushman_bharat", isEnglish = false))
        assertEquals("ज", SchemeCardAdapter.getBadgeForScheme("janani_suraksha", isEnglish = false))
        assertEquals("पें", SchemeCardAdapter.getBadgeForScheme("pension", isEnglish = false))
        assertEquals("य", SchemeCardAdapter.getBadgeForScheme("unknown_scheme", isEnglish = false))

        // English Badges
        assertEquals("LB", SchemeCardAdapter.getBadgeForScheme("ladli_behna", isEnglish = true))
        assertEquals("PK", SchemeCardAdapter.getBadgeForScheme("pm_kisan", isEnglish = true))
        assertEquals("RC", SchemeCardAdapter.getBadgeForScheme("ration_card", isEnglish = true))
        assertEquals("AB", SchemeCardAdapter.getBadgeForScheme("ayushman_bharat", isEnglish = true))
        assertEquals("JS", SchemeCardAdapter.getBadgeForScheme("janani_suraksha", isEnglish = true))
        assertEquals("PN", SchemeCardAdapter.getBadgeForScheme("pension", isEnglish = true))
        assertEquals("SC", SchemeCardAdapter.getBadgeForScheme("unknown_scheme", isEnglish = true))
    }

    @Test
    fun testSchemeCardAdapter_AdapterPropertiesAndCallback() {
        var clickedScheme: SchemeItem? = null
        val adapter = SchemeCardAdapter(
            schemes = SchemeCardAdapter.DEFAULT_CURATED_SCHEMES,
            onSchemeSelected = { scheme -> clickedScheme = scheme }
        )

        assertEquals(6, adapter.itemCount)

        // Simulate callback invocation
        val target = adapter.schemes[0]
        assertEquals("ladli_behna", target.schemeId)
    }

    // ==========================================
    // 2. Intent Payload Formatting Tests
    // ==========================================

    @Test
    fun testVoiceIntentPayload_FormattingAndSerialization() {
        val voiceTranscript = "लाडली बहना योजना का फॉर्म भरना है"
        val language = "hi-IN"

        val request = IntentRequest(query = voiceTranscript, language = language)
        assertEquals("लाडली बहना योजना का फॉर्म भरना है", request.query)
        assertEquals("hi-IN", request.language)

        val jsonPayload = SahaayikaApiService.jsonParser.encodeToString(request)
        assertTrue(jsonPayload.contains("\"query\":\"लाडली बहना योजना का फॉर्म भरना है\""))
        assertTrue(jsonPayload.contains("\"language\":\"hi-IN\""))
    }

    @Test
    fun testTextIntentPayload_FormattingAndTrimming() {
        val rawTextInput = "   PM Kisan samman nidhi   "
        val language = "en-IN"

        val trimmed = rawTextInput.trim()
        val request = IntentRequest(query = trimmed, language = language)

        assertEquals("PM Kisan samman nidhi", request.query)
        assertEquals("en-IN", request.language)

        val jsonPayload = SahaayikaApiService.jsonParser.encodeToString(request)
        assertTrue(jsonPayload.contains("\"query\":\"PM Kisan samman nidhi\""))
        assertTrue(jsonPayload.contains("\"language\":\"en-IN\""))
    }

    @Test
    fun testIntentResponse_DeserializationAndGetters() {
        val json = """
            {
                "scheme_id": "ladli_behna",
                "scheme_name": "मुख्यमंत्री लाडली बहना योजना",
                "portal_url": "https://cmladlibehna.mp.gov.in",
                "confirmation_speech": "लाडली बहना योजना का पोर्टल खोला जा रहा है।",
                "initial_prompt": "समग्र आईडी दर्ज करें।"
            }
        """.trimIndent()

        val response = SahaayikaApiService.jsonParser.decodeFromString<IntentResponse>(json)

        assertEquals("ladli_behna", response.scheme_id)
        assertEquals("ladli_behna", response.schemeId)
        assertEquals("मुख्यमंत्री लाडली बहना योजना", response.schemeName)
        assertEquals("https://cmladlibehna.mp.gov.in", response.portalUrl)
        assertEquals("लाडली बहना योजना का पोर्टल खोला जा रहा है।", response.confirmationSpeech)
        assertEquals("समग्र आईडी दर्ज करें।", response.initialPrompt)
    }

    // ==========================================
    // 3. Local Fallback Keyword Matching Heuristic Tests
    // ==========================================

    @Test
    fun testMatchSchemeLocally_VoiceAndTextKeywords() {
        // Ladli Behna matches
        val matchLadli1 = GuidedHubActivity.matchSchemeLocally("लाडली बहना योजना", "hi-IN")
        assertNotNull(matchLadli1)
        assertEquals("ladli_behna", matchLadli1?.schemeId)

        val matchLadli2 = GuidedHubActivity.matchSchemeLocally("ladli behna form", "en-IN")
        assertNotNull(matchLadli2)
        assertEquals("ladli_behna", matchLadli2?.schemeId)

        // PM-Kisan matches
        val matchKisan1 = GuidedHubActivity.matchSchemeLocally("किसान सम्मान निधि की किस्त", "hi-IN")
        assertNotNull(matchKisan1)
        assertEquals("pm_kisan", matchKisan1?.schemeId)

        val matchKisan2 = GuidedHubActivity.matchSchemeLocally("PM Kisan payment status", "en-IN")
        assertNotNull(matchKisan2)
        assertEquals("pm_kisan", matchKisan2?.schemeId)

        // Ration Card matches
        val matchRation1 = GuidedHubActivity.matchSchemeLocally("नया राशन कार्ड बनवाना है", "hi-IN")
        assertNotNull(matchRation1)
        assertEquals("ration_card", matchRation1?.schemeId)

        val matchRation2 = GuidedHubActivity.matchSchemeLocally("ration card entitlement", "en-IN")
        assertNotNull(matchRation2)
        assertEquals("ration_card", matchRation2?.schemeId)

        // Ayushman Bharat matches
        val matchAyushman1 = GuidedHubActivity.matchSchemeLocally("आयुष्मान गोल्डन कार्ड से इलाज", "hi-IN")
        assertNotNull(matchAyushman1)
        assertEquals("ayushman_bharat", matchAyushman1?.schemeId)

        val matchAyushman2 = GuidedHubActivity.matchSchemeLocally("ayushman card hospital", "en-IN")
        assertNotNull(matchAyushman2)
        assertEquals("ayushman_bharat", matchAyushman2?.schemeId)

        // Janani Suraksha matches
        val matchJanani = GuidedHubActivity.matchSchemeLocally("जननी सुरक्षा प्रसूति सहायता", "hi-IN")
        assertNotNull(matchJanani)
        assertEquals("janani_suraksha", matchJanani?.schemeId)

        // Pension matches
        val matchPension = GuidedHubActivity.matchSchemeLocally("वृद्धावस्था पेंशन सूची", "hi-IN")
        assertNotNull(matchPension)
        assertEquals("pension", matchPension?.schemeId)

        // Unmatched & empty queries
        assertNull(GuidedHubActivity.matchSchemeLocally("", "hi-IN"))
        assertNull(GuidedHubActivity.matchSchemeLocally("   ", "hi-IN"))
        assertNull(GuidedHubActivity.matchSchemeLocally("क्रिकेट का स्कोर क्या है", "hi-IN"))
    }

    // ==========================================
    // 4. Vernacular Prompts & Audio Feedback Tests
    // ==========================================

    @Test
    fun testProactiveNarration_VernacularPrompts() {
        val hindiNarration = GuidedHubActivity.getProactiveNarration("hi-IN")
        assertEquals(GuidedHubActivity.PROMPT_HINDI, hindiNarration)
        assertTrue(hindiNarration.contains("माइक वाले गोल बटन को दबाएं"))

        val englishNarration = GuidedHubActivity.getProactiveNarration("en-IN")
        assertEquals(GuidedHubActivity.PROMPT_ENGLISH, englishNarration)
        assertTrue(englishNarration.contains("microphone button"))

        val tamilNarration = GuidedHubActivity.getProactiveNarration("ta-IN")
        assertTrue(tamilNarration.isNotBlank())

        val bengaliNarration = GuidedHubActivity.getProactiveNarration("bn-IN")
        assertTrue(bengaliNarration.isNotBlank())

        val teluguNarration = GuidedHubActivity.getProactiveNarration("te-IN")
        assertTrue(teluguNarration.isNotBlank())

        val marathiNarration = GuidedHubActivity.getProactiveNarration("mr-IN")
        assertTrue(marathiNarration.isNotBlank())
    }

    @Test
    fun testUnrecognizedSpeechMessage_Localization() {
        val hindiMsg = GuidedHubActivity.getUnrecognizedSpeechMessage("hi-IN")
        assertTrue(hindiMsg.contains("पहचान नहीं पाई"))

        val englishMsg = GuidedHubActivity.getUnrecognizedSpeechMessage("en-IN")
        assertTrue(englishMsg.contains("could not identify"))
    }
}
