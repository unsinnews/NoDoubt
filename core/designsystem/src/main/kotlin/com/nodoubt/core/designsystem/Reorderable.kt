package com.nodoubt.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Rows that reorder by long-press and drag. Taps still reach each row; a drag never triggers its click.
 * Accessibility services get 上移/下移 actions instead of the gesture.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>, keyOf: (T) -> Any, onMove: (from: Int, to: Int) -> Unit,
    row: @Composable (item: T, index: Int) -> Unit
) {
    var order by remember(items) { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    val haptic = LocalHapticFeedback.current
    val latestOrder by rememberUpdatedState(order)
    val latestMove by rememberUpdatedState(onMove)
    val raised = LocalNoDoubtTokens.current.raised
    fun sizes(list: List<T>) = list.map { heights[keyOf(it)] ?: 0 }
    fun commit(from: Int, to: Int) {
        if (from == to || from !in order.indices || to !in order.indices) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        latestMove(from, to)
    }
    val from = order.indexOfFirst { keyOf(it) == draggingKey }
    val target = if (from < 0) -1 else reorderTarget(sizes(order), from, offset)
    val draggedHeight = (draggingKey?.let { heights[it] } ?: 0).toFloat()
    order.forEachIndexed { index, item ->
        val itemKey = keyOf(item)
        val dragging = itemKey == draggingKey
        val shift = when {
            from < 0 || dragging -> 0f
            index in (from + 1)..target -> -draggedHeight
            index in target until from -> draggedHeight
            else -> 0f
        }
        key(itemKey) {
            val animated by animateFloatAsState(shift, if (draggingKey != null) spring(stiffness = Spring.StiffnessMediumLow) else snap(),
                label = "reorder")
            Box(Modifier.onSizeChanged { heights[itemKey] = it.height }
                .semantics {
                    customActions = listOfNotNull(
                        CustomAccessibilityAction("上移") { commit(index, index - 1); true }.takeIf { index > 0 },
                        CustomAccessibilityAction("下移") { commit(index, index + 1); true }.takeIf { index < order.lastIndex }
                    )
                }
                .pointerInput(itemKey) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val timeout = viewConfiguration.longPressTimeoutMillis
                        val interrupted = withTimeoutOrNull(timeout) {
                            awaitReleaseOrSlop(down, viewConfiguration.touchSlop, timeout)
                        }
                        if (interrupted == true) return@awaitEachGesture
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        draggingKey = itemKey
                        offset = 0f
                        try {
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                    .firstOrNull { it.id == down.id } ?: break
                                val delta = change.position.y - change.previousPosition.y
                                change.consume()
                                if (!change.pressed) break
                                val list = latestOrder
                                val start = list.indexOfFirst { keyOf(it) == itemKey }
                                val all = sizes(list)
                                val above = all.take(start).sum().toFloat()
                                val below = all.drop(start + 1).sum().toFloat()
                                offset = (offset + delta).coerceIn(-above, below)
                            }
                            val list = latestOrder
                            val start = list.indexOfFirst { keyOf(it) == itemKey }
                            if (start >= 0) commit(start, reorderTarget(sizes(list), start, offset))
                        } finally {
                            draggingKey = null
                            offset = 0f
                        }
                    }
                }
                .zIndex(if (dragging) 1f else 0f)
                .graphicsLayer {
                    translationY = if (dragging) offset else animated
                    if (dragging) {
                        shadowElevation = 10.dp.toPx()
                        shape = RoundedCornerShape(10.dp)
                    }
                }
                .then(if (dragging) Modifier.background(raised, RoundedCornerShape(10.dp)) else Modifier)
            ) { row(item, index) }
        }
    }
}

/**
 * True when the pointer lifts or moves past [slop] before the long-press timeout; false once a held
 * pointer's own event time passes the timeout (the coroutine timeout covers a perfectly still finger).
 */
private suspend fun AwaitPointerEventScope.awaitReleaseOrSlop(down: PointerInputChange, slop: Float, timeout: Long): Boolean {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return true
        if (!change.pressed || change.isConsumed) return true
        if (change.uptimeMillis - down.uptimeMillis >= timeout) return false
        if ((change.position - down.position).getDistance() > slop) return true
    }
}

/** The index a dragged row lands on once it passes half of each neighbour. */
fun reorderTarget(heights: List<Int>, from: Int, offset: Float): Int {
    var target = from
    var remaining = offset
    if (offset > 0) while (target < heights.lastIndex && remaining > heights[target + 1] / 2f) {
        remaining -= heights[target + 1]; target++
    } else while (target > 0 && -remaining > heights[target - 1] / 2f) {
        remaining += heights[target - 1]; target--
    }
    return target
}
