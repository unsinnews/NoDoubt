package com.nodoubt.feature.channels

import androidx.compose.runtime.*
import com.google.gson.JsonPrimitive
import com.nodoubt.core.ai.ParameterCatalog
import com.nodoubt.core.ai.ParameterDefinition
import com.nodoubt.core.ai.ParameterType
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.ParameterMode
import com.nodoubt.core.model.ParameterValue
import com.nodoubt.core.model.Protocol

@Composable
internal fun ThinkingSettings(
    protocol: Protocol, values: Map<String, ParameterValue>,
    onChange: (Map<String, ParameterValue>) -> Unit, active: Boolean = true
) {
    val definitions = remember(protocol) { ParameterCatalog.parameters(protocol).filter { it.isThinkingParameter() } }
    var modifying by remember(protocol) { mutableStateOf(hasThinkingParameters(values)) }
    var editing by remember { mutableStateOf<ParameterDefinition?>(null) }
    Section("思考", if (modifying && protocol == Protocol.ANTHROPIC_MESSAGES) "手动开启思考时需填写预算，至少 1024。" else null) {
        ChoiceField("思考设置", if (modifying) "modify" else "default", listOf("default" to "默认", "modify" to "修改")) { selection ->
            modifying = selection == "modify"
            if (!modifying) { onChange(clearThinkingParameters(values)); editing = null }
        }
        if (modifying) {
            definitions.forEach { definition ->
                val value = parameterValueAt(values, definition.path)?.takeIf { it.mode == ParameterMode.VALUE }
                if (definition.choices.isNotEmpty() || definition.type == ParameterType.BOOLEAN) {
                    val raw = value?.value?.let(::displayValue).orEmpty()
                    val options = if (definition.type == ParameterType.BOOLEAN) listOf("true", "false") else definition.choices
                    val labels = (options + raw.takeIf { it.isNotBlank() }).filterNotNull().distinct().map {
                        "value:$it" to thinkingValueLabel(it)
                    } + if (definition.type == ParameterType.STRING) listOf("custom" to "自定义…") else emptyList()
                    ChoiceField(definition.label, if (raw.isEmpty()) "" else "value:$raw", labels) { selection ->
                        if (selection == "custom") editing = definition
                        else {
                            val choice = selection.removePrefix("value:")
                            val next = if (definition.type == ParameterType.BOOLEAN) JsonPrimitive(choice.toBoolean()) else JsonPrimitive(choice)
                            onChange(clearThinkingField(values, definition.path) + (definition.path to ParameterValue(ParameterMode.VALUE, next)))
                        }
                    }
                } else SettingsRow(definition.label, value?.value?.let(::displayValue)?.take(100) ?: "填写") { editing = definition }
            }
            val known = definitions.map { it.path }.toSet()
            values.filterKeys { isThinkingPath(it) && it !in known }.forEach { (path, value) ->
                val definition = ParameterDefinition(path, path, typeOf(value.value))
                SettingsRow(path, value.value?.let(::displayValue)?.take(100)) { editing = definition }
            }
        }
    }
    editing?.takeIf { active }?.let { definition ->
        ThinkingValueSheet(definition, parameterValueAt(values, definition.path), onApply = { next ->
            val cleaned = clearThinkingField(values, definition.path)
            onChange(if (next == null) cleaned else cleaned + (definition.path to next))
            editing = null
        }, onDismiss = { editing = null })
    }
}

@Composable
private fun ThinkingValueSheet(
    definition: ParameterDefinition, value: ParameterValue?, onApply: (ParameterValue?) -> Unit, onDismiss: () -> Unit
) {
    var raw by remember { mutableStateOf(value?.takeIf { it.mode == ParameterMode.VALUE }?.value?.let(::displayValue).orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    Sheet(definition.label, onDismiss, actions = { QuietButton("完成", {
        runCatching {
            onApply(raw.takeIf { it.isNotBlank() }?.let { ParameterValue(ParameterMode.VALUE, parseTyped(it, definition.type)) })
        }.onFailure { error = it.message ?: "值的格式不正确" }
    }) }) {
        Section("") {
            TypedValueField(definition.type, raw, { raw = it; error = null }, hint = definition.suggestedValue.orEmpty())
            InlineStatus(error, error = true)
        }
    }
}

private fun thinkingValueLabel(value: String): String = when (value.lowercase()) {
    "none", "disabled", "false" -> "关闭"
    "enabled", "true" -> "开启"
    "minimal" -> "最低"
    "low" -> "低"
    "medium" -> "中"
    "high" -> "高"
    "xhigh" -> "很高"
    "max" -> "最高"
    "adaptive" -> "自适应"
    "auto" -> "自动"
    "concise", "summarized" -> "精简"
    "detailed" -> "详细"
    "omitted" -> "不显示"
    else -> value
}
