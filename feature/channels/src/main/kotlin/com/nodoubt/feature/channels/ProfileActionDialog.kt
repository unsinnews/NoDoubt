package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nodoubt.core.designsystem.*

@Composable
internal fun ProfileNameDialog(title: String, initial: String, onApply: (String) -> Unit, onDismiss: () -> Unit, error: String? = null) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.large, title = { Text(title) },
        text = { DialogBody { Field("名称", name, { name = it }); InlineStatus(error, error = true) } },
        confirmButton = { QuietButton("保存", { onApply(name.trim()) }, enabled = name.isNotBlank()) },
        dismissButton = { QuietButton("取消", onDismiss) })
}

@Composable
internal fun ExportConfigurationSheet(name: String, onExport: (Boolean) -> Unit, onDismiss: () -> Unit, error: String?) {
    var includeSecrets by remember { mutableStateOf(false) }
    ManagementSheet("导出配置", onDismiss) {
        Section("应用配置 JSON") { ItemRow(name, "渠道、模型、用途与应用设置") }
        Section("导出选项") {
            ToggleRow("包含密钥", includeSecrets, { includeSecrets = it },
                if (includeSecrets) "文件将包含明文密钥和变量值，请保存到可信位置。" else "默认不包含密钥和变量值。")
        }
        InlineStatus(error, error = true)
        ActionButton("选择保存位置", { onExport(includeSecrets) }, Modifier.fillMaxWidth())
    }
}
