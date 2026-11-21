package com.nodoubt.feature.solver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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

/**
 * A compact popover: a header with the draft, the depth slider, then 默认 / 预算 chips and a primary
 * 重新解答 button. Dragging and chips only change the draft; only the button sends a request.
 */
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
        modifier = Modifier.width(264.dp).testTag("thinking-menu-${usage.name.lowercase()}-$questionId")
            .semantics { dismiss { onDismiss(); true } },
        shape = RoundedCornerShape(16.dp), containerColor = tokens.raised, shadowElevation = 8.dp,
        properties = PopupProperties(focusable = false)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("思考强度", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, Modifier.weight(1f).testTag("thinking-draft-label"), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = tokens.foregroundAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (options.efforts.size > 1) {
                ThinkingDepthSlider(position, options.efforts.lastIndex, showLevel, label) { value ->
                    val index = value.roundToInt().coerceIn(options.efforts.indices)
                    position = index.toFloat()
                    draft = ThinkingOverride.Effort(options.efforts[index])
                    label = thinkingEffortLabel(options.efforts[index])
                    showLevel = true
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(options.efforts.first(), options.efforts.last()).forEach {
                        Text(thinkingEffortLabel(it), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DraftChip("默认", "thinking-option-default", draft == null, Modifier.weight(1f)) {
                    draft = null; label = "默认"; showLevel = false
                }
                if (options.minimumBudget != null) DraftChip("预算", "thinking-option-budget",
                    draft is ThinkingOverride.Budget, Modifier.weight(1f), onBudget)
            }
            Box(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp).heightIn(min = 44.dp)
                .clip(RoundedCornerShape(12.dp)).background(tokens.action)
                .clickable(role = Role.Button) { onConfirm(draft) }.testTag("confirm-thinking"),
                contentAlignment = Alignment.Center) {
                Text("重新解答", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = tokens.onAction)
            }
        }
    }
}

@Composable
private fun DraftChip(label: String, tag: String, checked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tokens = LocalNoDoubtTokens.current
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.heightIn(min = 40.dp).clip(shape)
        .background(if (checked) tokens.foregroundAccent.copy(alpha = .1f) else tokens.inset)
        .then(if (checked) Modifier.border(1.dp, tokens.foregroundAccent.copy(alpha = .45f), shape) else Modifier)
        .clickable(role = Role.Button, onClick = onClick).testTag(tag).semantics { selected = checked },
        contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge,
            color = if (checked) tokens.foregroundAccent else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal)
    }
}
