package com.sahaayika.app.ui.overlay

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/**
 * High-Contrast Floating Subtitle Banner for Sahaayika (सहायिका).
 *
 * Displays synchronized spoken guidance and vernacular transcripts in high-contrast yellow (#FFEB3B)
 * on a dark slate pill background (#1E1E1E, alpha 0.95), ensuring maximum legibility for low-literacy
 * rural citizens even in bright outdoor sunlight.
 */
class SubtitleBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        const val COLOR_BACKGROUND_SLATE: Int = 0xF21E1E1E.toInt() // #1E1E1E with 0.95 alpha
        const val COLOR_TEXT_YELLOW: Int = 0xFFFFEB3B.toInt() // High-contrast Yellow #FFEB3B
        const val CORNER_RADIUS_DP: Float = 24f
    }

    private val tvSubtitle: TextView
    private val cardContainer: FrameLayout

    init {
        // Container for rounded pill shape
        cardContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                val marginHorizontal = dpToPx(16f).toInt()
                setMargins(marginHorizontal, 0, marginHorizontal, 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(CORNER_RADIUS_DP)
                setColor(COLOR_BACKGROUND_SLATE)
                setStroke(dpToPx(1.5f).toInt(), 0x66FFEB3B)
            }
            elevation = dpToPx(8f)
            val padHorizontal = dpToPx(20f).toInt()
            val padVertical = dpToPx(12f).toInt()
            setPadding(padHorizontal, padVertical, padHorizontal, padVertical)
        }

        // Subtitle text: High-contrast yellow, 18sp bold
        tvSubtitle = TextView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            setTextColor(COLOR_TEXT_YELLOW)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            maxLines = 4
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setLineSpacing(dpToPx(3f), 1.15f)
        }

        cardContainer.addView(tvSubtitle)
        addView(cardContainer)

        // Initially hidden until subtitle text is supplied
        visibility = View.GONE
    }

    /**
     * Sets or updates the active subtitle text.
     * When text is null or blank, the banner is hidden.
     *
     * @param text Vernacular guidance or spoken subtitle text to display.
     */
    fun setSubtitle(text: String?) {
        val cleanText = text?.trim()
        if (cleanText.isNullOrBlank()) {
            visibility = View.GONE
            tvSubtitle.text = ""
        } else {
            tvSubtitle.text = cleanText
            if (visibility != View.VISIBLE) {
                alpha = 0f
                visibility = View.VISIBLE
                ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
                    duration = 200L
                    start()
                }
            }
        }
    }

    /**
     * Returns the currently displayed subtitle text.
     */
    fun getSubtitle(): String = tvSubtitle.text.toString()

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }
}
