package com.nodoubt.core.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChoiceField(label: String, selected: String, choices: List<Pair<String, String>>, onSelect: (String) -> Unit) {
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
