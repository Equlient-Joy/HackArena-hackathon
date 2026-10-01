package com.sahaayika.app

import com.sahaayika.app.grounding.CoordinateTransformer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateTransformerTest {

    @Test
    fun testStandardResolution_1080x2400_withoutPadding() {
        val screenWidth = 1080
        val screenHeight = 2400

        // [ymin, xmin, ymax, xmax] -> Center box: y=250..750, x=200..800
        val box2d = listOf(250, 200, 750, 800)
        val rect = CoordinateTransformer.transform(box2d, screenWidth, screenHeight, paddingPx = 0f)

        assertNotNull(rect)
        rect!!

        // Expected: left = 0.200 * 1080 = 216f
        // Expected: top = 0.250 * 2400 = 600f
        // Expected: right = 0.800 * 1080 = 864f
        // Expected: bottom = 0.750 * 2400 = 1800f
        assertEquals(216f, rect.left, 0.01f)
        assertEquals(600f, rect.top, 0.01f)
        assertEquals(864f, rect.right, 0.01f)
        assertEquals(1800f, rect.bottom, 0.01f)

        assertEquals(648f, rect.width, 0.01f)
        assertEquals(1200f, rect.height, 0.01f)
        assertEquals(540f, rect.centerX, 0.01f)
        assertEquals(1200f, rect.centerY, 0.01f)
    }

    @Test
    fun testStandardResolution_720x1280_withDefaultPadding() {
        val screenWidth = 720
        val screenHeight = 1280

        // Box: [ymin=100, xmin=100, ymax=200, xmax=500]
        val box2d = listOf(100, 100, 200, 500)
        val padding = CoordinateTransformer.DEFAULT_PADDING_PX // 16f
        val rect = CoordinateTransformer.transform(box2d, screenWidth, screenHeight, paddingPx = padding)

        assertNotNull(rect)
        rect!!

        // Raw: left = 72f, top = 128f, right = 360f, bottom = 256f
        // With padding:
        // left = 72 - 16 = 56f
        // top = 128 - 16 = 112f
        // right = 360 + 16 = 376f
        // bottom = 256 + 16 = 272f
        assertEquals(56f, rect.left, 0.01f)
        assertEquals(112f, rect.top, 0.01f)
        assertEquals(376f, rect.right, 0.01f)
        assertEquals(272f, rect.bottom, 0.01f)
    }

    @Test
    fun testClampingAtScreenEdges_0_and_Max() {
        val screenWidth = 1080
        val screenHeight = 1920

        // Box touching top-left corner: [ymin=0, xmin=0, ymax=100, xmax=100]
        val topLeftBox = listOf(0, 0, 100, 100)
        val topLeftRect = CoordinateTransformer.transform(topLeftBox, screenWidth, screenHeight, paddingPx = 30f)

        assertNotNull(topLeftRect)
        topLeftRect!!
        // left and top should be clamped to 0f, not negative
        assertEquals(0f, topLeftRect.left, 0.001f)
        assertEquals(0f, topLeftRect.top, 0.001f)

        // Box touching bottom-right corner: [ymin=950, xmin=950, ymax=1000, xmax=1000]
        val bottomRightBox = listOf(950, 950, 1000, 1000)
        val bottomRightRect = CoordinateTransformer.transform(bottomRightBox, screenWidth, screenHeight, paddingPx = 50f)

        assertNotNull(bottomRightRect)
        bottomRightRect!!
        // right and bottom should be clamped to screenWidth and screenHeight
        assertEquals(screenWidth.toFloat(), bottomRightRect.right, 0.001f)
        assertEquals(screenHeight.toFloat(), bottomRightRect.bottom, 0.001f)
    }

    @Test
    fun testFullScreenBox() {
        val screenWidth = 1080
        val screenHeight = 2400

        val fullScreenBox = listOf(0, 0, 1000, 1000)
        val rect = CoordinateTransformer.transform(fullScreenBox, screenWidth, screenHeight, paddingPx = 16f)

        assertNotNull(rect)
        rect!!
        assertEquals(0f, rect.left, 0.001f)
        assertEquals(0f, rect.top, 0.001f)
        assertEquals(1080f, rect.right, 0.001f)
        assertEquals(2400f, rect.bottom, 0.001f)
    }

    @Test
    fun testInvertedCoordinatesHandling() {
        val screenWidth = 1000
        val screenHeight = 2000

        // Inverted: ymin > ymax, xmin > xmax -> [800, 600, 200, 100]
        val invertedBox = listOf(800, 600, 200, 100)
        val rect = CoordinateTransformer.transform(invertedBox, screenWidth, screenHeight, paddingPx = 0f)

        assertNotNull(rect)
        rect!!
        // min x is 100 (100f), max x is 600 (600f)
        // min y is 200 (400f), max y is 800 (1600f)
        assertEquals(100f, rect.left, 0.01f)
        assertEquals(400f, rect.top, 0.01f)
        assertEquals(600f, rect.right, 0.01f)
        assertEquals(1600f, rect.bottom, 0.01f)
        assertTrue(rect.left <= rect.right)
        assertTrue(rect.top <= rect.bottom)
    }

    @Test
    fun testInvalidInput_ReturnsNull() {
        val screenWidth = 1080
        val screenHeight = 1920

        assertNull(CoordinateTransformer.transform(null, screenWidth, screenHeight))
        assertNull(CoordinateTransformer.transform(listOf(10, 20, 30), screenWidth, screenHeight))
        assertNull(CoordinateTransformer.transform(emptyList(), screenWidth, screenHeight))
        assertNull(CoordinateTransformer.transform(listOf(10, 20, 30, 40), 0, screenHeight))
        assertNull(CoordinateTransformer.transform(listOf(10, 20, 30, 40), screenWidth, -1))
    }

    @Test
    fun testCenterCoordinates() {
        val screenWidth = 1000
        val screenHeight = 1000

        // Box: [100, 200, 300, 400]
        val box = listOf(100, 200, 300, 400)
        val center = CoordinateTransformer.getCenterCoordinates(box, screenWidth, screenHeight)

        assertNotNull(center)
        center!!
        // xmin=200, xmax=400 -> center x = 300
        // ymin=100, ymax=300 -> center y = 200
        assertEquals(300f, center.first, 0.01f)
        assertEquals(200f, center.second, 0.01f)
    }
}
