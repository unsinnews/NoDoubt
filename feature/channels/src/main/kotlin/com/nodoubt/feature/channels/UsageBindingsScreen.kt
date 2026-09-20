package com.nodoubt.feature.channels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nodoubt.core.config.ConfigRepository
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.*

@Composable
internal fun UsageBindingsScreen(
    repository: ConfigRepository, config: AppConfig, onBack: () -> Unit, modifier: Modifier, active: Boolean = true
) {
    var selectedUsage by remember { mutableStateOf<Usage?>(null) }
    var adding by remember { mutableStateOf<Usage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun update(usage: Usage, next: UsageBinding): Boolean = runCatching {
        repository.save(repository.state.value.copy(bindings = repository.state.value.bindings + (usage to next)))
    }.onSuccess { error = null }.onFailure { error = it.message ?: "保存失败，请重试" }.isSuccess
    BackHandler(active && selectedUsage != null) { selectedUsage = null }
    val usage = selectedUsage
    if (usage == null) Page("用途", modifier, onBack = onBack) {
        InlineStatus(error, error = true)
        Section("模型用途") {
            Usage.entries.forEach { entry ->
                val binding = config.bindings[entry] ?: UsageBinding()
                val default = binding.selected?.let { config.modelName(it) }
                ItemRow(entry.label, default?.let { "$it · ${binding.entries.size} 个候选" } ?: "尚未选择模型",
                    onClick = { selectedUsage = entry })
            }
        }
    } else {
        val binding = config.bindings[usage] ?: UsageBinding()
        Page(usage.label, modifier, onBack = { selectedUsage = null }, actions = {
            ToolbarIcon(UiSymbol.ADD, "添加候选模型", { adding = usage })
        }) {
            InlineStatus(error, error = true)
            Section("候选模型") {
                binding.entries.forEachIndexed { index, ref ->
                    val channel = config.providers.firstOrNull { it.id == ref.channelId }
                    ItemRow(config.modelName(ref), "${channel?.name ?: "渠道已移除"} · ${ref.modelId}",
                        onClick = { update(usage, binding.copy(selected = ref)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (binding.selected == ref) Symbol(UiSymbol.CHECK,
                                Modifier.semantics { contentDescription = "默认模型" }, LocalNoDoubtTokens.current.foregroundAccent)
                            ManagementMoreMenu("${config.modelName(ref)}的更多操作", active, actions = listOf(
                                ManagementAction("上移", { update(usage, binding.copy(entries = binding.entries.moved(index, index - 1))) }, enabled = index > 0),
                                ManagementAction("下移", { update(usage, binding.copy(entries = binding.entries.moved(index, index + 1))) }, enabled = index < binding.entries.lastIndex),
                                ManagementAction("移除", {
                                    val entries = binding.entries - ref
                                    update(usage, UsageBinding(entries, binding.selected?.takeIf { it in entries } ?: entries.firstOrNull()))
                                }, danger = true)
                            ))
                        }
                    }
                }
                ActionListRow("添加候选模型", { adding = usage })
            }
            Caption("轻点模型设为默认。")
        }
    }
    adding?.takeIf { active }?.let { target ->
        val binding = config.bindings[target] ?: UsageBinding()
        BindingPicker(config, binding.entries.toSet(), error, onApply = { selected ->
            val entries = (binding.entries + selected).distinct()
            if (update(target, UsageBinding(entries, binding.selected ?: entries.firstOrNull()))) adding = null
        }, onDismiss = { adding = null })
    }
}

private fun AppConfig.modelName(ref: ModelRef): String = providers.firstOrNull { it.id == ref.channelId }
    ?.models?.firstOrNull { it.id == ref.modelId }?.name ?: ref.modelId

private fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }
