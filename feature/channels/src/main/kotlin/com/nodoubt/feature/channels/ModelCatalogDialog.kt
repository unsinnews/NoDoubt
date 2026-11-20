package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.ModelConfig

@Composable
internal fun ModelCatalogDialog(
    models: List<ModelConfig>, existing: Set<String>, onApply: (List<ModelConfig>) -> Unit,
    onDismiss: () -> Unit, title: String = "添加模型", single: Boolean = false
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val filtered = remember(models, query) { models.filter { it.id.contains(query, true) || it.name.contains(query, true) } }
    Sheet(title, onDismiss, confirm = if (single) null else "添加",
        onConfirm = { onApply(models.filter { it.id in selected }) }, confirmEnabled = selected.isNotEmpty()) {
        Section("", when {
            models.isEmpty() -> null
            single -> "轻点模型开始测试"
            else -> "已选 ${selected.size} / ${models.size}"
        }) {
            if (models.size > 8) Field("搜索", query, { query = it }, hint = "模型名称或 ID")
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(filtered, key = { it.id }) { model ->
                    val enabled = model.id !in existing
                    SelectableRow(model.name.ifBlank { model.id },
                        if (!enabled) "已添加" else model.id.takeIf { model.name.isNotBlank() && model.name != model.id },
                        model.id in selected || !enabled, enabled) {
                        if (single) onApply(listOf(model))
                        else selected = if (model.id in selected) selected - model.id else selected + model.id
                    }
                }
            }
            if (models.isEmpty()) Caption("未获取到模型，请手动添加", Modifier.padding(vertical = 14.dp))
            else if (filtered.isEmpty()) Caption("没有匹配的模型", Modifier.padding(vertical = 14.dp))
        }
    }
}
