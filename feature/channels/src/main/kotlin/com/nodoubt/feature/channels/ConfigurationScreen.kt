package com.nodoubt.feature.channels

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.nodoubt.core.config.*
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.AppConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ConfigurationPage { HOME, REMOTE, VARIABLES }

@Composable
internal fun ConfigurationScreen(repository: ConfigRepository, config: AppConfig, onBack: () -> Unit, modifier: Modifier, active: Boolean) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    val profiles = remember(config.id, config.name, revision) { repository.profiles() }
    var page by remember { mutableStateOf(ConfigurationPage.HOME) }
    var status by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }
    var restoring by remember { mutableStateOf(false) }
    fun apply(label: String, action: () -> Unit): Boolean = runCatching(action)
        .onSuccess { failure = false; status = label; revision++ }
        .onFailure { failure = true; status = it.message ?: "操作失败" }.isSuccess
    fun launch(label: String, action: suspend () -> String?) {
        if (busy) return
        busy = true; failure = false; status = label
        scope.launch {
            try { status = action(); revision++ }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure = true; status = e.message ?: "操作失败，请重试" }
            finally { busy = false }
        }
    }
    fun importText(text: String) = launch("读取中…") {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "剪贴板为空" }
        preview = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) repository.fetchRemote(trimmed)
            else repository.importPreview(trimmed)
        null
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) launch("读取中…") {
            val json = withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openInputStream(uri)) { "无法打开文件" }.use { readConfiguration(it) }
            }
            preview = repository.importPreview(json)
            null
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val payload = exportText
        exportText = ""
        if (uri != null) launch("写入中…") {
            withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openOutputStream(uri)) { "无法创建文件" }
                    .use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            }
            "已导出"
        }
    }
    BackHandler(active && page != ConfigurationPage.HOME) { page = ConfigurationPage.HOME }
    when (page) {
        ConfigurationPage.HOME -> Page("配置文件", modifier, onBack = onBack) {
            InlineStatus(status, failure, busy)
            InlineStatus(repository.initializationError, error = true)
            ProfileSection(repository, profiles, busy, active, ::apply)
            Section("导入") {
                ActionListRow("从文件导入", {
                    openFile.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                }, UiSymbol.DOWNLOAD, enabled = !busy)
                ActionListRow("从剪贴板导入", { importText(clipboard.getText()?.text.orEmpty()) }, UiSymbol.EDIT, enabled = !busy)
                SettingsRow("配置订阅", if (config.source == null) "未订阅" else "已订阅", UiSymbol.LINK) { page = ConfigurationPage.REMOTE }
            }
            Section("导出") {
                ActionListRow("导出配置", { exporting = true }, UiSymbol.UPLOAD, enabled = !busy)
            }
            Section("") {
                SettingsRow("变量映射", "${config.variables.size} 项", UiSymbol.KEY) { page = ConfigurationPage.VARIABLES }
                ActionListRow("恢复上一个版本", { restoring = true }, UiSymbol.FOLDER, enabled = !busy)
            }
        }
        ConfigurationPage.REMOTE -> RemoteConfigurationScreen(config, modifier, status, failure, busy, active,
            onBack = { page = ConfigurationPage.HOME },
            onFetch = { url -> launch("获取中…") { preview = repository.fetchRemote(url); null } },
            onRefresh = {
                launch("检查中…") {
                    val update = repository.refreshRemote()
                    when (update.changes) {
                        listOf("远程配置没有变化") -> "已是最新"
                        listOf("与当前配置相同") -> { repository.applyImport(update, ImportMode.MERGE); "已是最新" }
                        else -> { preview = update; null }
                    }
                }
            },
            onUnsubscribe = { apply("已停止订阅") { repository.save(repository.state.value.copy(source = null)) } })
        ConfigurationPage.VARIABLES -> VariableMappingsScreen(config.variables, modifier, active, if (failure) status else null,
            onBack = { page = ConfigurationPage.HOME }, onChange = { values ->
                apply("变量已保存") { repository.save(repository.state.value.copy(variables = values)) }
            })
    }
    preview?.takeIf { active }?.let { value -> ImportPreviewDialog(value, { mode ->
        val label = when { value.isRefresh -> "已更新"; value.sourceUrl != null -> "已订阅"; else -> "已导入" }
        apply(label) { repository.applyImport(value, mode); preview = null }
    }, { preview = null }, error = if (failure) status else null) }
    if (exporting && active) ExportConfigurationSheet(config.name,
        onFile = { includeSecrets ->
            if (apply("选择保存位置") {
                exportText = repository.exportConfig(includeSecrets, false)
                saveFile.launch(exportFileName(config.name))
            }) exporting = false
        },
        onClipboard = {
            if (apply("已复制到剪贴板") { clipboard.setText(AnnotatedString(repository.exportConfig(false, false))) }) exporting = false
        },
        onDismiss = { exporting = false }, error = if (failure) status else null)
    if (restoring && active) ConfirmDialog("恢复上一个版本？", "回到最近一次保存前。", "恢复", {
        if (apply("已恢复") { repository.restorePrevious() }) restoring = false
    }, { restoring = false })
}

private fun exportFileName(name: String): String {
    val safe = name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-").trim('-').take(40).ifBlank { "配置" }
    val date = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT).format(java.util.Date())
    return "不惑-$safe-$date.json"
}
