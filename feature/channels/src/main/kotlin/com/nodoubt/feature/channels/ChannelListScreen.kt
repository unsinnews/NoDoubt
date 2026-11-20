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
    onOpen: (ChannelConfig?) -> Unit, onBindings: () -> Unit, onFiles: () -> Unit, onClose: () -> Unit,
    onMove: (Int, Int) -> Unit = { _, _ -> }
) {
    Page("模型服务", modifier, onBack = onClose, actions = { ToolbarIcon(UiSymbol.ADD, "添加服务", { onOpen(null) }) }) {
        InlineStatus(initializationError, error = true)
        Section("我的服务", if (config.providers.size > 1) "长按拖动排序" else null) {
            if (config.providers.isEmpty()) {
                Column(Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("还没有服务", style = MaterialTheme.typography.titleMedium)
                    Caption("添加后即可识题和解答")
                }
                ActionListRow("添加服务", { onOpen(null) })
            }
            ReorderableColumn(config.providers, keyOf = { it.id }, onMove = onMove) { channel, _ ->
                SettingsRow(channel.name, if (channel.enabled) "${channel.models.size} 个模型" else "已停用", UiSymbol.SERVER) { onOpen(channel) }
            }
        }
        Section("") {
            SettingsRow("解题模型", symbol = UiSymbol.SLIDERS, onClick = onBindings)
            SettingsRow("配置文件", config.name, UiSymbol.FOLDER, onFiles)
        }
    }
}
