package com.sahaayika.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
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

        /**
         * Captures raw Bitmap snapshot using the active AccessibilityService for on-device redaction.
         */
        fun captureBitmap(onSuccess: (Bitmap) -> Unit, onError: () -> Unit) {
            val service = instance
            if (service != null) {
                service.takeScreenBitmap(onSuccess, onError)
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
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            onWindowStateChangedListener?.invoke()
        }
    }

    override fun onInterrupt() {
        // Accessibility feedback interrupted
    }

    /**
     * Captures display contents as a Bitmap for on-device processing.
     */
    fun takeScreenBitmap(
        onSuccess: (Bitmap) -> Unit,
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
                            val swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, true)

                            hardwareBuffer.close()
                            hwBitmap?.recycle()

                            if (swBitmap != null) {
                                onSuccess(swBitmap)
                            } else {
                                onError()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w("SahaayikaA11y", "takeScreenshot onFailure code: $errorCode")
                            onError()
                        }
                    }
                )
            } catch (e: Exception) {
                Log.w("SahaayikaA11y", "takeScreenshot exception: $e")
                onError()
            }
        } else {
            onError()
        }
    }

    /**
     * Silently captures native-resolution display contents and compresses them into a high-quality JPEG byte array.
     */
    fun takeScreenSnapshot(
        onSuccess: (ByteArray) -> Unit,
        onError: () -> Unit
    ) {
        takeScreenBitmap(
            onSuccess = { swBitmap ->
                val outputStream = ByteArrayOutputStream()
                swBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                val jpegBytes = outputStream.toByteArray()
                swBitmap.recycle()
                onSuccess(jpegBytes)
            },
            onError = onError
        )
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
