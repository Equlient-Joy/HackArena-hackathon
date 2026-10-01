package com.sahaayika.app.ui.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.sahaayika.R
import kotlin.math.hypot

/**
 * Draggable Floating "Didi" (दीदी) Assistant Avatar.
 *
 * Provides a persistent conversational anchor for rural women during welfare portal navigation.
 *
 * Key Capabilities:
 * - Fluid drag-and-drop across the screen with auto-snapping to the nearest edge
 * - Tap to speak (Push-to-Talk) trigger callback: [onAvatarClicked]
 * - Thinking state animation with bright yellow pulsing glow ring ("दीदी सोच रही हैं...")
 * - Idle breathing ripple effect for approachable vernacular persona
 */
class FloatingDidiAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        const val AVATAR_SIZE_DP = 64f
        const val RIPPLE_SIZE_DP = 78f
        const val GOLD_COLOR = 0xFFFFD700.toInt()
        const val SAFFRON_COLOR = 0xFFE65100.toInt()
        const val DEEP_PURPLE_COLOR = 0xFF2C1E38.toInt()
    }

    private var windowManager: WindowManager? = null
    private var windowParams: WindowManager.LayoutParams? = null

    // Child UI components
    private val rippleRingView: View
    private val avatarContainer: FrameLayout
    private val tvAvatarLabel: TextView
    private val ivAvatarIcon: ImageView
    private val thinkingBadge: TextView
    private val mainLayout: LinearLayout

    // State & Animators
    private var isThinking: Boolean = false
    private var idleRippleAnimator: ValueAnimator? = null
    private var thinkingAnimator: ValueAnimator? = null

    // Touch dragging tracking
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var touchStartTime: Long = 0L
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

    /**
     * Tap listener invoked when citizen taps Didi avatar to ask a question.
     */
    var onAvatarClicked: (() -> Unit)? = null

    init {
        // Main vertical stack containing the circular avatar and optional thinking pill underneath
        mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Circular root container for avatar and glow ripple
        val circleFrame = FrameLayout(context).apply {
            val sizePx = dpToPx(RIPPLE_SIZE_DP).toInt()
            layoutParams = LayoutParams(sizePx, sizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        // 1. Outer breathing/thinking ripple ring
        rippleRingView = View(context).apply {
            val ringSizePx = dpToPx(RIPPLE_SIZE_DP).toInt()
            layoutParams = LayoutParams(ringSizePx, ringSizePx).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(3f).toInt(), SAFFRON_COLOR)
            }
            alpha = 0.4f
        }
        circleFrame.addView(rippleRingView)

        // 2. Avatar central circle (saffron gradient with gold border)
        avatarContainer = FrameLayout(context).apply {
            val coreSizePx = dpToPx(AVATAR_SIZE_DP).toInt()
            layoutParams = LayoutParams(coreSizePx, coreSizePx).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(SAFFRON_COLOR)
                setStroke(dpToPx(2.5f).toInt(), GOLD_COLOR)
            }
            elevation = dpToPx(6f)
        }

        // Didi helper icon
        ivAvatarIcon = ImageView(context).apply {
            val iconSize = dpToPx(30f).toInt()
            layoutParams = LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dpToPx(6f).toInt()
            }
            try {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_didi_assist))
            } catch (e: Exception) {
                // Defensive fallback
            }
        }
        avatarContainer.addView(ivAvatarIcon)

        // "दीदी" warm vernacular label inside circular badge
        tvAvatarLabel = TextView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(6f).toInt()
            }
            text = "दीदी"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            paint.isFakeBoldText = true
        }
        avatarContainer.addView(tvAvatarLabel)
        circleFrame.addView(avatarContainer)

        mainLayout.addView(circleFrame)

        // 3. Thinking badge ("दीदी सोच रही हैं...") displayed directly under the avatar
        thinkingBadge = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(4f).toInt()
            }
            text = "दीदी सोच रही हैं..."
            setTextColor(GOLD_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            paint.isFakeBoldText = true
            setPadding(dpToPx(8f).toInt(), dpToPx(3f).toInt(), dpToPx(8f).toInt(), dpToPx(3f).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12f)
                setColor(0xEE1E1E1E.toInt())
                setStroke(dpToPx(1.5f).toInt(), GOLD_COLOR)
            }
            visibility = View.GONE
        }
        mainLayout.addView(thinkingBadge)

        addView(mainLayout)

        startIdleBreathingAnimation()
    }

    /**
     * Attaches this floating avatar to the WindowManager with initial LayoutParams.
     */
    fun attachToWindowManager(wm: WindowManager, params: WindowManager.LayoutParams) {
        this.windowManager = wm
        this.windowParams = params
    }

    /**
     * Sets the avatar's thinking state.
     * When thinking is active, displays pulsing yellow glow ring and indicator badge.
     */
    fun setThinkingState(thinking: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post { setThinkingState(thinking) }
            return
        }
        if (this.isThinking == thinking) return
        this.isThinking = thinking

        if (thinking) {
            idleRippleAnimator?.cancel()
            thinkingBadge.visibility = View.VISIBLE
            startThinkingPulseAnimation()
        } else {
            thinkingAnimator?.cancel()
            thinkingBadge.visibility = View.GONE
            startIdleBreathingAnimation()
        }
    }

    /**
     * Returns true if Didi is currently analyzing or thinking.
     */
    fun isThinking(): Boolean = isThinking

    private fun startIdleBreathingAnimation() {
        idleRippleAnimator?.cancel()
        val rippleDrawable = rippleRingView.background as? GradientDrawable
        rippleDrawable?.setStroke(dpToPx(3f).toInt(), SAFFRON_COLOR)

        idleRippleAnimator = ValueAnimator.ofFloat(0.9f, 1.15f).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                rippleRingView.scaleX = scale
                rippleRingView.scaleY = scale
                rippleRingView.alpha = 0.5f - (scale - 0.9f) * 0.8f
            }
            start()
        }
    }

    private fun startThinkingPulseAnimation() {
        thinkingAnimator?.cancel()
        val rippleDrawable = rippleRingView.background as? GradientDrawable
        rippleDrawable?.setStroke(dpToPx(4f).toInt(), GOLD_COLOR)

        thinkingAnimator = ValueAnimator.ofFloat(0.85f, 1.3f).apply {
            duration = 600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                rippleRingView.scaleX = scale
                rippleRingView.scaleY = scale
                rippleRingView.alpha = (0.3f + (scale - 0.85f) * 1.2f).coerceIn(0.2f, 0.95f)

                avatarContainer.scaleX = 0.95f + (scale - 0.85f) * 0.15f
                avatarContainer.scaleY = 0.95f + (scale - 0.85f) * 0.15f
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wm = windowManager ?: return super.onTouchEvent(event)
        val params = windowParams ?: return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                touchStartTime = System.currentTimeMillis()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                params.x = initialX + dx
                params.y = initialY + dy
                try {
                    wm.updateViewLayout(this, params)
                } catch (e: Exception) {
                    // Ignored if detached
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val deltaX = event.rawX - initialTouchX
                val deltaY = event.rawY - initialTouchY
                val dist = hypot(deltaX.toDouble(), deltaY.toDouble()).toFloat()
                val duration = System.currentTimeMillis() - touchStartTime

                if (dist < touchSlop && duration < 500L) {
                    // Registered as an intentional click on Didi avatar
                    onAvatarClicked?.invoke()
                } else {
                    // Dragged - snap to left or right screen edge
                    snapToEdge()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Smoothly snaps the floating avatar to the nearest left or right edge of the display.
     */
    fun snapToEdge() {
        val wm = windowManager ?: return
        val params = windowParams ?: return
        val displayWidth = resources.displayMetrics.widthPixels
        val viewWidth = if (width > 0) width else dpToPx(RIPPLE_SIZE_DP).toInt()
        val margin = dpToPx(12f).toInt()

        val targetX = if (params.x + viewWidth / 2 < displayWidth / 2) {
            margin
        } else {
            displayWidth - viewWidth - margin
        }

        val startX = params.x
        ValueAnimator.ofInt(startX, targetX).apply {
            duration = 250L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                params.x = animator.animatedValue as Int
                try {
                    wm.updateViewLayout(this@FloatingDidiAvatarView, params)
                } catch (e: Exception) {
                    // Safe guard against view detach during animation
                }
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        idleRippleAnimator?.cancel()
        thinkingAnimator?.cancel()
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }
}
