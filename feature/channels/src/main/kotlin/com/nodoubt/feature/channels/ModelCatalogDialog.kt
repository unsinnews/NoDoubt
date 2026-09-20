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
import com.nodoubt.core.model.ModelConfig

@Composable
internal fun ModelCatalogDialog(
    models: List<ModelConfig>, existing: Set<String>, onApply: (List<ModelConfig>) -> Unit,
    onDismiss: () -> Unit, title: String = "模型目录", single: Boolean = false
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    Sheet(title, onDismiss, actions = {
        QuietButton(if (single) "测试" else "添加", { onApply(models.filter { it.id in selected }) }, enabled = selected.isNotEmpty())
    }) {
        Section("") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("搜索模型", query, { query = it })
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(models.filter { it.id.contains(query, true) || it.name.contains(query, true) }, key = { it.id }) { model ->
                        val enabled = model.id !in existing
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled) {
                            selected = if (single) setOf(model.id) else if (model.id in selected) selected - model.id else selected + model.id
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(model.name.ifBlank { model.id }, style = MaterialTheme.typography.bodyLarge)
                                if (!enabled) Caption("已添加") else if (model.name != model.id) Caption(model.id)
                            }
                            if (model.id in selected || !enabled) Symbol(UiSymbol.CHECK,
                                color = LocalNoDoubtTokens.current.foregroundAccent.copy(alpha = if (enabled) 1f else .4f))
                        }
                    }
                }
                if (models.isEmpty()) Caption("暂无模型，可以返回后手动添加。")
            }
        }
        Caption("${models.size} 个模型 · 已选 ${selected.size} 个")
    }
}
