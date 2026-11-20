package com.nodoubt.feature.channels

import androidx.compose.runtime.*
import com.nodoubt.core.designsystem.*

@Composable
internal fun KeyValueEditor(label: String, values: Map<String, String>, sensitive: Boolean = false, onChange: (Map<String, String>) -> Unit) {
    var editingKey by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Section(label) {
        values.forEach { (entryKey, entryValue) ->
            EditableRow(entryKey, if (sensitive || isSecretHeader(entryKey)) "已配置" else entryValue,
                onClick = { editingKey = entryKey; key = entryKey; value = if (sensitive) "" else entryValue; open = true; error = null },
                onDelete = { onChange(values - entryKey) })
        }
        ActionListRow("添加$label", { editingKey = null; key = ""; value = ""; open = true; error = null })
    }
    if (open) Sheet(if (editingKey == null) "添加$label" else "编辑$label", { open = false }, confirm = "完成", onConfirm = {
        val normalized = key.trim()
        when {
            normalized.isBlank() -> error = "请输入名称"
            normalized != editingKey && normalized in values -> error = "已有同名项目"
            normalized.contains('\n') || value.contains('\n') -> error = "名称和值不能换行"
            else -> {
                val resolved = if (sensitive && value.isBlank() && editingKey != null) values[editingKey].orEmpty() else value
                onChange((values - (editingKey ?: "")) + (normalized to resolved)); open = false
            }
        }
    }) {
        Section("") {
            Field("名称", key, { key = it; error = null }, hint = "例如 X-Api-Key")
            Field("值", value, { value = it }, secret = sensitive || isSecretHeader(key),
                hint = if (sensitive && editingKey != null) "留空不修改" else "输入值")
        }
        InlineStatus(error, error = true)
    }
}

private fun isSecretHeader(key: String): Boolean = listOf("authorization", "api-key", "apikey", "token", "secret", "password")
    .any { key.contains(it, ignoreCase = true) }
