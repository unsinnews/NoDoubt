package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.fillMaxWidth
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
    fun open(key: String? = null) {
        original = key; name = key.orEmpty(); value = ""; validation = null; editing = true
    }
    Page("变量映射", modifier, onBack = onBack, actions = {
        ToolbarIcon(UiSymbol.ADD, "添加变量", { open() })
    }) {
        InlineStatus(error, error = true)
        Section("已保存的变量") {
            values.keys.forEach { entry ->
                ItemRow(entry, "已配置", onClick = { open(entry) }) {
                    ManagementMoreMenu("$entry 的更多操作", active, actions = listOf(
                        ManagementAction("编辑", { open(entry) }),
                        ManagementAction("移除", { onChange(values - entry) }, danger = true)
                    ))
                }
            }
            ActionListRow("添加变量", { open() })
        }
        Caption("填写配置中引用的变量。值仅保存在此设备。")
    }
    if (editing && active) ManagementSheet(if (original == null) "添加变量" else "编辑变量", { editing = false; value = "" }) {
        Section("变量") {
            Field("名称", name, { name = it; validation = null })
            Field("值", value, { value = it; validation = null }, secret = true,
                hint = if (original == null) "输入变量值" else "留空保留已保存值")
        }
        InlineStatus(validation ?: error, error = true)
        ActionButton("保存", {
            val key = name.trim()
            val previous = original
            when {
                key.isBlank() -> validation = "名称不能为空"
                key != previous && key in values -> validation = "名称已存在"
                key.contains('\n') || value.contains('\n') -> validation = "名称和值不能包含换行"
                else -> {
                    val resolved = if (previous != null && value.isBlank()) values[previous].orEmpty() else value
                    if (onChange((values - (previous ?: "")) + (key to resolved))) { editing = false; value = "" }
                }
            }
        }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
    }
}
