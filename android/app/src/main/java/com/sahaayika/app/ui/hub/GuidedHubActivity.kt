package com.sahaayika.app.ui.hub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Screen 2: Guided Action & Intent Hub (दीदी गाइडेड हब).
 * Initial stub launched upon language selection.
 */
class GuidedHubActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LANGUAGE_CODE = "extra_language_code"

        fun createIntent(context: Context, languageCode: String? = null): Intent {
            return Intent(context, GuidedHubActivity::class.java).apply {
                if (languageCode != null) {
                    putExtra(EXTRA_LANGUAGE_CODE, languageCode)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
