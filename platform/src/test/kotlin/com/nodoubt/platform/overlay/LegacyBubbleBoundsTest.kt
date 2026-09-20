package com.nodoubt.platform.overlay

import org.junit.Assert.*
import org.junit.Test

class LegacyBubbleBoundsTest {
    @Test fun portraitUsesGlobalStatusAndBottomNavigationEvenWhenBubbleIsAwayFromBothBars() {
        val bounds = legacyBubbleScreenBounds(1080, 1920, 1080, 1800, 72, navigationOnLeft = false)
        assertEquals(BubbleScreenBounds(1080, 1920, insetTop = 72, insetBottom = 120), bounds)
        val placement = BubblePositioning(bounds, 150, 24).clamp(-9999, 9999)
        assertEquals(24, placement.x)
        assertEquals(1920 - 120 - 24 - 150, placement.y)
    }

    @Test fun oppositeLandscapeRotationsReserveTheNavigationBarOnTheCorrectSide() {
        val right = legacyBubbleScreenBounds(1920, 1080, 1800, 1080, 60, navigationOnLeft = false)
        val left = legacyBubbleScreenBounds(1920, 1080, 1800, 1080, 60, navigationOnLeft = true)
        assertEquals(BubbleScreenBounds(1920, 1080, insetTop = 60, insetRight = 120), right)
        assertEquals(BubbleScreenBounds(1920, 1080, insetLeft = 120, insetTop = 60), left)
        assertEquals(144, BubblePositioning(left, 150, 24).clamp(-9999, 200).x)
        assertEquals(1626, BubblePositioning(right, 150, 24).clamp(9999, 200).x)
    }

    @Test fun landscapeTabletsKeepBottomNavigationWithoutInventingASideInset() {
        val bounds = legacyBubbleScreenBounds(2560, 1600, 2560, 1504, 48, navigationOnLeft = true)
        assertEquals(BubbleScreenBounds(2560, 1600, insetTop = 48, insetBottom = 96), bounds)
    }

    @Test fun globalCutoutAndNavigationLossesAreNotDoubleCounted() {
        val bounds = legacyBubbleScreenBounds(2400, 1080, 2200, 1080, 48, navigationOnLeft = false,
            cutout = BubbleSystemInsets(left = 80))
        assertEquals(BubbleScreenBounds(2400, 1080, insetLeft = 80, insetTop = 48, insetRight = 120), bounds)
        val overlapping = legacyBubbleScreenBounds(2400, 1080, 2280, 1080, 48, navigationOnLeft = false,
            cutout = BubbleSystemInsets(right = 80))
        assertEquals(120, overlapping.insetRight)
        val portrait = legacyBubbleScreenBounds(1080, 2400, 1080, 2180, 60, navigationOnLeft = false,
            cutout = BubbleSystemInsets(top = 100))
        assertEquals(100, portrait.insetTop)
        assertEquals(120, portrait.insetBottom)
    }
}
