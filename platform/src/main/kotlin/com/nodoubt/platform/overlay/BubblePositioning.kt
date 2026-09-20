package com.nodoubt.platform.overlay

import kotlin.math.roundToInt

internal data class BubbleScreenBounds(
    val width: Int, val height: Int,
    val insetLeft: Int = 0, val insetTop: Int = 0, val insetRight: Int = 0, val insetBottom: Int = 0
)

internal data class BubbleRelativePosition(val x: Float, val y: Float)
internal data class BubblePlacement(val x: Int, val y: Int, val size: Int)

/** Pixel coordinates use the physical display origin; saved coordinates use available travel. */
internal class BubblePositioning(private val screen: BubbleScreenBounds, requestedSize: Int, margin: Int) {
    private val width = screen.width.coerceAtLeast(1)
    private val height = screen.height.coerceAtLeast(1)
    private val left = screen.insetLeft.coerceIn(0, width - 1)
    private val top = screen.insetTop.coerceIn(0, height - 1)
    private val right = (width - screen.insetRight).coerceIn(left + 1, width)
    private val bottom = (height - screen.insetBottom).coerceIn(top + 1, height)
    private val gap = margin.coerceAtLeast(0).coerceAtMost(((minOf(right - left, bottom - top) - 1) / 2))
    val size = requestedSize.coerceAtLeast(1).coerceAtMost(minOf(right - left, bottom - top) - gap * 2)
    private val minX = left + gap
    private val minY = top + gap
    private val maxX = (right - gap - size).coerceAtLeast(minX)
    private val maxY = (bottom - gap - size).coerceAtLeast(minY)

    fun place(position: BubbleRelativePosition?): BubblePlacement {
        if (position == null || !position.x.isFinite() || !position.y.isFinite()) {
            return clamp(maxX, (height * .75f - size / 2f).roundToInt())
        }
        return clamp((minX + (maxX - minX) * position.x.coerceIn(0f, 1f)).roundToInt(),
            (minY + (maxY - minY) * position.y.coerceIn(0f, 1f)).roundToInt())
    }

    fun clamp(x: Int, y: Int) = BubblePlacement(x.coerceIn(minX, maxX), y.coerceIn(minY, maxY), size)

    fun relative(x: Int, y: Int): BubbleRelativePosition {
        val placement = clamp(x, y)
        fun fraction(value: Int, low: Int, high: Int) = if (high == low) 0f else (value - low).toFloat() / (high - low)
        return BubbleRelativePosition(fraction(placement.x, minX, maxX), fraction(placement.y, minY, maxY))
    }
}
