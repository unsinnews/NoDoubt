package com.nodoubt.feature.solver

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.LocalNoDoubtTokens
import com.nodoubt.core.designsystem.Symbol
import com.nodoubt.core.designsystem.UiSymbol
import com.nodoubt.core.model.ThinkingOptions
import com.nodoubt.core.model.ThinkingOverride
import com.nodoubt.core.model.Usage

/** The entry describes the actual request; opening its menu never starts another one. */
@Composable
internal fun AnswerThinkingControl(
    questionId: Int, usage: Usage, answer: AnswerState, options: ThinkingOptions?,
    onRetryWithThinking: (ThinkingOverride?) -> Result<Unit>,
    onInputActiveChange: (Boolean) -> Unit,
    modelSelector: @Composable (Modifier) -> Unit, trailingAction: @Composable () -> Unit
) {
    val snapshot = answer.thinkingSnapshot
    val control = remember(questionId, usage, answer.runId, answer.model, snapshot?.protocol) { ThinkingControlState() }
    val label = snapshot?.label ?: "待解答"
    val visibleLabel = if (label == "服务端默认") "默认" else label
    val canChange = options != null
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modelSelector(Modifier.weight(1f).heightIn(min = 48.dp))
            Box {
                Box(Modifier.widthIn(min = 48.dp, max = 132.dp).heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = "本次思考层级：$label"
                            stateDescription = if (control.menu) "已展开" else "已收起"
                        }
                        .clip(RoundedCornerShape(50)).clickable(enabled = canChange, role = Role.Button) {
                            control.budget = false
                            control.error = null
                            control.menu = !control.menu
                        }.testTag("thinking-level-control"), contentAlignment = Alignment.Center) {
                    ThinkingCapsule(visibleLabel, canChange, control.menu)
                }
                if (options != null) ThinkingLevelMenu(questionId, usage, options, answer.thinkingOverride,
                    actualLabel = label,
                    expanded = control.menu, onDismiss = { control.menu = false },
                    onConfirm = { selection ->
                        control.menu = false
                        control.error = null
                        onRetryWithThinking(selection).onFailure { control.error = it.message ?: "无法使用此思考设置" }
                    }, onBudget = { control.menu = false; control.budget = true })
            }
            trailingAction()
        }
        control.error?.let {
            Text(it, Modifier.testTag("thinking-option-error"), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
        }
        if (control.budget && options?.minimumBudget != null) {
            ThinkingBudgetEditor(options, answer.thinkingOverride as? ThinkingOverride.Budget,
                onRetryWithThinking, onClose = { control.budget = false }, onInputActiveChange = onInputActiveChange)
        }
    }
}

/** 思考 + current level, with a chevron that turns while the popover is open. */
@Composable
private fun ThinkingCapsule(label: String, enabled: Boolean, open: Boolean) {
    val tokens = LocalNoDoubtTokens.current
    val accent = if (enabled) tokens.foregroundAccent else MaterialTheme.colorScheme.onSurfaceVariant
    val rotation by animateFloatAsState(if (open) -90f else 90f, label = "thinking-chevron")
    Row(Modifier.background(if (open) accent.copy(alpha = .14f) else accent.copy(alpha = .08f), RoundedCornerShape(50))
        .padding(start = 9.dp, end = if (enabled) 6.dp else 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(11.dp)) {
            // A small spark: the level of reasoning requested.
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2
            drawLine(accent, Offset(c.x, 0f), Offset(c.x, size.height), 1.4.dp.toPx(), StrokeCap.Round)
            drawLine(accent, Offset(0f, c.y), Offset(size.width, c.y), 1.4.dp.toPx(), StrokeCap.Round)
            drawCircle(accent, r * .32f, c)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, color = accent)
        if (enabled) Symbol(UiSymbol.CHEVRON, Modifier.size(11.dp).rotate(rotation), accent)
    }
}

private class ThinkingControlState {
    var menu by mutableStateOf(false)
    var budget by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}
