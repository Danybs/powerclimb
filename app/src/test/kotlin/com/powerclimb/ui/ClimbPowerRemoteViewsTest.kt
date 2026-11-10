package com.powerclimb.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimbPowerRemoteViewsTest {

    private val density = 1.875f

    @Test
    fun `two by one uses the host size and fits the slot`() {
        val size = fitted(
            widthPx = 478,
            heightPx = 228,
            hostSize = 90f,
            digits = 3,
            verticalPaddingDp = 2f,
        )

        assertEquals(90f, size, 0.01f)
        assertFits(size, digits = 3, widthPx = 478, heightPx = 228, 2f)
    }

    @Test
    fun `one by one uses the same host size as the native field`() {
        val size = fitted(
            widthPx = 240,
            heightPx = 228,
            hostSize = 90f,
            digits = 3,
            verticalPaddingDp = 2f,
        )

        assertEquals(90f, size, 0.01f)
        assertFits(size, digits = 3, widthPx = 240, heightPx = 228, 2f)
    }

    @Test
    fun `one by one keeps the host size when it is half a page tall`() {
        val size = fitted(
            widthPx = 240,
            heightPx = 321,
            hostSize = 80f,
            digits = 3,
            verticalPaddingDp = 2f,
        )

        assertEquals(80f, size, 0.01f)
        assertFits(size, digits = 3, widthPx = 240, heightPx = 321, 2f)
    }

    @Test
    fun `one by two tall uses the same width limit and fits`() {
        val size = fitted(
            widthPx = 240,
            heightPx = 456,
            hostSize = 90f,
            digits = 3,
            verticalPaddingDp = 2f,
        )

        assertEquals(90f, size, 0.01f)
        assertFits(size, digits = 3, widthPx = 240, heightPx = 456, 2f)
    }

    @Test
    fun `four digits shrink to fit the one by one width`() {
        val size = fitted(
            widthPx = 240,
            heightPx = 228,
            hostSize = 90f,
            digits = 4,
            verticalPaddingDp = 2f,
        )

        assertTrue("four digits must be smaller than three", size < 70f)
        assertFits(size, digits = 4, widthPx = 240, heightPx = 228, 2f)
    }

    @Test
    fun `two by two can use the larger host size`() {
        val size = fitted(
            widthPx = 478,
            heightPx = 456,
            hostSize = 120f,
            digits = 3,
            verticalPaddingDp = 3f,
        )

        assertEquals(120f, size, 0.01f)
        assertFits(size, digits = 3, widthPx = 478, heightPx = 456, 3f)
    }

    @Test
    fun `picker preview keeps the host size`() {
        val size = fitted(
            widthPx = 240,
            heightPx = 128,
            hostSize = 41f,
            digits = 3,
            verticalPaddingDp = 2f,
        )

        assertEquals(41f, size, 0.01f)
        assertFits(size, digits = 3, widthPx = 240, heightPx = 128, 2f)
    }

    private fun fitted(
        widthPx: Int,
        heightPx: Int,
        hostSize: Float,
        digits: Int,
        verticalPaddingDp: Float,
    ): Float {
        return ClimbPowerRemoteViews.fittedValueSp(
            viewWidthPx = widthPx,
            viewHeightPx = heightPx,
            hostTextSize = hostSize,
            density = density,
            scaledDensity = density,
            digits = digits,
            verticalPaddingDp = verticalPaddingDp,
        )
    }

    private fun assertFits(
        sizeSp: Float,
        digits: Int,
        widthPx: Int,
        heightPx: Int,
        verticalPaddingDp: Float,
    ) {
        val widthEm = 0.5f * digits - 0.04f * (digits - 1)
        val textWidthPx = widthEm * sizeSp * density
        val inkHeightPx = 0.78f * sizeSp * density
        val availableHeight = heightPx - 2f * verticalPaddingDp * density

        assertTrue(
            "text width $textWidthPx must fit in $widthPx",
            textWidthPx <= widthPx + 0.1f,
        )
        assertTrue(
            "ink height $inkHeightPx must fit in $availableHeight",
            inkHeightPx <= availableHeight + 0.1f,
        )
    }
}
