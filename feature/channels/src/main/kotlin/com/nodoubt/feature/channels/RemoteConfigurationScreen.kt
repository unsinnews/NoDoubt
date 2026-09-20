package com.nodoubt.feature.channels

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.AppConfig

@Composable
internal fun RemoteConfigurationScreen(
    config: AppConfig, modifier: Modifier, status: String?, failure: Boolean, busy: Boolean,
    onBack: () -> Unit, onFetch: (String) -> Unit, onRefresh: () -> Unit
) {
    var url by remember(config.id, config.source?.url) { mutableStateOf(config.source?.url.orEmpty()) }
    Page("远程配置", modifier, onBack = onBack) {
        InlineStatus(status, failure, busy)
        Section("配置地址") {
            Field("URL", url, { url = it }, hint = "https://example.com/config.json", keyboardType = KeyboardType.Uri)
            ActionListRow("获取并预览", { onFetch(url.trim()) }, UiSymbol.DOWNLOAD,
                enabled = !busy && url.isNotBlank())
        }
        config.source?.let { source ->
            Section("当前来源") {
                ItemRow(source.url)
                ActionListRow("检查更新", onRefresh, UiSymbol.DOWNLOAD, enabled = !busy)
            }
        }
        Caption("获取后先预览，再决定是否导入。更新会保留本地修改。")
    }
}
