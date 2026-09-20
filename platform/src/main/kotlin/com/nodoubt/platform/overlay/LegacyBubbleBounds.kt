package com.nodoubt.platform.overlay

import kotlin.math.max

internal data class BubbleSystemInsets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)

/** Uses full-display geometry; a small overlay's local WindowInsets are not screen insets. */
internal fun legacyBubbleScreenBounds(
    realWidth: Int, realHeight: Int, availableWidth: Int, availableHeight: Int,
    statusBarHeight: Int, navigationOnLeft: Boolean,
    cutout: BubbleSystemInsets = BubbleSystemInsets()
): BubbleScreenBounds {
    val width = realWidth.coerceAtLeast(1)
    val height = realHeight.coerceAtLeast(1)
    val lostWidth = (width - availableWidth).coerceIn(0, width)
    val lostHeight = (height - availableHeight).coerceIn(0, height)
    val cutoutLeft = cutout.left.coerceIn(0, width)
    val cutoutRight = cutout.right.coerceIn(0, width)
    val left = if (navigationOnLeft) max(cutoutLeft, lostWidth - cutoutRight) else cutoutLeft
    val right = if (navigationOnLeft) cutoutRight else max(cutoutRight, lostWidth - cutoutLeft)
    return BubbleScreenBounds(width, height, left,
        max(statusBarHeight, cutout.top).coerceIn(0, height), right,
        max(cutout.bottom, lostHeight - cutout.top).coerceIn(0, height))
}
