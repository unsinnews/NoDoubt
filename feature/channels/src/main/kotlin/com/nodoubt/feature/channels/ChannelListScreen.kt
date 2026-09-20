package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.*

@Composable
internal fun ChannelListScreen(
    config: AppConfig, initializationError: String?, modifier: Modifier,
    onOpen: (ChannelConfig?) -> Unit, onBindings: () -> Unit, onFiles: () -> Unit, onClose: () -> Unit
) {
    Page("模型服务", modifier, onBack = onClose, actions = { ToolbarIcon(UiSymbol.ADD, "添加服务", { onOpen(null) }) }) {
        InlineStatus(initializationError, error = true)
        Section("我的服务") {
            if (config.providers.isEmpty()) {
                Column(Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("添加你的第一个服务", style = MaterialTheme.typography.titleMedium)
                    Caption("连接模型后，即可用来识题和解答。")
                }
                ActionListRow("添加服务", { onOpen(null) })
            }
            config.providers.forEach { channel ->
                SettingsRow(channel.name, if (channel.enabled) "${channel.models.size} 个模型" else "已停用", UiSymbol.SERVER) { onOpen(channel) }
            }
        }
        Section("") {
            SettingsRow("解题模型", symbol = UiSymbol.SLIDERS, onClick = onBindings)
            SettingsRow("配置文件", config.name, UiSymbol.FOLDER, onFiles)
        }
    }
}
