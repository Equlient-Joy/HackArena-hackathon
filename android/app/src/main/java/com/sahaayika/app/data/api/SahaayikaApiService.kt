package com.sahaayika.app.data.api

import com.sahaayika.app.data.model.DidiAskRequest
import com.sahaayika.app.data.model.DidiAskResponse
import com.sahaayika.app.data.model.IntentRequest
import com.sahaayika.app.data.model.IntentResponse
import com.sahaayika.app.data.model.SchemeItem
import com.sahaayika.app.data.model.ScreenAnalysisResponse
import com.sahaayika.app.privacy.FieldState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Custom Exception for Sahaayika API errors.
 */
class ApiException(val statusCode: Int, message: String) : IOException("API Error ($statusCode): $message")

/**
 * API Service for interacting with the Sahaayika FastAPI backend.
 *
 * Provides suspend functions for:
 * - Intent Recognition & Scheme matching
 * - Multimodal Gemini visual screen analysis & grounding
 * - Conversational guidance from Didi persona
 * - Scheme catalog querying
 */
class SahaayikaApiService(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = createDefaultClient()
) {

    companion object {
        const val DEFAULT_BASE_URL = "https://sahaayika-brain.onrender.com/api/v1"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val PNG_MEDIA_TYPE = "image/png".toMediaType()

        val jsonParser: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            encodeDefaults = true
        }

        fun createDefaultClient(): OkHttpClient {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build()
        }
    }

    /**
     * Resolve citizen voice/text intent into a matching welfare scheme.
     * POST /api/v1/intent
     */
    suspend fun resolveIntent(
        query: String,
        language: String = "hi-IN"
    ): Result<IntentResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBodyObj = IntentRequest(query = query, language = language)
            val jsonPayload = jsonParser.encodeToString(requestBodyObj)

            val request = Request.Builder()
                .url("$baseUrl/intent")
                .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()

            executeAndParse<IntentResponse>(request)
        }
    }

    /**
     * Send screen capture for multimodal visual grounding and vernacular guidance.
     * POST /api/v1/analyze-screen
     */
    suspend fun analyzeScreen(
        imageBytes: ByteArray,
        schemeId: String? = null,
        currentStep: String? = null,
        language: String = "hi-IN",
        fieldStates: List<FieldState> = emptyList()
    ): Result<ScreenAnalysisResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    "screen.png",
                    imageBytes.toRequestBody(PNG_MEDIA_TYPE)
                )
                .addFormDataPart("language", language)

            if (!schemeId.isNullOrBlank()) {
                multipartBuilder.addFormDataPart("scheme_id", schemeId)
            }
            if (!currentStep.isNullOrBlank()) {
                multipartBuilder.addFormDataPart("current_step", currentStep)
            }
            if (fieldStates.isNotEmpty()) {
                multipartBuilder.addFormDataPart("field_states", jsonParser.encodeToString(fieldStates))
            }

            val request = Request.Builder()
                .url("$baseUrl/analyze-screen")
                .post(multipartBuilder.build())
                .header("Accept", "application/json")
                .build()

            executeAndParse<ScreenAnalysisResponse>(request)
        }
    }

    /**
     * Ask conversational inquiry directly to Didi persona.
     * POST /api/v1/didi/ask
     */
    suspend fun askDidi(
        userQuery: String,
        language: String = "hi-IN"
    ): Result<DidiAskResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBodyObj = DidiAskRequest(user_query = userQuery, language = language)
            val jsonPayload = jsonParser.encodeToString(requestBodyObj)

            val request = Request.Builder()
                .url("$baseUrl/didi/ask")
                .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()

            executeAndParse<DidiAskResponse>(request)
        }
    }

    /**
     * Retrieve list of supported welfare schemes in the requested language.
     * GET /api/v1/schemes
     */
    suspend fun getSchemes(
        language: String = "hi-IN"
    ): Result<List<SchemeItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val urlBuilder = "$baseUrl/schemes".toHttpUrlOrNull()?.newBuilder()
                ?: throw IllegalArgumentException("Invalid URL: $baseUrl/schemes")

            urlBuilder.addQueryParameter("language", language)

            val request = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .header("Accept", "application/json")
                .build()

            executeAndParse<List<SchemeItem>>(request)
        }
    }

    private inline fun <reified T> executeAndParse(request: Request): T {
        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw ApiException(
                    statusCode = response.code,
                    message = bodyString.ifBlank { "HTTP Error ${response.code}" }
                )
            }
            return jsonParser.decodeFromString<T>(bodyString)
        }
    }
}
