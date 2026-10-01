package com.sahaayika.app.ui.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Service orchestrating the visual spotlight and voice guidance overlay
 * on top of external government scheme web portals.
 *
 * Implements stub service lifecycle for Guided Action & Intent Hub (Task 9),
 * ready for full overlay window management in Task 10.
 */
class SpotlightOverlayService : Service() {

    companion object {
        const val EXTRA_SCHEME_ID = "extra_scheme_id"
        const val EXTRA_PORTAL_URL = "extra_portal_url"
        const val EXTRA_INITIAL_PROMPT = "extra_initial_prompt"
        const val EXTRA_LANGUAGE = "extra_language"

        const val ACTION_START_SPOTLIGHT = "com.sahaayika.app.ACTION_START_SPOTLIGHT"
        const val ACTION_STOP_SPOTLIGHT = "com.sahaayika.app.ACTION_STOP_SPOTLIGHT"

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
                context.startService(intent)
            } catch (e: Exception) {
                // Defensive handling if service start fails in background
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SpotlightOverlayService::class.java).apply {
                action = ACTION_STOP_SPOTLIGHT
            }
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                // Ignored
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SPOTLIGHT) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }
}
