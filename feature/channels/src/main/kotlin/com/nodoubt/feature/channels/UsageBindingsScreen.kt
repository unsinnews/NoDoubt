package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nodoubt.core.config.ConfigRepository
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.*

/**
 * Each usage lists its candidates in priority order: the first is the default, the rest are fallbacks.
 * Long-press and drag to reorder; the trash icon removes. Every change saves immediately.
 */
@Composable
internal fun UsageBindingsScreen(
    repository: ConfigRepository, config: AppConfig, onBack: () -> Unit, modifier: Modifier, active: Boolean = true
) {
    var adding by remember { mutableStateOf<Usage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var removed by remember { mutableStateOf<Pair<Usage, ModelRef>?>(null) }
    fun update(usage: Usage, entries: List<ModelRef>): Boolean = runCatching {
        val current = repository.state.value
        repository.save(current.copy(bindings = current.bindings + (usage to orderedBinding(entries))))
    }.onSuccess { error = null }.onFailure { error = it.message ?: "保存失败" }.isSuccess
    Page("解题模型", modifier, onBack = onBack) {
        Caption("排在第一的为默认，失败时依次尝试。长按拖动排序。", Modifier.padding(horizontal = 16.dp))
        InlineStatus(error, error = true)
        removed?.let { (usage, ref) ->
            UndoBar("已移除 ${config.modelName(ref)}") {
                val entries = (config.bindings[usage] ?: UsageBinding()).priority()
                if (update(usage, (entries + ref).distinct())) removed = null
            }
        }
        Usage.entries.forEach { usage ->
            val entries = (config.bindings[usage] ?: UsageBinding()).priority()
            Section(usage.label) {
                ReorderableColumn(entries, keyOf = { "${it.channelId}/${it.modelId}" }, onMove = { from, to ->
                    update(usage, entries.toMutableList().apply { add(to, removeAt(from)) })
                }) { ref, index ->
                    val channel = config.providers.firstOrNull { it.id == ref.channelId }
                    EditableRow(config.modelName(ref), channel?.name ?: "服务已删除", handle = true,
                        badge = if (index == 0) "默认" else null, subtitleError = channel == null,
                        onDelete = { removed = usage to ref; update(usage, entries - ref) })
                }
                ActionListRow("添加模型", { adding = usage })
            }
        }
    }
    adding?.takeIf { active }?.let { target ->
        val entries = (config.bindings[target] ?: UsageBinding()).priority()
        BindingPicker(config, entries.toSet(), error, title = "添加到${target.label}", onApply = { selected ->
            if (update(target, (entries + selected).distinct())) adding = null
        }, onDismiss = { adding = null })
    }
}

internal fun UsageBinding.priority(): List<ModelRef> = (listOfNotNull(selected) + entries).distinct()

internal fun orderedBinding(entries: List<ModelRef>): UsageBinding = entries.distinct().let { UsageBinding(it, it.firstOrNull()) }

private fun AppConfig.modelName(ref: ModelRef): String = providers.firstOrNull { it.id == ref.channelId }
    ?.models?.firstOrNull { it.id == ref.modelId }?.name?.takeIf { it.isNotBlank() } ?: ref.modelId
