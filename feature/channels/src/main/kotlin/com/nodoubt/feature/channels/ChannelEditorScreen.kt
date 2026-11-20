package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nodoubt.core.ai.AiClient
import com.nodoubt.core.ai.ConnectionResult
import com.nodoubt.core.ai.describeFailure
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
    var testResult by remember(channel.id) { mutableStateOf<ConnectionResult?>(null) }
    var testedModel by remember(channel.id) { mutableStateOf("") }
    val storedCredential = remember(channel.id, channel.credentialRef, editor.dirty) { runCatching { repository.getApiKey(channel.id).isNotBlank() } }
    val hasSavedCredential = storedCredential.getOrDefault(false)
    fun credential() = if (editor.keyChanged) editor.keyDraft else null
    fun run(label: String, action: suspend () -> String) {
        if (busy) return
        busy = true; failed = false; status = label
        scope.launch {
            try { status = action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed = true; status = describeFailure(e, editor.keyDraft.isBlank() && !hasSavedCredential) }
            finally { busy = false }
        }
    }
    fun test(model: ModelConfig) {
        if (busy) return
        busy = true; testResult = null; testedModel = model.name.ifBlank { model.id }; status = null
        scope.launch {
            try { testResult = aiClient.test(repository.resolveDraft(channel, model, credential())) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { testResult = ConnectionResult(ConnectionResult.Level.ERROR, "配置有误", e.message) }
            finally { busy = false }
        }
    }
    Page(if (editor.original == null) "添加服务" else channel.name, modifier,
        subtitle = if (editor.dirty) "未保存" else null, onBack = editor::back,
        actions = { QuietButton("保存", { if (editor.save(repository)) editor.page = ChannelPage.LIST },
            enabled = !busy && editor.dirty) }) {
        InlineStatus(editor.error, error = true)
        if (storedCredential.isFailure) InlineStatus("缺少 API Key", error = true)
        Section("连接") {
            Field("名称", channel.name, { editor.channel = channel.copy(name = it) })
            ChoiceField("协议", channel.protocol.id, Protocol.entries.map { it.id to it.label }) { id ->
                val protocol = Protocol.fromId(id)
                editor.channel = channel.copy(protocol = protocol,
                    baseUrl = if (channel.baseUrl == channel.protocol.defaultBaseUrl) protocol.defaultBaseUrl else channel.baseUrl)
            }
            Field("服务地址", channel.baseUrl, { editor.channel = channel.copy(baseUrl = it.trim()) }, hint = channel.protocol.defaultBaseUrl,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri)
            Field("API Key", editor.keyDraft, { editor.keyDraft = it; editor.keyChanged = it.isNotEmpty() }, secret = true,
                hint = when {
                    editor.keyChanged -> "保存后清除"
                    hasSavedCredential -> "已保存，输入以替换"
                    else -> "填写密钥"
                })
            ToggleRow("启用服务", channel.enabled, { editor.channel = channel.copy(enabled = it) })
        }
        Section("模型", if (channel.models.size > 1) "长按拖动排序" else null) {
            ReorderableColumn(channel.models, keyOf = { it.id }, onMove = editor::moveModel) { model, index ->
                EditableRow(model.name.ifBlank { model.id }, model.id.takeIf { model.name.isNotBlank() && it != model.name },
                    onClick = { onModel(index) }, onDelete = { editor.removeModelAt(index) }, handle = channel.models.size > 1)
            }
            ActionListRow("添加模型", { addOptions = true }, enabled = !busy)
        }
        Section("设置") {
            SettingsRow("请求参数", if (channel.parameters.isEmpty()) "默认" else "${channel.parameters.size} 项", onClick = onParameters)
            SettingsRow("兼容选项", if (channel.compat.size() == 0) "默认" else "已调整", onClick = onCompatibility)
            SettingsRow("高级设置", onClick = onAdvanced)
        }
        Section("", when {
            channel.models.isEmpty() -> "添加模型后可测试"
            editor.dirty -> "使用当前未保存的设置测试"
            else -> null
        }) {
            ActionListRow(if (busy && testResult == null && status == null) "测试中…" else "测试连接", {
                channel.models.singleOrNull()?.let(::test) ?: run { testing = true }
            }, UiSymbol.PLAY, enabled = !busy && channel.models.isNotEmpty())
            testResult?.let { ConnectionResultRow(testedModel, it) }
        }
        InlineStatus(status, failed, busy && testResult == null && status != null)
        if (hasSavedCredential || editor.original != null) Section("") {
            if (hasSavedCredential && !(editor.keyChanged && editor.keyDraft.isEmpty()))
                ActionListRow("清除密钥", { editor.keyDraft = ""; editor.keyChanged = true }, UiSymbol.KEY, danger = true)
            if (editor.original != null) ActionListRow("删除服务", { deleting = true }, UiSymbol.TRASH, danger = true)
        }
    }
    if (addOptions && active) Sheet("添加模型", { addOptions = false }) {
        Section("", "需先填写地址和 API Key") {
            ActionListRow("从服务获取", {
                addOptions = false
                run("获取中…") {
                    val models = aiClient.listModels(repository.resolveDraft(channel, null, credential()), channel.modelCatalogUrl)
                    catalog = models
                    "共 ${models.size} 个模型"
                }
            }, UiSymbol.DOWNLOAD)
            ActionListRow("手动添加", { addOptions = false; adding = true; modelId = ""; addError = null }, UiSymbol.EDIT)
        }
    }
    if (adding && active) Sheet("手动添加", { adding = false }, confirm = "添加", confirmEnabled = modelId.isNotBlank(), onConfirm = {
        val id = modelId.trim()
        when {
            id.isEmpty() -> addError = "请输入模型 ID"
            channel.models.any { it.id == id } -> addError = "该模型已在此服务中"
            else -> { editor.addModels(listOf(ModelConfig(id))); adding = false }
        }
    }) {
        Section("", "保存服务后生效") {
            Field("模型 ID", modelId, { modelId = it; addError = null }, hint = "例如 gpt-4o", error = addError)
        }
    }
    if (deleting && active) ConfirmDialog("删除“${channel.name}”？", "其中的模型将从解题模型中移除，无法撤销。", "删除", {
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
    }, { deleting = false }, danger = true)
    catalog?.takeIf { active }?.let { models -> ModelCatalogDialog(models, channel.models.map { it.id }.toSet(), { selected ->
        editor.addModels(selected)
        catalog = null
    }, { catalog = null }) }
    if (testing && active) ModelCatalogDialog(channel.models, emptySet(), { selected ->
        testing = false
        selected.firstOrNull()?.let(::test)
    }, { testing = false }, title = "选择测试模型", single = true)
}
