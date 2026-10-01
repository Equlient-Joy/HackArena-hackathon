package com.sahaayika.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/**
 * Accessibility Service for Sahaayika (सहायिका).
 *
 * Silently ingests native-resolution screen captures of external welfare portals
 * for multimodal Gemini grounding without requiring MediaProjection screen-recording dialogs.
 *
 * Also monitors window state changes to detect navigation across portal pages.
 */
class SahaayikaAccessibilityService : AccessibilityService() {

    companion object {
        private var serviceInstance: WeakReference<SahaayikaAccessibilityService>? = null

        /**
         * Singleton reference to active accessibility service instance.
         */
        val instance: SahaayikaAccessibilityService?
            get() = serviceInstance?.get()

        /**
         * Returns true if accessibility permission is granted and service is active.
         */
        fun isServiceRunning(): Boolean = instance != null

        /**
         * Callback notifying overlay when browser/window transitions occur.
         */
        var onWindowStateChangedListener: (() -> Unit)? = null

        /**
         * Captures native-resolution JPEG snapshot using the active AccessibilityService.
         */
        fun captureSnapshot(onSuccess: (ByteArray) -> Unit, onError: () -> Unit) {
            val service = instance
            if (service != null) {
                service.takeScreenSnapshot(onSuccess, onError)
            } else {
                onError()
            }
        }
    }

    private val screenshotExecutor = Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInstance = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                onWindowStateChangedListener?.invoke()
            }
        }
    }

    override fun onInterrupt() {
        // Accessibility feedback interrupted
    }

    /**
     * Silently captures native-resolution display contents and compresses them into a high-quality JPEG byte array.
     */
    fun takeScreenSnapshot(
        onSuccess: (ByteArray) -> Unit,
        onError: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    screenshotExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val hwBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            val swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)

                            hardwareBuffer.close()
                            hwBitmap?.recycle()

                            if (swBitmap != null) {
                                val outputStream = ByteArrayOutputStream()
                                // Compress to native-resolution JPEG (~250-400KB)
                                swBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                                val jpegBytes = outputStream.toByteArray()
                                swBitmap.recycle()
                                onSuccess(jpegBytes)
                            } else {
                                onError()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            onError()
                        }
                    }
                )
            } catch (e: Exception) {
                onError()
            }
        } else {
            // Android versions below API 30 do not support AccessibilityService.takeScreenshot
            onError()
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        serviceInstance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceInstance = null
        screenshotExecutor.shutdown()
    }
}
