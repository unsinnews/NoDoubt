package com.nodoubt.feature.channels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.AppConfig
import com.nodoubt.core.model.ModelRef

@Composable
internal fun BindingPicker(
    config: AppConfig, existing: Set<ModelRef>, error: String?, onApply: (List<ModelRef>) -> Unit, onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<ModelRef>()) }
    val available = remember(config.providers) {
        config.providers.flatMap { channel -> channel.models.map { ModelRef(channel.id, it.id) to "${it.name} · ${channel.name}" } }
    }
    val filtered = remember(available, query) { available.filter { it.second.contains(query, true) || it.first.modelId.contains(query, true) } }
    ManagementSheet("添加候选模型", onDismiss) {
        Field("搜索模型或渠道", query, { query = it })
        if (available.isEmpty()) Caption("先在渠道中添加模型，再为用途选择候选模型。")
        else if (filtered.isEmpty()) Caption("没有找到匹配的模型")
        LazyColumn(Modifier.heightIn(max = 320.dp)) {
            items(filtered, key = { "${it.first.channelId}:${it.first.modelId}" }) { (ref, title) ->
                val enabled = ref !in existing
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(enabled) {
                    selected = if (ref in selected) selected - ref else selected + ref
                }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp, horizontal = 4.dp)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge)
                        Caption(if (enabled) ref.modelId else "已添加")
                    }
                    Checkbox(ref in selected || !enabled, null, enabled = enabled)
                }
            }
        }
        InlineStatus(error, error = true)
        ActionButton(if (selected.isEmpty()) "添加" else "添加 ${selected.size} 个模型", { onApply(selected.toList()) },
            Modifier.fillMaxWidth(), enabled = selected.isNotEmpty())
    }
}
