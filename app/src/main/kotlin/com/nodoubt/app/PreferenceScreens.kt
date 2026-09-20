package com.nodoubt.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nodoubt.core.config.ConfigRepository
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.*

@Composable
internal fun AppearanceScreen(repository: ConfigRepository, onBack: () -> Unit) {
    val config by repository.state.collectAsState()
    val prefs = config.preferences
    var error by remember { mutableStateOf<String?>(null) }
    fun save(update: (AppPreferences) -> AppPreferences) {
        runCatching { repository.save(repository.state.value.copy(preferences = update(repository.state.value.preferences))) }
            .onSuccess { error = null }.onFailure { error = it.message }
    }
    Page("外观与阅读", onBack = onBack) {
        InlineStatus(error, error = true)
        Section("外观") {
            ChoiceField("主题色", prefs.theme.name, listOf(ThemeFamily.APPLE.name to "海蓝",
                ThemeFamily.PICOVE.name to "暖橙", ThemeFamily.VERCEL.name to "石墨")) { value -> save { it.copy(theme = ThemeFamily.valueOf(value)) } }
            ChoiceField("颜色模式", prefs.appearance.name, listOf(Appearance.SYSTEM.name to "跟随系统",
                Appearance.LIGHT.name to "浅色", Appearance.DARK.name to "深色")) { value -> save { it.copy(appearance = Appearance.valueOf(value)) } }
        }
        Section("阅读") {
            ChoiceField("回答字号", prefs.answerFontSize.toString(), (13..24).map { it.toString() to "$it" }) {
                value -> save { it.copy(answerFontSize = value.toInt()) }
            }
        }
        Section("预览") {
            RichText("清晰的正文、**重点结论**与公式：\$x^2+y^2=z^2\$",
                modifier = Modifier.testTag("settings-rich-text"), fontSize = prefs.answerFontSize)
        }
    }
}

@Composable
internal fun CaptureSettingsScreen(repository: ConfigRepository, ready: Boolean, onStop: () -> Unit, onBack: () -> Unit) {
    val config by repository.state.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    fun save(update: (AppPreferences) -> AppPreferences) {
        runCatching { repository.save(repository.state.value.copy(preferences = update(repository.state.value.preferences))) }
            .onSuccess { error = null }.onFailure { error = it.message }
    }
    Page("悬浮球与截图", onBack = onBack) {
        InlineStatus(error, error = true)
        Section("悬浮球") {
            ChoiceField("大小", config.preferences.bubbleSizeDp.toString(), listOf("48" to "小", "56" to "标准", "64" to "大")) {
                value -> save { it.copy(bubbleSizeDp = value.toInt()) }
            }
        }
        Section("截图") {
            ToggleRow("保留截图", !config.preferences.autoDeleteScreenshot,
                { value -> save { it.copy(autoDeleteScreenshot = !value) } })
            Caption("默认只用于识题，处理后不保存。")
            ItemRow("截图授权", if (ready) "已就绪" else "开启悬浮球时授权") {
                if (ready) QuietButton("关闭", onStop)
            }
        }
    }
}
