package com.nodoubt.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nodoubt.core.config.ConfigRepository
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.AppPreferences

@Composable
internal fun SettingsScreen(repository: ConfigRepository, onOpen: (String) -> Unit) {
    val config by repository.state.collectAsState()
    var editing by remember { mutableStateOf(false) }
    Page("我的") {
        Surface(onClick = { editing = true }, shape = RoundedCornerShape(18.dp), color = LocalNoDoubtTokens.current.raised) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(54.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center) {
                    Text(when (config.preferences.avatar) { "brain" -> "思"; "rocket" -> "探"; "star" -> "星"; else -> "N" }, style = MaterialTheme.typography.headlineSmall,
                        color = LocalNoDoubtTokens.current.foregroundAccent)
                }
                Column(Modifier.weight(1f)) {
                    Text(config.preferences.nickname, style = MaterialTheme.typography.titleLarge)
                    Caption("个人资料")
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Section("模型") {
            ItemRow("模型服务", if (config.providers.isEmpty()) "添加渠道和模型" else "${config.providers.size} 个渠道",
                onClick = { onOpen("channels") })
        }
        Section("偏好设置") {
            ItemRow("外观与阅读", "主题、深浅色与回答字号", onClick = { onOpen("appearance") })
            ItemRow("悬浮球与截图", "大小、截图保存与授权", onClick = { onOpen("capture") })
        }
        repository.initializationError?.let { InlineStatus(it, error = true) }
        Caption("${stringResource(R.string.app_name)} 0.2.0")
    }
    if (editing) ProfileEditor(repository, onClose = { editing = false })
}

@Composable
private fun ProfileEditor(repository: ConfigRepository, onClose: () -> Unit) {
    var nickname by remember { mutableStateOf(repository.state.value.preferences.nickname) }
    var avatar by remember { mutableStateOf(repository.state.value.preferences.avatar) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onClose, title = { Text("个人资料") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("昵称", nickname, { nickname = it }); InlineStatus(error, error = true)
            ChoiceField("头像", avatar, listOf("spark" to "灵感", "brain" to "思考", "rocket" to "探索", "star" to "星标")) { avatar = it }
        } }, confirmButton = { QuietButton("保存", {
            runCatching {
                val config = repository.state.value
                repository.save(config.copy(preferences = config.preferences.copy(nickname = nickname.trim().ifBlank { AppPreferences().nickname }, avatar = avatar)))
            }.onSuccess { onClose() }.onFailure { error = it.message }
        }) }, dismissButton = { QuietButton("取消", onClose) })
}
