package com.nodoubt.feature.channels

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.AppConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Subscription: one URL whose configuration is previewed before it is applied. Updates merge
 * three ways, so local edits and local keys survive; an unchanged remote is reported as 已是最新.
 */
@Composable
internal fun RemoteConfigurationScreen(
    config: AppConfig, modifier: Modifier, status: String?, failure: Boolean, busy: Boolean, active: Boolean,
    onBack: () -> Unit, onFetch: (String) -> Unit, onRefresh: () -> Unit, onUnsubscribe: () -> Unit
) {
    var url by remember(config.id, config.source?.url) { mutableStateOf(config.source?.url.orEmpty()) }
    var unsubscribing by remember { mutableStateOf(false) }
    val source = config.source
    val valid = url.trim().let { it.startsWith("https://") || it.startsWith("http://") }
    Page("配置订阅", modifier, onBack = onBack) {
        InlineStatus(status, failure, busy)
        if (source == null) {
            Section("订阅地址", "获取后先预览，确认才会应用") {
                Field("URL", url, { url = it }, hint = "https://…/config.json", keyboardType = KeyboardType.Uri)
                ActionListRow("获取并预览", { onFetch(url.trim()) }, UiSymbol.DOWNLOAD, enabled = !busy && valid)
            }
        } else {
            Section("当前订阅", "更新时保留本地修改与密钥，冲突以本地为准") {
                ItemRow(source.url, "上次更新：${formatTime(source.updatedAt)}")
                ActionListRow(if (busy) "检查中…" else "检查更新", onRefresh, UiSymbol.DOWNLOAD, enabled = !busy)
            }
            Section("更换地址") {
                Field("URL", url, { url = it }, hint = "https://…/config.json", keyboardType = KeyboardType.Uri)
                ActionListRow("获取并预览", { onFetch(url.trim()) }, UiSymbol.DOWNLOAD,
                    enabled = !busy && valid && url.trim() != source.url)
            }
            Section("", "仅停止自动关联，已导入的服务保留") {
                ActionListRow("停止订阅", { unsubscribing = true }, UiSymbol.TRASH, danger = true, enabled = !busy)
            }
        }
    }
    if (unsubscribing && active) ConfirmDialog("停止订阅？", "已导入的服务会保留。", "停止", {
        unsubscribing = false; onUnsubscribe()
    }, { unsubscribing = false }, danger = true)
}

private fun formatTime(millis: Long): String =
    if (millis <= 0) "未知" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(millis))
