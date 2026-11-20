package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nodoubt.core.config.ImportMode
import com.nodoubt.core.config.ImportPreview
import com.nodoubt.core.designsystem.*

/** Preview before anything is written. Replacing asks once more, since it discards current services. */
@Composable
internal fun ImportPreviewDialog(preview: ImportPreview, onApply: (ImportMode) -> Unit, onDismiss: () -> Unit, error: String? = null) {
    var mode by remember(preview) { mutableStateOf(ImportMode.MERGE) }
    var confirmReplace by remember(preview) { mutableStateOf(false) }
    val subscribing = preview.sourceUrl != null && !preview.isRefresh
    val title = when { preview.isRefresh -> "订阅更新"; subscribing -> "订阅配置"; else -> "导入配置" }
    val changes = preview.changes.filter { it != "与当前配置相同" }
    Sheet(title, onDismiss, confirm = when { preview.isRefresh -> "更新"; subscribing -> "订阅"; else -> "导入" },
        onConfirm = { if (mode == ImportMode.REPLACE) confirmReplace = true else onApply(mode) }) {
        InlineStatus(error, error = true)
        Section("", preview.sourceUrl) {
            ItemRow("${preview.config.providers.size} 个服务", "${preview.config.providers.sumOf { it.models.size }} 个模型" +
                if (preview.credentials.isNotEmpty()) "，含 ${preview.credentials.size} 个密钥" else "")
        }
        Section("变更") {
            changes.forEachIndexed { index, change ->
                Text(change, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
                if (index < changes.lastIndex) GroupDivider()
            }
            if (changes.isEmpty()) Caption("与当前配置相同", Modifier.padding(vertical = 12.dp))
        }
        if (preview.warnings.isNotEmpty()) Section("需要留意") {
            preview.warnings.forEachIndexed { index, warning ->
                Text(warning, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
                if (index < preview.warnings.lastIndex) GroupDivider()
            }
        }
        if (!preview.isRefresh) Section("", if (mode == ImportMode.MERGE) "保留现有服务，同名服务以导入为准"
            else "清空现有服务后导入，可恢复上一个版本") {
            ChoiceField("方式", mode.name, listOf(ImportMode.MERGE.name to "合并", ImportMode.REPLACE.name to "替换")) {
                mode = ImportMode.valueOf(it)
            }
        }
    }
    if (confirmReplace) ConfirmDialog("替换当前配置？", "现有服务将被导入内容取代。", "替换", {
        confirmReplace = false; onApply(ImportMode.REPLACE)
    }, { confirmReplace = false }, danger = true)
}
