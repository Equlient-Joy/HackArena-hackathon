package com.sahaayika.app.ui.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Custom full-screen Canvas View for the Sahaayika Spotlight Overlay.
 *
 * Uses PorterDuff.Mode.CLEAR with hardware/software layer compatibility to punch an illuminated
 * rectangular aperture directly over the active web form element, while rendering a 70% dark scrim
 * across the rest of the display.
 *
 * Features:
 * - Hardware layer compatible offscreen buffer via [Canvas.saveLayer]
 * - 70% semi-transparent black scrim (#B3000000)
 * - Yellow/Gold glowing border (#FFD700) with breathing/pulsing stroke animation
 * - Touch pass-through for touches located strictly inside the illuminated cutout
 */
class SpotlightCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val COLOR_SCRIM: Int = 0xB3000000.toInt() // 70% transparent black
        const val COLOR_BORDER_GOLD: Int = 0xFFFFD700.toInt() // Gold/Yellow #FFD700
        const val DEFAULT_BORDER_WIDTH_DP: Float = 5f
        const val MIN_BORDER_WIDTH_DP: Float = 4f
        const val MAX_BORDER_WIDTH_DP: Float = 7f
        const val CORNER_RADIUS_DP: Float = 12f
    }

    // 1. Scrim paint: 70% transparent black
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_SCRIM
        style = Paint.Style.FILL
    }

    // 2. Clear paint: PorterDuff.Mode.CLEAR punch-out
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    // 3. Border paint: Yellow/Gold stroke around highlighted aperture
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_BORDER_GOLD
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(DEFAULT_BORDER_WIDTH_DP)
    }

    private var targetRect: RectF? = null
    private var pulseAnimator: ValueAnimator? = null
    private val cornerRadiusPx: Float = dpToPx(CORNER_RADIUS_DP)

    /**
     * Optional callback triggered on user touches, used by the watchdog to detect activity.
     */
    var onUserInteraction: (() -> Unit)? = null

    init {
        // Ensure hardware layer compatibility for PorterDuff transfer modes
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /**
     * Sets the highlight bounds and initiates a smooth breathing pulsing border animation.
     *
     * @param rect Physical screen bounds to spotlight, or null to clear highlight.
     */
    fun setTargetRect(rect: RectF?) {
        targetRect = rect
        if (rect != null) {
            startBreathingAnimation()
        } else {
            stopBreathingAnimation()
        }
        invalidate()
    }

    /**
     * Returns the currently active spotlight bounding rectangle, or null if no target is active.
     */
    fun getTargetRect(): RectF? = targetRect

    /**
     * Clears the current spotlight target and resets canvas to a uniform scrim.
     */
    fun clearTarget() {
        targetRect = null
        stopBreathingAnimation()
        invalidate()
    }

    private fun startBreathingAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val currentWidth = dpToPx(MIN_BORDER_WIDTH_DP + fraction * (MAX_BORDER_WIDTH_DP - MIN_BORDER_WIDTH_DP))
                borderPaint.strokeWidth = currentWidth
                borderPaint.alpha = (180 + fraction * 75).toInt()
                invalidate()
            }
            start()
        }
    }

    private fun stopBreathingAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        borderPaint.strokeWidth = dpToPx(DEFAULT_BORDER_WIDTH_DP)
        borderPaint.alpha = 255
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val rect = targetRect
        if (rect == null) {
            // Draw uniform scrim across entire canvas
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
            return
        }

        // Hardware-accelerated offscreen layer for safe PorterDuff.Mode.CLEAR punching
        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        try {
            // 1. Draw 70% black scrim
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

            // 2. Clear out the highlighted aperture
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, clearPaint)

            // 3. Draw pulsing yellow/gold glowing border around the cutout
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, borderPaint)
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        onUserInteraction?.invoke()
        val rect = targetRect
        // Pass touches inside the clear cutout directly to underlying window (Chrome/browser)
        if (rect != null && rect.contains(event.x, event.y)) {
            return false
        }
        // Consume touches outside cutout to prevent accidental background taps
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopBreathingAnimation()
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }
}
