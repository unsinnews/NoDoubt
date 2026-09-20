package com.nodoubt.feature.channels

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    val profiles = remember(config.id, config.name, revision) { repository.profiles() }
    var page by remember { mutableStateOf(ConfigurationPage.HOME) }
    var operation by remember { mutableStateOf<String?>(null) }
    var targetId by remember { mutableStateOf(config.id) }
    var targetName by remember { mutableStateOf("") }
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
    fun launch(label: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true; failure = false; status = label
        scope.launch {
            try { action(); status = "操作完成"; revision++ }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure = true; status = e.message ?: "操作失败，请重试" }
            finally { busy = false }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) launch("正在读取配置文件…") {
            val json = withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openInputStream(uri)) { "无法打开文件" }.use { readConfiguration(it) }
            }
            preview = repository.importPreview(json)
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val payload = exportText
        exportText = ""
        if (uri != null) launch("正在写入配置文件…") {
            withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openOutputStream(uri)) { "无法创建文件" }
                    .use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            }
        }
    }
    BackHandler(active && page != ConfigurationPage.HOME) { page = ConfigurationPage.HOME }
    when (page) {
        ConfigurationPage.HOME -> Page("配置文件", modifier, onBack = onBack, actions = {
            ToolbarIcon(UiSymbol.ADD, "新建配置集", { operation = "create"; targetName = "" }, enabled = !busy)
        }) {
            InlineStatus(status, failure, busy)
            InlineStatus(repository.initializationError, error = true)
            Section("本地配置集") {
                profiles.forEach { profile ->
                    ItemRow(profile.name, onClick = {
                        if (!profile.isActive && !busy) apply("已切换配置") { repository.switchProfile(profile.id) }
                    }) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (profile.isActive) Symbol(UiSymbol.CHECK,
                                Modifier.semantics { contentDescription = "当前配置集" }, LocalNoDoubtTokens.current.foregroundAccent)
                            ManagementMoreMenu("${profile.name}的更多操作", active, enabled = !busy, actions = listOf(
                                ManagementAction("复制", { targetId = profile.id; targetName = "${profile.name} 副本"; operation = "copy" }),
                                ManagementAction("重命名", { targetId = profile.id; targetName = profile.name; operation = "rename" }),
                                ManagementAction("删除", { targetId = profile.id; targetName = profile.name; operation = "delete" },
                                    enabled = profiles.size > 1, danger = true)
                            ))
                        }
                    }
                }
            }
            Section("导入与导出") {
                ActionListRow("从文件导入", {
                    openFile.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                }, UiSymbol.DOWNLOAD, enabled = !busy)
                ActionListRow("导出配置文件", { exporting = true }, UiSymbol.UPLOAD, enabled = !busy)
            }
            Section("更多设置") {
                SettingsRow("远程配置", if (config.source == null) "未连接" else "已连接", UiSymbol.LINK) { page = ConfigurationPage.REMOTE }
                SettingsRow("变量映射", "${config.variables.size} 项", UiSymbol.KEY) { page = ConfigurationPage.VARIABLES }
                ActionListRow("恢复上一个版本", { restoring = true }, UiSymbol.FOLDER, enabled = !busy)
            }
        }
        ConfigurationPage.REMOTE -> RemoteConfigurationScreen(config, modifier, status, failure, busy,
            onBack = { page = ConfigurationPage.HOME },
            onFetch = { url -> launch("正在获取远程配置…") { preview = repository.fetchRemote(url) } },
            onRefresh = { launch("正在检查远程更新…") { preview = repository.refreshRemote() } })
        ConfigurationPage.VARIABLES -> VariableMappingsScreen(config.variables, modifier, active, if (failure) status else null,
            onBack = { page = ConfigurationPage.HOME }, onChange = { values ->
                apply("变量已保存") { repository.save(repository.state.value.copy(variables = values)) }
            })
    }
    preview?.takeIf { active }?.let { value -> ImportPreviewDialog(value, { mode ->
        apply("配置导入成功") { repository.applyImport(value, mode); preview = null }
    }, { preview = null }, error = if (failure) status else null) }
    if (exporting && active) ExportConfigurationSheet(config.name, onExport = { includeSecrets ->
        if (apply("请选择保存位置") {
            exportText = repository.exportConfig(includeSecrets, false)
            saveFile.launch("不惑-${config.id.take(8)}.json")
        }) exporting = false
    }, onDismiss = { exporting = false }, error = if (failure) status else null)
    when (operation.takeIf { active }) {
        "create", "copy", "rename" -> ProfileNameDialog(
            when (operation) { "create" -> "新建配置集"; "copy" -> "复制配置集"; else -> "重命名配置集" }, targetName, { name ->
                apply("配置集已保存") {
                    when (operation) {
                        "create" -> repository.switchProfile(repository.createProfile(name).id)
                        "copy" -> repository.switchProfile(repository.copyProfile(targetId, name).id)
                        "rename" -> repository.renameProfile(targetId, name)
                    }
                    operation = null
                }
            }, { operation = null }, error = if (failure) status else null)
        "delete" -> ConfirmDialog("删除 $targetName？", "这个配置集将从本机删除。删除当前配置集后会切换到另一个配置集。", "删除", {
            apply("配置集已删除") {
                if (repository.state.value.id == targetId) repository.switchProfile(profiles.first { it.id != targetId }.id)
                repository.deleteProfile(targetId); operation = null
            }
        }, { operation = null })
    }
    if (restoring && active) ConfirmDialog("恢复上一个版本？", "当前配置将恢复为最近保存前的版本。", "恢复", {
        if (apply("已恢复配置") { repository.restorePrevious() }) restoring = false
    }, { restoring = false })
}
