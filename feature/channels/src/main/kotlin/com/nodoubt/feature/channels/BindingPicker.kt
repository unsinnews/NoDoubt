package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.AppConfig
import com.nodoubt.core.model.ModelRef

@Composable
internal fun BindingPicker(
    config: AppConfig, existing: Set<ModelRef>, error: String?, onApply: (List<ModelRef>) -> Unit, onDismiss: () -> Unit,
    title: String = "添加模型"
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<ModelRef>()) }
    val available = remember(config.providers) {
        config.providers.flatMap { channel ->
            channel.models.map { ModelRef(channel.id, it.id) to (it.name.ifBlank { it.id } to channel.name) }
        }
    }
    val filtered = remember(available, query) {
        available.filter { (ref, names) -> listOf(names.first, names.second, ref.modelId).any { it.contains(query, true) } }
    }
    Sheet(title, onDismiss, confirm = "添加",
        onConfirm = { onApply(selected.toList()) }, confirmEnabled = selected.isNotEmpty()) {
        InlineStatus(error, error = true)
        Section("", if (available.isEmpty()) null
            else "已选 ${selected.size} 个") {
            if (available.size > 8) Field("搜索", query, { query = it }, hint = "模型或服务")
            if (available.isEmpty()) Caption("暂无模型，请先添加服务", Modifier.padding(vertical = 14.dp))
            else if (filtered.isEmpty()) Caption("没有匹配的模型", Modifier.padding(vertical = 14.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(filtered, key = { "${it.first.channelId}:${it.first.modelId}" }) { (ref, names) ->
                    val enabled = ref !in existing
                    SelectableRow(names.first, if (enabled) names.second else "已添加", ref in selected || !enabled, enabled) {
                        selected = if (ref in selected) selected - ref else selected + ref
                    }
                }
            }
        }
    }
}
