package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nodoubt.core.config.ImportMode
import com.nodoubt.core.config.ImportPreview
import com.nodoubt.core.designsystem.*

@Composable
internal fun ImportPreviewDialog(preview: ImportPreview, onApply: (ImportMode) -> Unit, onDismiss: () -> Unit, error: String? = null) {
    var mode by remember(preview) { mutableStateOf(ImportMode.MERGE) }
    var confirmed by remember(preview) { mutableStateOf(false) }
    ManagementSheet("导入预览", onDismiss) {
        InlineStatus(error, error = true)
        Section("配置内容") {
            ItemRow("${preview.config.providers.size} 个渠道", "${preview.config.providers.sumOf { it.models.size }} 个模型")
            preview.sourceUrl?.let { ItemRow("来源", it) }
            if (preview.changes.isEmpty()) Caption("没有配置差异")
            preview.changes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        if (preview.warnings.isNotEmpty()) Section("需要留意") {
            preview.warnings.forEach { InlineStatus(it, error = true) }
        }
        Section("导入方式") {
            listOf(ImportMode.MERGE to "合并配置", ImportMode.REPLACE to "替换当前配置").forEach { (value, title) ->
                ItemRow(title, if (value == ImportMode.MERGE) "保留已有渠道和用途" else "使用此次导入的渠道和用途",
                    onClick = { mode = value; confirmed = false }) {
                    if (mode == value) Symbol(UiSymbol.CHECK, Modifier.semantics { contentDescription = "已选择" },
                        LocalNoDoubtTokens.current.foregroundAccent)
                }
            }
            if (mode == ImportMode.REPLACE) ToggleRow("确认替换", confirmed, { confirmed = it },
                "现有配置会被替换，可在配置文件中恢复上一个版本。")
        }
        ActionButton("导入配置", { onApply(mode) }, Modifier.fillMaxWidth(),
            enabled = mode != ImportMode.REPLACE || confirmed)
    }
}
