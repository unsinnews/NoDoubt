package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nodoubt.core.ai.AiClient
import com.nodoubt.core.config.ConfigRepository
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ChannelEditorScreen(
    channel: ChannelConfig, editor: ChannelEditorState, repository: ConfigRepository, aiClient: AiClient,
    modifier: Modifier, active: Boolean, onModel: (Int) -> Unit, onParameters: () -> Unit, onCompatibility: () -> Unit,
    onAdvanced: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var status by remember(channel.id) { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var addOptions by remember { mutableStateOf(false) }
    var modelId by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var catalog by remember { mutableStateOf<List<ModelConfig>?>(null) }
    var testing by remember { mutableStateOf(false) }
    val storedCredential = remember(channel.id, channel.credentialRef, editor.dirty) { runCatching { repository.getApiKey(channel.id).isNotBlank() } }
    val hasSavedCredential = storedCredential.getOrDefault(false)
    fun credential() = if (editor.keyChanged) editor.keyDraft else null
    fun run(label: String, action: suspend () -> String) {
        if (busy) return
        busy = true; failed = false; status = label
        scope.launch {
            try { status = action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed = true; status = e.message ?: "请求失败，请检查配置" }
            finally { busy = false }
        }
    }
    Page(if (editor.original == null) "添加服务" else channel.name, modifier,
        subtitle = if (editor.dirty) "未保存" else null, onBack = editor::back,
        actions = { QuietButton("保存", { if (editor.save(repository)) status = "配置已保存" }, enabled = !busy) }) {
        InlineStatus(editor.error, error = true)
        if (storedCredential.isFailure) InlineStatus("请补充 API Key，或在配置文件中填写凭据变量。", error = true)
        Section("连接") {
            Field("名称", channel.name, { editor.channel = channel.copy(name = it) })
            ChoiceField("协议", channel.protocol.id, Protocol.entries.map { it.id to it.label }) { id ->
                val protocol = Protocol.fromId(id)
                editor.channel = channel.copy(protocol = protocol,
                    baseUrl = if (channel.baseUrl == channel.protocol.defaultBaseUrl) protocol.defaultBaseUrl else channel.baseUrl)
            }
            Field("服务地址", channel.baseUrl, { editor.channel = channel.copy(baseUrl = it.trim()) }, hint = channel.protocol.defaultBaseUrl,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri)
            Field("API Key", editor.keyDraft, { editor.keyDraft = it; editor.keyChanged = true }, secret = true,
                hint = if (hasSavedCredential) "已保存，输入可替换" else "填写密钥")
            ToggleRow("启用服务", channel.enabled, { editor.channel = channel.copy(enabled = it) })
        }
        Section("模型") {
            channel.models.forEachIndexed { index, model ->
                ItemRow(model.name.ifBlank { model.id }, model.id.takeIf { it != model.name }, onClick = { onModel(index) })
            }
            ActionListRow("添加模型", { addOptions = true }, enabled = !busy)
        }
        Section("设置") {
            SettingsRow("请求参数", if (channel.parameters.isEmpty()) "默认" else "${channel.parameters.size} 项", onClick = onParameters)
            SettingsRow("兼容选项", if (channel.compat.size() == 0) "默认" else "已调整", onClick = onCompatibility)
            SettingsRow("高级设置", onClick = onAdvanced)
            ActionListRow("测试连接", { testing = true }, UiSymbol.PLAY, enabled = !busy && channel.models.isNotEmpty())
        }
        InlineStatus(status, failed, busy)
        if (hasSavedCredential || editor.keyChanged || editor.original != null) Section("") {
            if (hasSavedCredential || editor.keyChanged) ActionListRow("清除密钥", { editor.keyDraft = ""; editor.keyChanged = true }, UiSymbol.KEY, danger = true)
            if (editor.original != null) ActionListRow("删除服务", { deleting = true }, UiSymbol.TRASH, danger = true)
        }
    }
    if (addOptions && active) Sheet("添加模型", { addOptions = false }) {
        Section("") {
            ActionListRow("从服务获取", {
                addOptions = false
                run("正在获取模型…") {
                    val models = aiClient.listModels(repository.resolveDraft(channel, null, credential()), channel.modelCatalogUrl)
                    catalog = models
                    "找到 ${models.size} 个模型"
                }
            }, UiSymbol.DOWNLOAD)
            ActionListRow("手动添加", { addOptions = false; adding = true; modelId = ""; addError = null }, UiSymbol.EDIT)
        }
    }
    if (adding && active) AlertDialog(onDismissRequest = { adding = false }, shape = MaterialTheme.shapes.large,
        title = { Text("添加模型") }, text = {
            Field("模型 ID", modelId, { modelId = it; addError = null }, error = addError)
        }, confirmButton = { QuietButton("添加", {
            val id = modelId.trim()
            when {
                id.isEmpty() -> addError = "请输入模型 ID"
                channel.models.any { it.id == id } -> addError = "此模型已在渠道中"
                else -> { editor.addModels(listOf(ModelConfig(id))); adding = false }
            }
        }) }, dismissButton = { QuietButton("取消", { adding = false }) })
    if (deleting && active) ConfirmDialog("删除 ${channel.name}？", "此服务中的模型也会从解题模型中移除。", "删除", {
        runCatching {
            val config = repository.state.value
            repository.save(config.copy(providers = config.providers.filterNot { it.id == channel.id },
                bindings = config.bindings.mapValues { (_, binding) ->
                    val entries = binding.entries.filterNot { it.channelId == channel.id }
                    UsageBinding(entries, binding.selected?.takeIf { it in entries } ?: entries.firstOrNull())
                }))
            editor.page = ChannelPage.LIST
        }.onFailure { editor.error = it.message }
        deleting = false
    }, { deleting = false })
    catalog?.takeIf { active }?.let { models -> ModelCatalogDialog(models, channel.models.map { it.id }.toSet(), { selected ->
        editor.addModels(selected)
        catalog = null
    }, { catalog = null }) }
    if (testing && active) ModelCatalogDialog(channel.models, emptySet(), { selected ->
        testing = false
        selected.firstOrNull()?.let { model -> run("正在测试 ${model.id}…") {
            aiClient.test(repository.resolveDraft(channel, model, credential()))
        } }
    }, { testing = false }, title = "选择一个模型测试", single = true)
}
