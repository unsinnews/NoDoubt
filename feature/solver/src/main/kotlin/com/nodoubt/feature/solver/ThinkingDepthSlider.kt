package com.nodoubt.feature.solver

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.nodoubt.core.designsystem.LocalNoDoubtTokens

/** A pale full track invites dragging without claiming a default request sent a particular effort. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThinkingDepthSlider(value: Float, lastIndex: Int, showLevel: Boolean, label: String, onValue: (Float) -> Unit) {
    val tokens = LocalNoDoubtTokens.current
    val neutral = tokens.inset
    val gradient = listOf(Color(0xff629bfa), Color(0xffa777f0))
    val pendingGradient = gradient.map { it.copy(alpha = if (tokens.dark) .24f else .20f).compositeOver(neutral) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Slider(value, onValue, valueRange = 0f..lastIndex.toFloat(), steps = (lastIndex - 1).coerceAtLeast(0),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("thinking-depth-slider")
            .semantics { stateDescription = label },
        thumb = {
            Box(Modifier.size(34.dp).shadow(1.5.dp, CircleShape, clip = false)
                .background(Color.White, CircleShape)
                .border(.75.dp, if (showLevel) Color(0xffd6dce6) else Color(0xffafbbd2), CircleShape))
        },
        track = {
            Canvas(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(50))) {
                val fraction = (value / lastIndex).coerceIn(0f, 1f)
                val fillWidth = size.width * if (showLevel) fraction else 1f
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(neutral, cornerRadius = radius)
                if (fillWidth > 0f) {
                    val left = if (rtl) size.width - fillWidth else 0f
                    val colors = if (showLevel) gradient else pendingGradient
                    drawRoundRect(Brush.horizontalGradient(if (rtl) colors.reversed() else colors,
                        startX = left, endX = left + fillWidth), topLeft = Offset(left, 0f),
                        size = Size(fillWidth, size.height), cornerRadius = radius)
                    listOf(Offset(.12f, .35f), Offset(.28f, .72f), Offset(.47f, .28f),
                        Offset(.67f, .62f), Offset(.85f, .37f)).forEachIndexed { index, point ->
                        val distance = size.width * point.x
                        if (distance < fillWidth - 2.dp.toPx()) drawCircle(
                            Color.White.copy(alpha = if (showLevel) .82f else .58f),
                            radius = (if (index % 2 == 0) 1.2.dp else .8.dp).toPx(),
                            center = Offset(if (rtl) size.width - distance else distance, size.height * point.y))
                    }
                }
            }
        })
}
