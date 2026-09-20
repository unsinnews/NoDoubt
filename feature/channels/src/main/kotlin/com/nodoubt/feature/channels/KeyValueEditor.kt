package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            ItemRow(entryKey, if (sensitive || isSecretHeader(entryKey)) "已配置" else entryValue,
                onClick = { editingKey = entryKey; key = entryKey; value = if (sensitive) "" else entryValue; open = true; error = null })
        }
        ActionListRow("添加", { editingKey = null; key = ""; value = ""; open = true; error = null })
    }
    if (open && LocalScreenActive.current) AlertDialog(onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large,
        title = { Text(label) }, text = {
            DialogBody {
                Field("名称", key, { key = it; error = null })
                Field("值", value, { value = it }, secret = sensitive || isSecretHeader(key),
                    hint = if (sensitive && editingKey != null) "留空保留已保存值" else "")
                InlineStatus(error, error = true)
                if (editingKey != null) ActionListRow("删除", { onChange(values - requireNotNull(editingKey)); open = false }, UiSymbol.TRASH, danger = true)
            }
        }, confirmButton = { QuietButton("应用", {
            val normalized = key.trim()
            when {
                normalized.isBlank() -> error = "名称不能为空"
                normalized != editingKey && normalized in values -> error = "名称已存在"
                normalized.contains('\n') || value.contains('\n') -> error = "名称和值不能包含换行"
                else -> {
                    val resolved = if (sensitive && value.isBlank() && editingKey != null) values[editingKey].orEmpty() else value
                    onChange((values - (editingKey ?: "")) + (normalized to resolved)); open = false
                }
            }
        }) }, dismissButton = { QuietButton("取消", { open = false }) })
}

private fun isSecretHeader(key: String): Boolean = listOf("authorization", "api-key", "apikey", "token", "secret", "password")
    .any { key.contains(it, ignoreCase = true) }
