package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.nodoubt.core.ai.*
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.Protocol

@Composable
internal fun CompatibilityScreen(
    protocol: Protocol, value: JsonObject, onChange: (JsonObject) -> Unit, onBack: () -> Unit, modifier: Modifier
) {
    var selected by remember { mutableStateOf<ParameterDefinition?>(null) }
    val definitions = remember(protocol) { ParameterCatalog.compatibility(protocol) }
    var extra by remember { mutableStateOf(false) }
    Page("兼容选项", modifier, onBack = onBack) {
        Section("连接兼容", "通常无需修改。") {
            definitions.forEach { definition ->
                val current = value.atPath(definition.path)
                SettingsRow(definition.label, current?.let(::displayValue) ?: "自动", onClick = { selected = definition })
            }
            if (definitions.isEmpty()) Caption("当前连接使用默认兼容设置。")
        }
        Section("") { SettingsRow("自定义选项", onClick = { extra = true }) }
    }
    if (extra) Sheet("自定义选项", { extra = false }) { JsonObjectEditor("", value, onChange) }
    selected?.let { definition ->
        CompatibilityDialog(definition, value, onChange = { onChange(it); selected = null }, onDismiss = { selected = null })
    }
}

@Composable
private fun CompatibilityDialog(definition: ParameterDefinition, value: JsonObject, onChange: (JsonObject) -> Unit, onDismiss: () -> Unit) {
    val current = value.atPath(definition.path)
    var enabled by remember { mutableStateOf(current != null) }
    var raw by remember { mutableStateOf(current?.let(::displayValue) ?: if (definition.type == ParameterType.BOOLEAN) "false" else "") }
    var error by remember { mutableStateOf<String?>(null) }
    Sheet(definition.label, onDismiss, actions = { QuietButton("完成", {
        runCatching { onChange(value.changedPath(definition.path, if (enabled) parseTyped(raw, definition.type) else null)) }
            .onFailure { error = it.message ?: "值的格式不正确" }
    }) }) {
            Section("") {
                ToggleRow("自定义", enabled, { enabled = it })
                if (enabled) TypedValueField(definition.type, raw, { raw = it; error = null }, definition.choices,
                    definition.suggestedValue?.let { "建议：$it" }.orEmpty())
                InlineStatus(error, error = true)
            }
    }
}
