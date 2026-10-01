package com.sahaayika.app.data.profile

import android.content.Context
import android.content.SharedPreferences

/**
 * Citizen Profile representation stored in Didi Batua (दीदी बटुआ).
 */
data class CitizenProfile(
    val name: String?,
    val aadhaar: String?,
    val samagraId: String?,
    val mobile: String?
) {
    val isComplete: Boolean
        get() = !name.isNullOrBlank() && !aadhaar.isNullOrBlank() && !mobile.isNullOrBlank()
}

/**
 * Didi Batua (दीदी बटुआ) - Secure Local Citizen Wallet for rural women.
 *
 * Stores personal identifiers locally on device using SharedPreferences so rural users
 * never need to repeatedly memorize or type complex 12-digit Aadhaar, 9-digit Samagra,
 * or 10-digit mobile numbers when navigating government portals.
 */
class DidiBatuaManager(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
    )

    companion object {
        const val PREF_FILE_NAME = "sahaayika_didi_batua_prefs"

        private const val KEY_NAME = "key_citizen_name"
        private const val KEY_AADHAAR = "key_aadhaar_number"
        private const val KEY_SAMAGRA_ID = "key_samagra_id"
        private const val KEY_MOBILE = "key_mobile_number"
    }

    /**
     * Save or update citizen profile fields in the local wallet.
     */
    fun saveProfile(name: String?, aadhaar: String?, samagraId: String?, mobile: String?) {
        prefs.edit().apply {
            if (name != null) putString(KEY_NAME, name.trim()) else remove(KEY_NAME)
            if (aadhaar != null) putString(KEY_AADHAAR, aadhaar.trim().replace(" ", "")) else remove(KEY_AADHAAR)
            if (samagraId != null) putString(KEY_SAMAGRA_ID, samagraId.trim()) else remove(KEY_SAMAGRA_ID)
            if (mobile != null) putString(KEY_MOBILE, mobile.trim()) else remove(KEY_MOBILE)
            apply()
        }
    }

    /**
     * Retrieve the citizen's full name.
     */
    fun getName(): String? = prefs.getString(KEY_NAME, null)

    /**
     * Retrieve the 12-digit Aadhaar number.
     */
    fun getAadhaar(): String? = prefs.getString(KEY_AADHAAR, null)

    /**
     * Retrieve the Samagra Family / Member ID.
     */
    fun getSamagraId(): String? = prefs.getString(KEY_SAMAGRA_ID, null)

    /**
     * Retrieve the registered mobile number.
     */
    fun getMobile(): String? = prefs.getString(KEY_MOBILE, null)

    /**
     * Checks if any profile identification data is saved in Didi Batua.
     */
    fun hasProfile(): Boolean {
        return !getName().isNullOrBlank() ||
                !getAadhaar().isNullOrBlank() ||
                !getSamagraId().isNullOrBlank() ||
                !getMobile().isNullOrBlank()
    }

    /**
     * Get the aggregated CitizenProfile data class.
     */
    fun getProfile(): CitizenProfile {
        return CitizenProfile(
            name = getName(),
            aadhaar = getAadhaar(),
            samagraId = getSamagraId(),
            mobile = getMobile()
        )
    }

    /**
     * Clear all stored wallet credentials.
     */
    fun clearProfile() {
        prefs.edit().clear().apply()
    }
}
