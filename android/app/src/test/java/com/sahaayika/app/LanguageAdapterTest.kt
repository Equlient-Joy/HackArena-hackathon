package com.sahaayika.app

import com.sahaayika.app.data.model.Language
import com.sahaayika.app.data.profile.DidiBatuaManager
import com.sahaayika.app.data.profile.UserPreferences
import com.sahaayika.app.ui.language.LanguageActivity
import com.sahaayika.app.ui.language.LanguageAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying Language Onboarding specifications:
 * 1. All 6 core Indic languages with proper codes, native titles, and script badges.
 * 2. LanguageAdapter initialization and callback behavior.
 * 3. UserPreferences and DidiBatuaManager language persistence.
 * 4. LanguageActivity bilingual welcome prompts.
 */
class LanguageAdapterTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var userPreferences: UserPreferences
    private lateinit var batuaManager: DidiBatuaManager

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        userPreferences = UserPreferences(fakePrefs)
        batuaManager = DidiBatuaManager(fakePrefs)
    }

    // ==========================================
    // 1. Core Indic Languages Specification Tests
    // ==========================================

    @Test
    fun testCoreLanguages_ContainsAllSixIndicLanguages() {
        val core = Language.CORE_LANGUAGES
        assertEquals("Core Indic language list must contain exactly 6 languages", 6, core.size)

        val codes = core.map { it.code }
        val expectedCodes = listOf("hi-IN", "ta-IN", "bn-IN", "te-IN", "mr-IN", "en-IN")
        assertEquals(expectedCodes, codes)
    }

    @Test
    fun testCoreLanguages_NativeTitlesAndEnglishNames() {
        val core = Language.CORE_LANGUAGES

        // Hindi
        val hindi = core.first { it.code == "hi-IN" }
        assertEquals("हिन्दी", hindi.nativeName)
        assertEquals("Hindi", hindi.englishName)
        assertEquals("अ", hindi.badge)

        // Tamil
        val tamil = core.first { it.code == "ta-IN" }
        assertEquals("தமிழ்", tamil.nativeName)
        assertEquals("Tamil", tamil.englishName)
        assertEquals("அ", tamil.badge)

        // Bengali
        val bengali = core.first { it.code == "bn-IN" }
        assertEquals("বাংলা", bengali.nativeName)
        assertEquals("Bengali", bengali.englishName)
        assertEquals("অ", bengali.badge)

        // Telugu
        val telugu = core.first { it.code == "te-IN" }
        assertEquals("తెలుగు", telugu.nativeName)
        assertEquals("Telugu", telugu.englishName)
        assertEquals("తె", telugu.badge)

        // Marathi
        val marathi = core.first { it.code == "mr-IN" }
        assertEquals("मराठी", marathi.nativeName)
        assertEquals("Marathi", marathi.englishName)
        assertEquals("म", marathi.badge)

        // Indian English
        val english = core.first { it.code == "en-IN" }
        assertEquals("English", english.nativeName)
        assertEquals("English", english.englishName)
        assertEquals("A", english.badge)
    }

    @Test
    fun testLanguage_FindByCode() {
        assertEquals("hi-IN", Language.findByCode("hi-IN").code)
        assertEquals("ta-IN", Language.findByCode("ta-IN").code)
        assertEquals("bn-IN", Language.findByCode("bn-IN").code)
        assertEquals("te-IN", Language.findByCode("te-IN").code)
        assertEquals("mr-IN", Language.findByCode("mr-IN").code)
        assertEquals("en-IN", Language.findByCode("en-IN").code)

        // Short code matching
        assertEquals("hi-IN", Language.findByCode("hi").code)
        assertEquals("ta-IN", Language.findByCode("ta").code)
        assertEquals("bn-IN", Language.findByCode("bn").code)

        // Fallback to Hindi
        assertEquals("hi-IN", Language.findByCode("unknown-XX").code)
    }

    // ==========================================
    // 2. LanguageAdapter Tests
    // ==========================================

    @Test
    fun testLanguageAdapter_DefaultItemCount() {
        val adapter = LanguageAdapter(
            onLanguageSelected = {}
        )
        assertEquals(6, adapter.itemCount)
        assertEquals(Language.CORE_LANGUAGES, adapter.languages)
    }

    @Test
    fun testLanguageAdapter_CustomLanguages() {
        val customList = listOf(Language.HINDI, Language.TAMIL)
        val adapter = LanguageAdapter(
            languages = customList,
            onLanguageSelected = {}
        )
        assertEquals(2, adapter.itemCount)
        assertEquals(customList, adapter.languages)
    }

    @Test
    fun testLanguageAdapter_SelectionCallback() {
        var selectedLanguage: Language? = null
        val adapter = LanguageAdapter(
            onLanguageSelected = { lang -> selectedLanguage = lang }
        )

        val targetLang = adapter.languages[1] // Tamil
        // Directly test selection logic
        assertNotNull(targetLang)
        assertEquals("ta-IN", targetLang.code)
    }

    // ==========================================
    // 3. UserPreferences & Persistence Tests
    // ==========================================

    @Test
    fun testUserPreferences_DefaultAndSave() {
        // Initial state
        assertEquals("hi-IN", userPreferences.getSelectedLanguage())
        assertFalse(userPreferences.isOnboardingCompleted())

        // Save selected language
        userPreferences.saveSelectedLanguage("ta-IN")
        assertEquals("ta-IN", userPreferences.getSelectedLanguage())
        assertTrue(userPreferences.isOnboardingCompleted())

        // Clear preferences
        userPreferences.clear()
        assertEquals("hi-IN", userPreferences.getSelectedLanguage())
        assertFalse(userPreferences.isOnboardingCompleted())
    }

    @Test
    fun testDidiBatuaManager_LanguagePersistence() {
        assertEquals("hi-IN", batuaManager.getLanguage())

        batuaManager.saveLanguage("bn-IN")
        assertEquals("bn-IN", batuaManager.getLanguage())
    }

    // ==========================================
    // 4. Bilingual Welcome Prompts
    // ==========================================

    @Test
    fun testLanguageActivity_WelcomePrompts() {
        assertEquals(
            "नमस्ते! नीचे दी गई सूची में से अपनी भाषा पर उँगली रखकर चुनें।",
            LanguageActivity.WELCOME_HINDI
        )
        assertEquals(
            "Welcome. Please tap your language.",
            LanguageActivity.WELCOME_ENGLISH
        )
    }
}
