package com.sahaayika.app.grounding

import android.graphics.RectF

/**
 * Pixel coordinates of a visual bounding box on screen.
 * Pure Kotlin data class allowing execution in JVM unit tests without Android mock overhead.
 */
data class PixelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = maxOf(0f, right - left)
    val height: Float get() = maxOf(0f, bottom - top)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /**
     * Convert to Android RectF for Canvas and View drawing.
     */
    fun toRectF(): RectF = RectF(left, top, right, bottom)
}

/**
 * Coordinate Transformer for multimodal visual grounding.
 *
 * Maps normalized [ymin, xmin, ymax, xmax] coordinates (0 to 1000) returned by the Gemini backend
 * to exact physical screen pixels on the citizen's mobile device, applying safety padding and boundary clamping.
 */
object CoordinateTransformer {

    const val DEFAULT_PADDING_PX: Float = 16f
    private const val NORM_SCALE: Float = 1000f

    /**
     * Maps normalized Gemini coordinates [ymin, xmin, ymax, xmax] (0..1000)
     * to physical pixel bounds [left, top, right, bottom], applying padding and clamping.
     *
     * @param box2d List of 4 integers: [ymin, xmin, ymax, xmax]
     * @param screenWidth Screen width in physical pixels
     * @param screenHeight Screen height in physical pixels
     * @param paddingPx Extra touch-target/highlight padding in pixels (default 16px)
     * @return Clamped [PixelRect] or null if input coordinates are invalid
     */
    fun transform(
        box2d: List<Int>?,
        screenWidth: Int,
        screenHeight: Int,
        paddingPx: Float = DEFAULT_PADDING_PX
    ): PixelRect? {
        if (box2d == null || box2d.size < 4 || screenWidth <= 0 || screenHeight <= 0) {
            return null
        }

        val rawYmin = box2d[0]
        val rawXmin = box2d[1]
        val rawYmax = box2d[2]
        val rawXmax = box2d[3]

        // Ensure correct coordinate ordering
        val ymin = minOf(rawYmin, rawYmax).coerceIn(0, 1000)
        val xmin = minOf(rawXmin, rawXmax).coerceIn(0, 1000)
        val ymax = maxOf(rawYmin, rawYmax).coerceIn(0, 1000)
        val xmax = maxOf(rawXmin, rawXmax).coerceIn(0, 1000)

        // Map normalized 0-1000 to physical pixels
        val rawLeft = (xmin / NORM_SCALE) * screenWidth
        val rawTop = (ymin / NORM_SCALE) * screenHeight
        val rawRight = (xmax / NORM_SCALE) * screenWidth
        val rawBottom = (ymax / NORM_SCALE) * screenHeight

        // Apply padding expansion and clamp to screen boundary [0, 0, screenWidth, screenHeight]
        val left = (rawLeft - paddingPx).coerceIn(0f, screenWidth.toFloat())
        val top = (rawTop - paddingPx).coerceIn(0f, screenHeight.toFloat())
        val right = (rawRight + paddingPx).coerceIn(0f, screenWidth.toFloat())
        val bottom = (rawBottom + paddingPx).coerceIn(0f, screenHeight.toFloat())

        return PixelRect(
            left = minOf(left, right),
            top = minOf(top, bottom),
            right = maxOf(left, right),
            bottom = maxOf(top, bottom)
        )
    }

    /**
     * Compute the center (x, y) coordinates of the bounding box without padding,
     * useful for simulating touch/clicks or pointing arrows.
     */
    fun getCenterCoordinates(
        box2d: List<Int>?,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<Float, Float>? {
        val rect = transform(box2d, screenWidth, screenHeight, paddingPx = 0f) ?: return null
        return Pair(rect.centerX, rect.centerY)
    }
}
