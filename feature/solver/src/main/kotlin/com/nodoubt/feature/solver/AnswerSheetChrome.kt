package com.nodoubt.feature.solver

import android.view.MotionEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.Usage
import kotlin.math.abs

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AnswerDragHandle(onResizeBy: (Float) -> Unit) {
    val resize by rememberUpdatedState(onResizeBy)
    var previousY by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = "上下拖动，调整答案窗高度" }
        .pointerInteropFilter { event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { previousY = event.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    val nextY = event.rawY
                    resize(nextY - previousY)
                    previousY = nextY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 38.dp, height = 4.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .3f), CircleShape))
    }
}

@Composable
internal fun AnswerModePicker(usage: Usage, select: (Usage) -> Unit) {
    val tokens = LocalNoDoubtTokens.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        .height(48.dp).background(tokens.inset, RoundedCornerShape(12.dp))) {
        val itemWidth = (maxWidth - 6.dp) / 2
        val offset by animateDpAsState(if (usage == Usage.FAST) 3.dp else itemWidth + 3.dp, label = "answer-mode")
        Surface(Modifier.offset(x = offset, y = 3.dp).width(itemWidth).height(42.dp),
            shape = RoundedCornerShape(9.dp), color = tokens.raised, shadowElevation = 1.dp) {}
        Row(Modifier.fillMaxSize().padding(horizontal = 3.dp).selectableGroup()) {
            listOf(Usage.FAST to "极速解题", Usage.DEEP to "深度思考").forEach { (mode, title) ->
                Box(Modifier.weight(1f).fillMaxHeight().selectable(usage == mode, role = Role.Tab,
                    onClick = { select(mode) }), contentAlignment = Alignment.Center) {
                    Text(title, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (usage == mode) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (usage == mode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

internal fun Modifier.answerModeSwipe(select: (Usage) -> Unit): Modifier = composed {
    val currentSelect by rememberUpdatedState(select)
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    pointerInput(threshold) {
        var distance = 0f
        detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragCancel = { distance = 0f },
            onDragEnd = {
                if (abs(distance) >= threshold) currentSelect(if (distance > 0) Usage.FAST else Usage.DEEP)
            }, onHorizontalDrag = { change, delta -> change.consume(); distance += delta })
    }
}
