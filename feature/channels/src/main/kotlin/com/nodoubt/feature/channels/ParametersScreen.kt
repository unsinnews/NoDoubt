package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nodoubt.core.ai.*
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.*

@Composable
internal fun ParametersScreen(
    protocol: Protocol, values: Map<String, ParameterValue>, modelScope: Boolean,
    onChange: (Map<String, ParameterValue>) -> Unit, onBack: () -> Unit, modifier: Modifier
) {
    val allDefinitions = remember(protocol) { ParameterCatalog.parameters(protocol) }
    val definitions = remember(allDefinitions) { allDefinitions.filterNot { it.isThinkingParameter() } }
    val visibleValues = remember(values, modelScope) { if (modelScope) values else clearThinkingParameters(values) }
    var selected by remember { mutableStateOf<ParameterDefinition?>(null) }
    var adding by remember { mutableStateOf(false) }
    Page("请求参数", modifier, subtitle = protocol.label, onBack = onBack) {
        Section("参数", if (modelScope) "未修改项跟随服务" else "「默认」使用与 pi 相同的默认值") {
            definitions.forEach { definition ->
                ParameterRow(definition, parameterValueAt(visibleValues, definition.path), modelScope) { selected = definition }
            }
        }
        Section("自定义参数") {
            val known = allDefinitions.map { it.path }.toSet()
            visibleValues.filterKeys { it !in known && !isThinkingPath(it) }.forEach { (key, value) ->
                val definition = ParameterDefinition(key, key, typeOf(value.value))
                ParameterRow(definition, value, modelScope) { selected = definition }
            }
            ActionListRow("添加参数", { adding = true })
        }
    }
    selected?.let { definition ->
        ParameterDialog(definition, parameterValueAt(visibleValues, definition.path) ?: ParameterValue(), modelScope,
            onApply = { onChange(values + (definition.path to it)); selected = null }, onDismiss = { selected = null },
            onRemove = if (definition.path in values) ({ onChange(values - definition.path); selected = null }) else null)
    }
    if (adding) NewParameterDialog(values.keys, { definition -> adding = false; selected = definition }, { adding = false }, modelScope)
}

@Composable
internal fun ParameterRow(definition: ParameterDefinition, value: ParameterValue?, modelScope: Boolean, onClick: () -> Unit) {
    val mode = value?.mode ?: ParameterMode.INHERIT
    val state = when (mode) {
        ParameterMode.INHERIT -> if (modelScope) "跟随服务" else "默认"
        ParameterMode.OMIT -> "不发送"
        ParameterMode.VALUE -> definition.labelOf(displayValue(value?.value)).take(120)
    }
    SettingsRow(definition.label + if (definition.required) " · 必需" else "", state, onClick = onClick)
}

@Composable
internal fun ParameterDialog(
    definition: ParameterDefinition, value: ParameterValue, modelScope: Boolean,
    onApply: (ParameterValue) -> Unit, onDismiss: () -> Unit, onRemove: (() -> Unit)? = null
) {
    var mode by remember { mutableStateOf(value.mode) }
    var raw by remember { mutableStateOf(if (value.value != null) displayValue(value.value) else "") }
    var error by remember { mutableStateOf<String?>(null) }
    Sheet(definition.label, onDismiss, confirm = "完成", onConfirm = {
        runCatching {
            val json = if (mode == ParameterMode.VALUE) parseTyped(raw, definition.type) else null
            onApply(ParameterValue(mode, json))
        }.onFailure { error = it.message ?: "值的格式不正确" }
    }) {
        Section("", definition.description.takeIf { it.isNotBlank() }) {
            ChoiceField("发送方式", mode.name, listOf(
                ParameterMode.INHERIT.name to if (modelScope) "跟随服务" else "默认",
                ParameterMode.VALUE.name to "自定义", ParameterMode.OMIT.name to "不发送"
            )) {
                mode = ParameterMode.valueOf(it)
                if (mode == ParameterMode.VALUE && raw.isBlank() && definition.type == ParameterType.BOOLEAN) raw = "false"
                error = null
            }
            if (mode == ParameterMode.VALUE) TypedValueField(definition.type, raw, { raw = it; error = null },
                choices = definition.choices, hint = definition.suggestedValue?.let { "建议 $it" }.orEmpty(),
                labelOf = definition::labelOf)
        }
        InlineStatus(error, error = true)
        if (onRemove != null) Section("") { ActionListRow("恢复默认", onRemove, UiSymbol.TRASH, danger = true) }
    }
}

@Composable
private fun NewParameterDialog(existing: Set<String>, onApply: (ParameterDefinition) -> Unit, onDismiss: () -> Unit, modelScope: Boolean) {
    var path by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ParameterType.STRING) }
    var error by remember { mutableStateOf<String?>(null) }
    Sheet("添加参数", onDismiss, confirm = "下一步", confirmEnabled = path.isNotBlank(), onConfirm = {
        val name = path.trim()
        when {
            name.isBlank() || name.split('.').any { it.isBlank() } -> error = "路径无效"
            name in existing -> error = "参数已存在"
            isThinkingPath(name) -> error = "请在模型的思考中设置"
            name in setOf("model", "messages", "stream") -> error = "由应用自动填写"
            else -> onApply(ParameterDefinition(name, name, type))
        }
    }) {
        Section("", "用 . 表示嵌套") {
            Field("字段路径", path, { path = it; error = null }, hint = "vendor_options.mode")
            ChoiceField("类型", type.name, ParameterType.entries.map { it.name to typeLabel(it) }) { type = ParameterType.valueOf(it) }
        }
        InlineStatus(error, error = true)
    }
}
