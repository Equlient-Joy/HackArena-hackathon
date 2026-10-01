package com.sahaayika.app.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Supported vernacular language in Sahaayika.
 */
@Serializable
data class Language(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val welcomePrompt: String
) {
    companion object {
        val HINDI = Language(
            code = "hi-IN",
            nativeName = "हिन्दी",
            englishName = "Hindi",
            welcomePrompt = "नमस्ते! मैं आपकी सहायिका दीदी हूँ। मैं सरकारी योजनाओं के फॉर्म भरने में आपकी मदद करूँगी। बोलकर बताइए आप क्या करना चाहती हैं।"
        )
        val MARATHI = Language(
            code = "mr-IN",
            nativeName = "मराठी",
            englishName = "Marathi",
            welcomePrompt = "नमस्कार! मी तुमची सहायिका दीदी आहे. मी तुम्हाला सरकारी योजनांचे फॉर्म भरण्यास मदत करेन."
        )
        val GUJARATI = Language(
            code = "gu-IN",
            nativeName = "ગુજરાતી",
            englishName = "Gujarati",
            welcomePrompt = "નમસ્તે! હું તમારી સહાયિકા દીદી છું. સરકારી યોજનાઓનાં ફોર્મ ભરવામાં હું તમને મદદ કરીશ."
        )
        val ENGLISH = Language(
            code = "en-IN",
            nativeName = "English",
            englishName = "English",
            welcomePrompt = "Hello! I am your Sahaayika Didi. I am here to guide you step-by-step through government scheme applications. Tell me what you need."
        )

        val SUPPORTED_LANGUAGES = listOf(HINDI, MARATHI, GUJARATI, ENGLISH)

        fun findByCode(code: String): Language {
            val prefix = code.split("-").first().lowercase()
            return SUPPORTED_LANGUAGES.firstOrNull { it.code.startsWith(prefix, ignoreCase = true) }
                ?: HINDI
        }
    }
}

/**
 * Government welfare scheme representation.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SchemeItem(
    @SerialName("id")
    @JsonNames("scheme_id", "schemeId")
    val schemeId: String = "",

    @SerialName("name")
    @JsonNames("scheme_name", "schemeName")
    val schemeName: String = "",

    @SerialName("portal_url")
    @JsonNames("portalUrl")
    val portalUrl: String = "",

    @SerialName("confirmation_speech")
    @JsonNames("confirmationSpeech")
    val confirmationSpeech: String = "",

    @SerialName("initial_prompt")
    @JsonNames("initialPrompt")
    val initialPrompt: String = "",

    val description: String = "",
    val iconRes: Int = 0
)

/**
 * Citizen request to resolve spoken or typed intent to a welfare scheme.
 */
@Serializable
data class IntentRequest(
    val query: String,
    val language: String = "hi-IN"
)

/**
 * Backend response matching user query to a government scheme.
 */
@Serializable
data class IntentResponse(
    val scheme_id: String,
    val scheme_name: String,
    val portal_url: String,
    val confirmation_speech: String,
    val initial_prompt: String
) {
    val schemeId: String get() = scheme_id
    val schemeName: String get() = scheme_name
    val portalUrl: String get() = portal_url
    val confirmationSpeech: String get() = confirmation_speech
    val initialPrompt: String get() = initial_prompt
}

/**
 * Visual grounding and multimodal guidance response from Gemini.
 */
@Serializable
data class ScreenAnalysisResponse(
    val action: String,
    val box_2d: List<Int> = emptyList(),
    val spoken_guidance: String,
    val subtitle_text: String,
    val field_type: String? = null,
    val captcha_code: String? = null,
    val status: String = "IN_PROGRESS"
) {
    val spokenGuidance: String get() = spoken_guidance
    val subtitleText: String get() = subtitle_text
    val fieldType: String? get() = field_type
    val captchaCode: String? get() = captcha_code
    val box2d: List<Int> get() = box_2d
}

/**
 * Direct conversational inquiry to Didi persona during form filling.
 */
@Serializable
data class DidiAskRequest(
    val user_query: String,
    val language: String = "hi-IN"
) {
    constructor(userQuery: String) : this(user_query = userQuery, language = "hi-IN")
}

/**
 * Empathetic Indic answer from Didi persona.
 */
@Serializable
data class DidiAskResponse(
    val spoken_answer: String,
    val subtitle_text: String
) {
    val spokenAnswer: String get() = spoken_answer
    val subtitleText: String get() = subtitle_text
}
