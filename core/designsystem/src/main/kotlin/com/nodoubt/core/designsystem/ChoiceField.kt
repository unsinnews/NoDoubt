package com.nodoubt.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Up to four short options render inline as a segmented control; longer lists open a picker sheet.
 * Both apply on tap, so no picker needs a separate confirmation.
 */
@Composable
fun ChoiceField(label: String, selected: String, choices: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    if (choices.size in 2..4 && choices.sumOf { it.second.length } <= 10 && choices.all { it.second.length <= 4 }) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Segmented(selected, choices, onSelect)
            }
            GroupDivider()
        }
        return
    }
    var expanded by remember { mutableStateOf(false) }
    SettingsRow(label, choices.firstOrNull { it.first == selected }?.second ?: selected.ifBlank { "请选择" },
        onClick = { expanded = true })
    if (expanded) Sheet(label, { expanded = false }) {
        Section("") {
            choices.forEach { (value, title) ->
                ItemRow(title, onClick = { onSelect(value); expanded = false }) {
                    if (value == selected) Symbol(UiSymbol.CHECK, color = LocalNoDoubtTokens.current.foregroundAccent)
                }
            }
        }
    }
}

/** iOS-style segmented control. The visual track is 34dp tall; each segment keeps a 48dp touch target. */
@Composable
fun Segmented(selected: String, choices: List<Pair<String, String>>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalNoDoubtTokens.current
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = if (tokens.dark) .14f else .07f)
    val thumb = if (tokens.dark) MaterialTheme.colorScheme.onSurface.copy(alpha = .22f) else Color.White
    Row(modifier.heightIn(min = 48.dp).drawBehind {
        val height = 34.dp.toPx()
        drawRoundRect(track, Offset(0f, (size.height - height) / 2), Size(size.width, height), CornerRadius(9.dp.toPx()))
    }.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        choices.forEach { (value, title) ->
            val on = value == selected
            Box(Modifier.heightIn(min = 48.dp).widthIn(min = 52.dp)
                .clickable(role = Role.Tab) { if (!on) onSelect(value) }
                .semantics { this.selected = on }
                .drawBehind {
                    if (on) {
                        val height = 30.dp.toPx()
                        drawRoundRect(thumb, Offset(0f, (size.height - height) / 2), Size(size.width, height), CornerRadius(7.dp.toPx()))
                    }
                }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A compact − value + stepper for small integer ranges, replacing long numeric pickers. */
@Composable
fun StepperRow(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, display: (Int) -> String = { "$it" }) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton("−", "减小$label", value > range.first) { onChange(value - 1) }
                Text(display(value), Modifier.widthIn(min = 36.dp), style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center)
                StepButton("+", "增大$label", value < range.last) { onChange(value + 1) }
            }
        }
        GroupDivider()
    }
}

@Composable
private fun StepButton(text: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick, Modifier.size(48.dp).semantics { contentDescription = description },
        enabled = enabled, contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = LocalNoDoubtTokens.current.foregroundAccent)) {
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}
