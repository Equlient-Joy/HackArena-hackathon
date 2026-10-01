package com.sahaayika.app

import com.sahaayika.app.data.api.SahaayikaApiService
import com.sahaayika.app.data.model.DidiAskRequest
import com.sahaayika.app.data.model.DidiAskResponse
import com.sahaayika.app.data.model.IntentRequest
import com.sahaayika.app.data.model.IntentResponse
import com.sahaayika.app.data.model.Language
import com.sahaayika.app.data.model.SchemeItem
import com.sahaayika.app.data.model.ScreenAnalysisResponse
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsSerializationTest {

    private val json = SahaayikaApiService.jsonParser

    @Test
    fun testLanguageModel() {
        val hindi = Language.HINDI
        assertEquals("hi-IN", hindi.code)
        assertEquals("हिन्दी", hindi.nativeName)
        assertEquals("Hindi", hindi.englishName)

        val found = Language.findByCode("hi-IN")
        assertEquals("hi-IN", found.code)

        val fallback = Language.findByCode("unknown")
        assertEquals(Language.HINDI, fallback)
    }

    @Test
    fun testIntentRequestSerialization() {
        val request = IntentRequest(query = "लाडली बहना योजना", language = "hi-IN")
        val jsonStr = json.encodeToString(request)
        assertTrue(jsonStr.contains("लाडली बहना योजना"))
        assertTrue(jsonStr.contains("hi-IN"))

        val decoded = json.decodeFromString<IntentRequest>(jsonStr)
        assertEquals(request.query, decoded.query)
        assertEquals(request.language, decoded.language)
    }

    @Test
    fun testIntentResponseDeserialization() {
        val backendJson = """
            {
                "scheme_id": "ladli_behna",
                "scheme_name": "मुख्यमंत्री लाड़ली बहना योजना",
                "portal_url": "https://cmladlibahna.mp.gov.in",
                "confirmation_speech": "मैं आपको लाडली बहना पोर्टल पर ले जा रही हूँ।",
                "initial_prompt": "यहाँ अपना समग्र सदस्य आईडी दर्ज करें।"
            }
        """.trimIndent()

        val response = json.decodeFromString<IntentResponse>(backendJson)
        assertEquals("ladli_behna", response.scheme_id)
        assertEquals("ladli_behna", response.schemeId)
        assertEquals("मुख्यमंत्री लाड़ली बहना योजना", response.scheme_name)
        assertEquals("मुख्यमंत्री लाड़ली बहना योजना", response.schemeName)
        assertEquals("https://cmladlibahna.mp.gov.in", response.portal_url)
        assertEquals("https://cmladlibahna.mp.gov.in", response.portalUrl)
        assertEquals("मैं आपको लाडली बहना पोर्टल पर ले जा रही हूँ।", response.confirmation_speech)
        assertEquals("यहाँ अपना समग्र सदस्य आईडी दर्ज करें।", response.initial_prompt)
    }

    @Test
    fun testScreenAnalysisResponseDeserialization() {
        val backendJson = """
            {
                "action": "HIGHLIGHT",
                "box_2d": [320, 150, 410, 850],
                "spoken_guidance": "दीदी, यहाँ अपना १२ अंकों का आधार नंबर दर्ज करें।",
                "subtitle_text": "आधार नंबर दर्ज करें",
                "field_type": "INPUT_TEXT",
                "captcha_code": null,
                "status": "IN_PROGRESS"
            }
        """.trimIndent()

        val response = json.decodeFromString<ScreenAnalysisResponse>(backendJson)
        assertEquals("HIGHLIGHT", response.action)
        assertEquals(listOf(320, 150, 410, 850), response.box_2d)
        assertEquals(listOf(320, 150, 410, 850), response.box2d)
        assertEquals("दीदी, यहाँ अपना १२ अंकों का आधार नंबर दर्ज करें।", response.spoken_guidance)
        assertEquals("दीदी, यहाँ अपना १२ अंकों का आधार नंबर दर्ज करें।", response.spokenGuidance)
        assertEquals("आधार नंबर दर्ज करें", response.subtitle_text)
        assertEquals("INPUT_TEXT", response.field_type)
        assertEquals(null, response.captcha_code)
        assertEquals("IN_PROGRESS", response.status)
    }

    @Test
    fun testDidiAskRequestAndResponse() {
        val askReq = DidiAskRequest(user_query = "मुझे कौन से दस्तावेज़ चाहिए?", language = "hi-IN")
        val reqJson = json.encodeToString(askReq)
        val decodedReq = json.decodeFromString<DidiAskRequest>(reqJson)
        assertEquals("मुझे कौन से दस्तावेज़ चाहिए?", decodedReq.user_query)

        val respJson = """
            {
                "spoken_answer": "दीदी, इसके लिए आपको समग्र आईडी, आधार कार्ड और बैंक पासबुक की आवश्यकता होगी।",
                "subtitle_text": "समग्र आईडी, आधार और बैंक पासबुक जरूरी हैं।"
            }
        """.trimIndent()

        val resp = json.decodeFromString<DidiAskResponse>(respJson)
        assertEquals("दीदी, इसके लिए आपको समग्र आईडी, आधार कार्ड और बैंक पासबुक की आवश्यकता होगी।", resp.spoken_answer)
        assertEquals("दीदी, इसके लिए आपको समग्र आईडी, आधार कार्ड और बैंक पासबुक की आवश्यकता होगी।", resp.spokenAnswer)
        assertEquals("समग्र आईडी, आधार और बैंक पासबुक जरूरी हैं।", resp.subtitleText)
    }

    @Test
    fun testSchemeItemDeserializationFromCatalog() {
        val catalogJson = """
            [
                {
                    "id": "ladli_behna",
                    "name": "मुख्यमंत्री लाड़ली बहना योजना",
                    "portal_url": "https://cmladlibahna.mp.gov.in",
                    "confirmation_speech": "लाडली बहना योजना पोर्टल खुल रहा है।",
                    "initial_prompt": "यहाँ अपना आवेदन शुरू करें।",
                    "description": "मध्य प्रदेश शासन द्वारा महिलाओं के आर्थिक स्वावलंबन हेतु।"
                }
            ]
        """.trimIndent()

        val list = json.decodeFromString<List<SchemeItem>>(catalogJson)
        assertEquals(1, list.size)
        val item = list[0]
        assertEquals("ladli_behna", item.schemeId)
        assertEquals("मुख्यमंत्री लाड़ली बहना योजना", item.schemeName)
        assertEquals("https://cmladlibahna.mp.gov.in", item.portalUrl)
        assertEquals("लाडली बहना योजना पोर्टल खुल रहा है।", item.confirmationSpeech)
        assertEquals("यहाँ अपना आवेदन शुरू करें।", item.initialPrompt)
        assertEquals("मध्य प्रदेश शासन द्वारा महिलाओं के आर्थिक स्वावलंबन हेतु।", item.description)
    }
}
