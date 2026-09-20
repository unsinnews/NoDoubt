package com.nodoubt.feature.channels

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.ChannelConfig

@Composable
internal fun ChannelAdvancedScreen(channel: ChannelConfig, onChange: (ChannelConfig) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    Page("高级设置", modifier, onBack = onBack) {
        Section("连接选项") {
            ChoiceField("鉴权头", channel.authHeader?.toString() ?: "default",
                listOf("default" to "自动", "true" to "开启", "false" to "关闭")) {
                onChange(channel.copy(authHeader = it.toBooleanStrictOrNull()))
            }
            Field("模型列表地址", channel.modelCatalogUrl.orEmpty(),
                { onChange(channel.copy(modelCatalogUrl = it.trim().ifBlank { null })) },
                hint = "使用默认地址", keyboardType = KeyboardType.Uri)
        }
        KeyValueEditor("请求头", channel.headers) { onChange(channel.copy(headers = it)) }
    }
}
