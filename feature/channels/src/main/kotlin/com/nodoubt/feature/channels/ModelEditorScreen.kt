package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.*

@Composable
internal fun ModelEditorScreen(
    model: ModelConfig, channel: ChannelConfig, onChange: (ModelConfig) -> Unit, onBack: () -> Unit,
    modifier: Modifier, onParameters: () -> Unit, onCompatibility: () -> Unit,
    onAdvanced: () -> Unit, onRemove: () -> Unit, active: Boolean = true
) {
    var removing by remember { mutableStateOf(false) }
    Page("模型详情", modifier, onBack = onBack) {
        Section("名称") {
            Field("模型 ID", model.id, { onChange(model.copy(id = it.trim())) })
            Field("显示名称", model.name, { onChange(model.copy(name = it)) })
        }
        ThinkingSettings(model.protocol ?: channel.protocol, model.parameters,
            { onChange(model.copy(parameters = it)) }, active)
        Section("设置") {
            SettingsRow("请求参数", if (model.parameters.isEmpty()) "跟随服务" else "已调整", onClick = onParameters)
            SettingsRow("兼容选项", if (model.compat.size() == 0) "跟随服务" else "已调整", onClick = onCompatibility)
            SettingsRow("高级设置", onClick = onAdvanced)
        }
        Section("") { ActionListRow("移除模型", { removing = true }, UiSymbol.TRASH, danger = true) }
    }
    if (removing && active) ConfirmDialog("移除这个模型？", "保存服务后，这个模型会从解题模型中移除。", "移除",
        { removing = false; onRemove() }, { removing = false })
}

@Composable
internal fun ModelAdvancedScreen(
    model: ModelConfig, channel: ChannelConfig, onChange: (ModelConfig) -> Unit, onBack: () -> Unit, modifier: Modifier
) {
    Page("模型高级设置", modifier, onBack = onBack) {
        Section("连接", "留空时使用服务的连接设置。") {
            ChoiceField("协议", model.protocol?.id ?: "inherit",
                listOf("inherit" to "跟随服务") + Protocol.entries.map { it.id to it.label }) {
                onChange(model.copy(protocol = if (it == "inherit") null else Protocol.fromId(it)))
            }
            Field("服务地址", model.baseUrl.orEmpty(), { onChange(model.copy(baseUrl = it.trim().ifBlank { null })) },
                hint = channel.baseUrl, keyboardType = KeyboardType.Uri)
        }
        KeyValueEditor("请求头", model.headers) { onChange(model.copy(headers = it)) }
        Section("模型容量", "每次回答的输出限制，可在请求参数中单独设置。") {
            OptionalPositiveInteger("上下文窗口", model.contextWindow) { onChange(model.copy(contextWindow = it)) }
            OptionalPositiveInteger("最大输出", model.maxTokens) { onChange(model.copy(maxTokens = it)) }
        }
        JsonObjectEditor("其他信息", model.metadata, { onChange(model.copy(metadata = it)) })
    }
}

@Composable
private fun OptionalPositiveInteger(label: String, value: Int?, onChange: (Int?) -> Unit) {
    var raw by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    val error = raw.takeIf { it.isNotBlank() && (it.toIntOrNull() ?: 0) <= 0 }?.let { "请输入正整数，或留空" }
    Field(label, raw, {
        raw = it
        if (it.isBlank()) onChange(null)
        else it.toIntOrNull()?.takeIf { number -> number > 0 }?.let(onChange)
    }, hint = "未设置", keyboardType = KeyboardType.Number, error = error)
}
