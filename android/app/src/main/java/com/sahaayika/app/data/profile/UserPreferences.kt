package com.sahaayika.app.data.profile

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user preferences including selected Indic language and onboarding state.
 */
class UserPreferences(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
    )

    companion object {
        const val PREF_FILE_NAME = "sahaayika_user_preferences"
        const val KEY_SELECTED_LANGUAGE = "key_selected_language"
        const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"
        const val DEFAULT_LANGUAGE = "hi-IN"
    }

    /**
     * Saves the selected language code (e.g. "hi-IN", "ta-IN", etc.).
     */
    fun saveSelectedLanguage(languageCode: String) {
        prefs.edit()
            .putString(KEY_SELECTED_LANGUAGE, languageCode)
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .apply()
    }

    /**
     * Retrieves the selected language code, defaulting to Hindi ("hi-IN").
     */
    fun getSelectedLanguage(): String {
        return prefs.getString(KEY_SELECTED_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    /**
     * Checks if language onboarding has been completed.
     */
    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    /**
     * Resets user preferences.
     */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
