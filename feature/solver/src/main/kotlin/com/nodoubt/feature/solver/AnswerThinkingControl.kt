package com.nodoubt.feature.solver

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.LocalNoDoubtTokens
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
                Box(Modifier.widthIn(min = 48.dp, max = 116.dp).heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = "本次思考层级：$label"
                            stateDescription = if (control.menu) "已展开" else "已收起"
                        }
                        .clickable(enabled = canChange, role = Role.Button) {
                            control.budget = false
                            control.error = null
                            control.menu = !control.menu
                        }.testTag("thinking-level-control"), contentAlignment = Alignment.Center) {
                    Text("$visibleLabel ${if (control.menu) "▴" else if (canChange) "▾" else ""}",
                        Modifier.background(LocalNoDoubtTokens.current.inset, RoundedCornerShape(14.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (canChange) LocalNoDoubtTokens.current.foregroundAccent
                            else MaterialTheme.colorScheme.onSurfaceVariant)
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

private class ThinkingControlState {
    var menu by mutableStateOf(false)
    var budget by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}
