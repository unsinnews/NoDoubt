package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.gson.*
import com.nodoubt.core.ai.ParameterType
import com.nodoubt.core.designsystem.*

internal fun typeOf(value: JsonElement?): ParameterType = when {
    value == null || value.isJsonNull -> ParameterType.STRING
    value.isJsonObject -> ParameterType.OBJECT
    value.isJsonArray -> ParameterType.ARRAY
    value.asJsonPrimitive.isBoolean -> ParameterType.BOOLEAN
    value.asJsonPrimitive.isNumber -> ParameterType.NUMBER
    else -> ParameterType.STRING
}

internal fun displayValue(value: JsonElement?): String = when {
    value == null || value.isJsonNull -> ""
    value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString
    else -> value.toString()
}

internal fun parseTyped(value: String, type: ParameterType): JsonElement {
    if (type == ParameterType.STRING) return JsonPrimitive(value)
    val parsed = JsonParser.parseString(value)
    val valid = when (type) {
        ParameterType.OBJECT -> parsed.isJsonObject
        ParameterType.ARRAY -> parsed.isJsonArray
        ParameterType.BOOLEAN -> parsed.isJsonPrimitive && parsed.asJsonPrimitive.isBoolean
        ParameterType.NUMBER -> parsed.isJsonPrimitive && parsed.asJsonPrimitive.isNumber
        ParameterType.INTEGER -> parsed.isJsonPrimitive && parsed.asJsonPrimitive.isNumber && value.toLongOrNull() != null
        ParameterType.STRING -> true
    }
    require(valid) { "值与所选类型不匹配" }
    return parsed
}

internal fun typeLabel(type: ParameterType): String = when (type) {
    ParameterType.STRING -> "文本"
    ParameterType.NUMBER -> "数字"
    ParameterType.INTEGER -> "整数"
    ParameterType.BOOLEAN -> "布尔值"
    ParameterType.OBJECT -> "JSON 对象"
    ParameterType.ARRAY -> "JSON 数组"
}

@Composable
internal fun TypedValueField(type: ParameterType, raw: String, onChange: (String) -> Unit, choices: List<String> = emptyList(), hint: String = "") {
    when {
        type == ParameterType.BOOLEAN -> ChoiceField("值", raw.ifBlank { "false" }, listOf("true" to "true", "false" to "false"), onChange)
        choices.isNotEmpty() -> {
            ChoiceField("预设选项", raw, choices.map { it to it }, onChange)
            Field("自定义值", raw, onChange, hint = hint)
        }
        else -> Field("值", raw, onChange, multiline = type == ParameterType.OBJECT || type == ParameterType.ARRAY,
            hint = hint, keyboardType = if (type == ParameterType.INTEGER || type == ParameterType.NUMBER) KeyboardType.Decimal else KeyboardType.Text)
    }
}

@Composable
internal fun JsonObjectEditor(title: String, value: JsonObject, onChange: (JsonObject) -> Unit, description: String? = null) {
    var editing by remember { mutableStateOf(false) }
    var previousKey by remember { mutableStateOf<String?>(null) }
    var key by remember { mutableStateOf("") }
    var raw by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ParameterType.STRING) }
    var error by remember { mutableStateOf<String?>(null) }
    Section(title, description) {
        value.entrySet().forEach { entry ->
            ItemRow(entry.key, "${typeLabel(typeOf(entry.value))} · ${displayValue(entry.value).take(140)}", onClick = {
                previousKey = entry.key; key = entry.key; raw = displayValue(entry.value)
                type = typeOf(entry.value); error = null; editing = true
            }) {
                QuietButton("移除", { onChange(value.deepCopy().apply { remove(entry.key) }) }, danger = true)
            }
        }
        OutlineButton("添加字段", { previousKey = null; key = ""; raw = ""; type = ParameterType.STRING; error = null; editing = true })
    }
    if (editing && LocalScreenActive.current) AlertDialog(onDismissRequest = { editing = false }, shape = MaterialTheme.shapes.large,
        title = { Text("编辑字段") }, text = {
            DialogBody {
                Field("字段名", key, { key = it; error = null })
                ChoiceField("数据类型", type.name, ParameterType.entries.map { it.name to typeLabel(it) }) {
                    type = ParameterType.valueOf(it)
                    raw = when (type) { ParameterType.OBJECT -> "{}"; ParameterType.ARRAY -> "[]"; ParameterType.BOOLEAN -> "false"; else -> "" }
                }
                TypedValueField(type, raw, { raw = it; error = null })
                InlineStatus(error, error = true)
            }
        }, confirmButton = { QuietButton("应用", {
            runCatching {
                val name = key.trim()
                require(name.isNotBlank()) { "字段名不能为空" }
                require(name == previousKey || !value.has(name)) { "字段名已存在" }
                val parsed = parseTyped(raw, type)
                onChange(value.deepCopy().apply { previousKey?.let(::remove); add(name, parsed) })
                editing = false
            }.onFailure { error = it.message ?: "JSON 格式不正确" }
        }) }, dismissButton = { QuietButton("取消", { editing = false }) })
}

internal fun JsonObject.atPath(path: String): JsonElement? = path.split('.').fold(this as JsonElement?) { value, part ->
    value?.takeIf { it.isJsonObject }?.asJsonObject?.get(part)
}

internal fun JsonObject.changedPath(path: String, value: JsonElement?): JsonObject = deepCopy().apply {
    val parts = path.split('.')
    var current = this
    parts.dropLast(1).forEach { part ->
        current = current.get(part)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject().also { current.add(part, it) }
    }
    if (value == null) current.remove(parts.last()) else current.add(parts.last(), value)
}
