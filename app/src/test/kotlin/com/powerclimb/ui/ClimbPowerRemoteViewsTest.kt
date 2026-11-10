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
            horizontalPaddingDp = 8f,
            verticalPaddingDp = 2f,
        )

        assertEquals(90f, size, 0.01f)
        assertFits(size, digits = 3, widthPx = 478, heightPx = 228, 8f, 2f)
    }

    @Test
    fun `one by one is limited by width`() {
        val size = fitted(
            widthPx = 240,
            heightPx = 228,
            hostSize = 90f,
            digits = 3,
            horizontalPaddingDp = 8f,
            verticalPaddingDp = 2f,
        )

        assertTrue("1x1 should be smaller than the host size", size < 90f)
        assertTrue("1x1 should stay above 78 sp", size > 78f)
        assertFits(size, digits = 3, widthPx = 240, heightPx = 228, 8f, 2f)
    }

    @Test
    fun `one by two tall uses the same width limit and fits`() {
        val size = fitted(
            widthPx = 240,
            heightPx = 456,
            hostSize = 90f,
            digits = 3,
            horizontalPaddingDp = 8f,
            verticalPaddingDp = 2f,
        )

        assertTrue("tall field is still width-limited", size < 90f)
        assertFits(size, digits = 3, widthPx = 240, heightPx = 456, 8f, 2f)
    }

    @Test
    fun `four digits shrink to fit the one by one width`() {
        val size = fitted(
            widthPx = 240,
            heightPx = 228,
            hostSize = 90f,
            digits = 4,
            horizontalPaddingDp = 8f,
            verticalPaddingDp = 2f,
        )

        assertTrue("four digits must be smaller than three", size < 65f)
        assertFits(size, digits = 4, widthPx = 240, heightPx = 228, 8f, 2f)
    }

    @Test
    fun `two by two can use the larger host size`() {
        val size = fitted(
            widthPx = 478,
            heightPx = 456,
            hostSize = 120f,
            digits = 3,
            horizontalPaddingDp = 10f,
            verticalPaddingDp = 3f,
        )

        assertEquals(120f, size, 0.01f)
        assertFits(size, digits = 3, widthPx = 478, heightPx = 456, 10f, 3f)
    }

    @Test
    fun `picker preview keeps the host size`() {
        val size = fitted(
            widthPx = 240,
            heightPx = 128,
            hostSize = 41f,
            digits = 3,
            horizontalPaddingDp = 8f,
            verticalPaddingDp = 2f,
        )

        assertEquals(41f, size, 0.01f)
        assertFits(size, digits = 3, widthPx = 240, heightPx = 128, 8f, 2f)
    }

    private fun fitted(
        widthPx: Int,
        heightPx: Int,
        hostSize: Float,
        digits: Int,
        horizontalPaddingDp: Float,
        verticalPaddingDp: Float,
    ): Float {
        return ClimbPowerRemoteViews.fittedValueSp(
            viewWidthPx = widthPx,
            viewHeightPx = heightPx,
            hostTextSize = hostSize,
            density = density,
            scaledDensity = density,
            digits = digits,
            horizontalPaddingDp = horizontalPaddingDp,
            verticalPaddingDp = verticalPaddingDp,
        )
    }

    private fun assertFits(
        sizeSp: Float,
        digits: Int,
        widthPx: Int,
        heightPx: Int,
        horizontalPaddingDp: Float,
        verticalPaddingDp: Float,
    ) {
        val widthEm = 0.5f * digits - 0.04f * (digits - 1)
        val textWidthPx = widthEm * sizeSp * density
        val inkHeightPx = 0.78f * sizeSp * density
        val availableWidth = widthPx - 2f * horizontalPaddingDp * density
        val availableHeight = heightPx - 2f * verticalPaddingDp * density

        assertTrue(
            "text width $textWidthPx must fit in $availableWidth",
            textWidthPx <= availableWidth + 0.1f,
        )
        assertTrue(
            "ink height $inkHeightPx must fit in $availableHeight",
            inkHeightPx <= availableHeight + 0.1f,
        )
    }
}
