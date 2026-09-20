package com.nodoubt.feature.solver

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.nodoubt.core.designsystem.LocalNoDoubtTokens
import com.nodoubt.core.model.ThinkingOptions
import com.nodoubt.core.model.ThinkingOverride
import com.nodoubt.core.model.Usage
import kotlin.math.roundToInt

@Composable
internal fun ThinkingLevelMenu(
    questionId: Int, usage: Usage, options: ThinkingOptions, current: ThinkingOverride?, actualLabel: String,
    expanded: Boolean, onDismiss: () -> Unit, onConfirm: (ThinkingOverride?) -> Unit, onBudget: () -> Unit
) {
    if (!expanded) return
    val initialIndex = options.efforts.indexOfFirst {
        thinkingEffortLabel(it) == actualLabel.substringBefore(" · ") || it.equals(actualLabel, ignoreCase = true)
    }
    var draft by remember { mutableStateOf(current) }
    var label by remember { mutableStateOf(when {
        initialIndex >= 0 -> thinkingEffortLabel(options.efforts[initialIndex])
        actualLabel == "服务端默认" -> "默认"
        else -> actualLabel
    }) }
    var position by remember { mutableFloatStateOf(initialIndex.coerceAtLeast(0).toFloat()) }
    var showLevel by remember { mutableStateOf(initialIndex >= 0) }
    val tokens = LocalNoDoubtTokens.current
    DropdownMenu(true, onDismiss,
        modifier = Modifier.width(248.dp).testTag("thinking-menu-${usage.name.lowercase()}-$questionId")
            .semantics { dismiss { onDismiss(); true } },
        properties = PopupProperties(focusable = false)) {
        Column(Modifier.padding(horizontal = 10.dp)) {
            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("思考", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, Modifier.weight(1f).testTag("thinking-draft-label"), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = tokens.foregroundAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (options.efforts.size > 1) ThinkingDepthSlider(position, options.efforts.lastIndex, showLevel, label) { value ->
                val index = value.roundToInt().coerceIn(options.efforts.indices)
                position = index.toFloat()
                draft = ThinkingOverride.Effort(options.efforts[index])
                label = thinkingEffortLabel(options.efforts[index])
                showLevel = true
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                DraftAction("默认", "thinking-option-default", draft == null, Modifier.weight(1f)) {
                    draft = null; label = "默认"; showLevel = false
                }
                if (options.minimumBudget != null) DraftAction("预算", "thinking-option-budget",
                    draft is ThinkingOverride.Budget, Modifier.weight(1f), onBudget)
                DraftAction("确认", "confirm-thinking", true, Modifier.weight(1f)) { onConfirm(draft) }
            }
        }
    }
}

@Composable
private fun DraftAction(label: String, tag: String, checked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tokens = LocalNoDoubtTokens.current
    TextButton(onClick, modifier.heightIn(min = 48.dp).testTag(tag).semantics { selected = checked },
        contentPadding = PaddingValues(horizontal = 2.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (checked) tokens.foregroundAccent
            else MaterialTheme.colorScheme.onSurfaceVariant)) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal)
    }
}
