package com.nodoubt.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTargetTest {
    private val rows = listOf(100, 100, 100, 100)

    @Test fun rowMovesOnlyAfterCrossingHalfOfItsNeighbour() {
        assertEquals(1, reorderTarget(rows, 1, 49f))
        assertEquals(2, reorderTarget(rows, 1, 51f))
        assertEquals(3, reorderTarget(rows, 1, 160f))
        assertEquals(0, reorderTarget(rows, 1, -60f))
    }

    @Test fun targetStaysInsideTheList() {
        assertEquals(3, reorderTarget(rows, 2, 900f))
        assertEquals(0, reorderTarget(rows, 1, -900f))
    }

    @Test fun unevenHeightsUseEachNeighboursOwnHalf() {
        assertEquals(0, reorderTarget(listOf(40, 120), 1, -21f))
        assertEquals(1, reorderTarget(listOf(120, 40, 200), 1, 99f))
        assertEquals(2, reorderTarget(listOf(120, 40, 200), 1, 101f))
    }
}
