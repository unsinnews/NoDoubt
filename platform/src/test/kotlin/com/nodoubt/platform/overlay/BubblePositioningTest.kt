package com.nodoubt.platform.overlay

import org.junit.Assert.*
import org.junit.Test

class BubblePositioningTest {
    private val portrait = BubbleScreenBounds(1080, 2400, insetTop = 80, insetBottom = 100)

    @Test fun firstPlacementIsRightAlignedWithItsCenterAtThreeQuartersScreenHeight() {
        val placement = BubblePositioning(portrait, requestedSize = 168, margin = 36).place(null)
        assertEquals(1080 - 36 - 168, placement.x)
        assertEquals(1800f, placement.y + placement.size / 2f, .5f)
    }

    @Test fun draggingCanBeRememberedAndRestoredByANewWindow() {
        val first = BubblePositioning(portrait, 168, 36)
        val moved = first.clamp(210, 1720)
        val stored = first.relative(moved.x, moved.y)
        val reopened = BubblePositioning(portrait, 168, 36).place(stored)
        assertEquals(moved, reopened)
        assertNotEquals(first.place(null), reopened)
    }

    @Test fun rotationKeepsRelativePositionInsideSafeBounds() {
        val first = BubblePositioning(portrait, 168, 36)
        val stored = first.relative(500, 1800)
        val landscape = BubbleScreenBounds(2400, 1080, insetLeft = 90, insetTop = 40, insetRight = 80, insetBottom = 60)
        val rotatedGeometry = BubblePositioning(landscape, 168, 36)
        val rotated = rotatedGeometry.place(stored)
        assertTrue(rotated.x >= 90 + 36)
        assertTrue(rotated.y >= 40 + 36)
        assertTrue(rotated.x + rotated.size <= 2400 - 80 - 36)
        assertTrue(rotated.y + rotated.size <= 1080 - 60 - 36)
        val restored = rotatedGeometry.relative(rotated.x, rotated.y)
        assertEquals(stored.x, restored.x, .002f)
        assertEquals(stored.y, restored.y, .002f)
    }

    @Test fun changingBubbleSizePreservesTheUsersRelativePosition() {
        val stored = BubbleRelativePosition(.23f, .82f)
        val larger = BubblePositioning(portrait, 240, 36)
        val placement = larger.place(stored)
        assertEquals(240, placement.size)
        val after = larger.relative(placement.x, placement.y)
        assertEquals(stored.x, after.x, .002f)
        assertEquals(stored.y, after.y, .002f)
        assertNotEquals(larger.place(null), placement)
    }

    @Test fun outOfBoundsDragsAndInvalidSavedValuesRemainVisible() {
        val geometry = BubblePositioning(portrait, 168, 36)
        assertEquals(BubblePlacement(36, 116, 168), geometry.clamp(-1000, -1000))
        assertEquals(BubblePlacement(876, 2096, 168), geometry.clamp(9999, 9999))
        assertEquals(geometry.place(null), geometry.place(BubbleRelativePosition(Float.NaN, .5f)))
        assertEquals(BubblePlacement(876, 116, 168), geometry.place(BubbleRelativePosition(8f, -4f)))
    }

    @Test fun verySmallAvailableDisplayCannotProduceAnOffScreenBubble() {
        val geometry = BubblePositioning(BubbleScreenBounds(20, 15), 80, 36)
        val placement = geometry.place(BubbleRelativePosition(1f, 1f))
        assertTrue(placement.size > 0)
        assertTrue(placement.x >= 0 && placement.y >= 0)
        assertTrue(placement.x + placement.size <= 20)
        assertTrue(placement.y + placement.size <= 15)
        val position = geometry.relative(placement.x, placement.y)
        assertTrue(position.x.isFinite() && position.y.isFinite())
    }
}
