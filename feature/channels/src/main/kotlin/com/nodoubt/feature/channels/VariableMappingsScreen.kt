package com.nodoubt.feature.channels

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nodoubt.core.designsystem.*

@Composable
internal fun VariableMappingsScreen(
    values: Map<String, String>, modifier: Modifier, active: Boolean, error: String?,
    onBack: () -> Unit, onChange: (Map<String, String>) -> Boolean
) {
    var editing by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<String?>(null) }
    fun open(key: String? = null) {
        original = key; name = key.orEmpty(); value = ""; validation = null; editing = true
    }
    Page("变量映射", modifier, onBack = onBack) {
        InlineStatus(error, error = true)
        Section("变量", "在地址、密钥或请求头中用 \${名称} 引用；值仅保存在本机") {
            values.keys.forEach { entry ->
                EditableRow(entry, "已配置", onClick = { open(entry) }, onDelete = { removing = entry })
            }
            ActionListRow("添加变量", { open() })
        }
    }
    if (editing && active) Sheet(if (original == null) "添加变量" else "编辑变量", { editing = false; value = "" },
        confirm = "完成", confirmEnabled = name.isNotBlank(), onConfirm = {
            val key = name.trim()
            val previous = original
            when {
                key.isBlank() -> validation = "请输入名称"
                key != previous && key in values -> validation = "已有同名变量"
                key.contains('\n') || value.contains('\n') -> validation = "名称和值不能换行"
                else -> {
                    val resolved = if (previous != null && value.isBlank()) values[previous].orEmpty() else value
                    if (onChange((values - (previous ?: "")) + (key to resolved))) { editing = false; value = "" }
                }
            }
        }) {
        Section("", "值仅保存在本机") {
            Field("名称", name, { name = it; validation = null }, hint = "OPENAI_API_KEY")
            Field("值", value, { value = it; validation = null }, secret = true,
                hint = if (original == null) "" else "留空不修改")
        }
        InlineStatus(validation ?: error, error = true)
    }
    removing?.takeIf { active }?.let { key ->
        ConfirmDialog("删除“$key”？", "引用它的配置将无法解析。", "删除", { removing = null; onChange(values - key) },
            { removing = null }, danger = true)
    }
}
