package com.nodoubt.feature.solver

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.nodoubt.core.designsystem.Symbol
import com.nodoubt.core.designsystem.UiSymbol
import com.nodoubt.core.model.AppConfig
import com.nodoubt.core.model.ModelRef
import com.nodoubt.core.model.Usage

/** The caller sizes the combined heading/model touch target; the model line stays compact. */
@Composable
internal fun AnswerModelPicker(
    answer: AnswerState, questionId: Int, usage: Usage, config: AppConfig, onModel: (ModelRef) -> Unit,
    modifier: Modifier = Modifier, heading: @Composable () -> Unit = {}
) {
    var expanded by remember(questionId, usage, answer.runId, answer.model) { mutableStateOf(false) }
    val binding = config.bindings[usage]
    val refs = (listOfNotNull(answer.model, binding?.selected) + binding?.entries.orEmpty()).distinct()
    val modelId = answer.thinkingSnapshot?.modelId ?: answer.model?.modelId ?: "选择模型"
    Box(modifier.clickable(enabled = refs.isNotEmpty(), role = Role.Button) { expanded = !expanded }
        .semantics { contentDescription = "切换模型" }.testTag("answer-model-control"),
        contentAlignment = Alignment.CenterStart) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            heading()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(modelId, Modifier.weight(1f, fill = false).testTag("answer-request-model"),
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (refs.isNotEmpty()) Symbol(UiSymbol.CHEVRON, Modifier.size(12.dp).rotate(if (expanded) -90f else 90f))
            }
        }
        DropdownMenu(expanded, { expanded = false },
            modifier = Modifier.widthIn(min = 220.dp, max = 300.dp).testTag("answer-model-menu")
                .semantics { dismiss { expanded = false; true } },
            properties = PopupProperties(focusable = false)) {
            Text("切换模型", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            refs.forEach { ref ->
                val channel = config.providers.firstOrNull { it.id == ref.channelId }
                val label = channel?.models?.firstOrNull { it.id == ref.modelId }?.name
                    ?.takeIf { it.isNotBlank() } ?: ref.modelId
                DropdownMenuItem(modifier = Modifier.testTag("answer-model-option-${ref.channelId}-${ref.modelId}")
                    .semantics { selected = answer.model == ref }, text = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(channel?.name?.takeIf { it.isNotBlank() } ?: ref.channelId,
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }, trailingIcon = { if (answer.model == ref) Symbol(UiSymbol.CHECK) }, onClick = {
                    expanded = false
                    if (answer.model != ref) onModel(ref)
                })
            }
        }
    }
}
