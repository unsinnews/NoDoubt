package com.nodoubt.feature.channels

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.nodoubt.core.ai.*
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.Protocol

private const val AUTO = "\u0000auto"

@Composable
internal fun CompatibilityScreen(
    protocol: Protocol, value: JsonObject, onChange: (JsonObject) -> Unit, onBack: () -> Unit, modifier: Modifier
) {
    var selected by remember { mutableStateOf<ParameterDefinition?>(null) }
    val definitions = remember(protocol) { ParameterCatalog.compatibility(protocol) }
    val known = remember(definitions) { definitions.map { it.path.substringBefore('.') }.toSet() }
    Page("兼容选项", modifier, subtitle = protocol.label, onBack = onBack) {
        if (definitions.isNotEmpty()) Section("", "未设置的项按服务地址自动判断，与 pi 一致") {
            definitions.forEach { definition ->
                val current = value.atPath(definition.path)?.let(::displayValue)
                SettingsRow(definition.label, current?.let(definition::labelOf) ?: "自动", onClick = { selected = definition })
            }
        }
        JsonObjectEditor("其他字段", value.filtered { it !in known }, { extra ->
            onChange(value.filtered { it in known }.apply { extra.entrySet().forEach { (key, item) -> add(key, item) } })
        }, if (definitions.isEmpty()) "当前协议无预设项" else null)
    }
    selected?.let { definition ->
        CompatibilityDialog(definition, value, onChange = { onChange(it); selected = null }, onDismiss = { selected = null })
    }
}

@Composable
private fun CompatibilityDialog(definition: ParameterDefinition, value: JsonObject, onChange: (JsonObject) -> Unit, onDismiss: () -> Unit) {
    val current = value.atPath(definition.path)
    val options = when {
        definition.type == ParameterType.BOOLEAN -> listOf("true", "false")
        else -> definition.choices
    }
    if (options.isNotEmpty()) {
        val selected = current?.let(::displayValue) ?: AUTO
        Sheet(definition.label, onDismiss) {
            Section("", definition.description) {
                (listOf(AUTO) + options).forEach { option ->
                    ItemRow(if (option == AUTO) "自动" else definition.labelOf(option), onClick = {
                        val next = when {
                            option == AUTO -> null
                            definition.type == ParameterType.BOOLEAN -> JsonPrimitive(option.toBoolean())
                            else -> JsonPrimitive(option)
                        }
                        onChange(value.changedPath(definition.path, next))
                    }) {
                        if (option == selected) Symbol(UiSymbol.CHECK, color = LocalNoDoubtTokens.current.foregroundAccent)
                    }
                }
            }
        }
        return
    }
    var raw by remember { mutableStateOf(current?.let(::displayValue).orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    Sheet(definition.label, onDismiss, confirm = "完成", onConfirm = {
        runCatching { onChange(value.changedPath(definition.path, raw.takeIf { it.isNotBlank() }?.let { parseTyped(it, definition.type) })) }
            .onFailure { error = it.message ?: "值的格式不正确" }
    }) {
        Section("", "${definition.description}；留空则自动") {
            TypedValueField(definition.type, raw, { raw = it; error = null }, hint = definition.suggestedValue.orEmpty())
        }
        InlineStatus(error, error = true)
    }
}

private fun JsonObject.filtered(keep: (String) -> Boolean) = JsonObject().also { result ->
    entrySet().filter { keep(it.key) }.forEach { (key, item) -> result.add(key, item.deepCopy()) }
}
