package com.nodoubt.core.config

import android.content.Context
import com.nodoubt.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

class ConfigRepository(context: Context) {
    private val app = context.applicationContext
    private val files by lazy { ProfileFiles(app) }
    private val vault by lazy { SecretVault(app) }
    private val remote = RemoteConfigs()
    private val mutableState = MutableStateFlow(AppConfig())
    val state: StateFlow<AppConfig> = mutableState.asStateFlow()
    var initializationError: String? = null
        private set

    init {
        runCatching { initialize() }.onFailure {
            initializationError = "配置读取失败：${it.message ?: "未知错误"}。原文件已保留，可恢复上版或创建新配置集。"
        }
    }

    @Synchronized fun save(config: AppConfig) {
        require(config.id == state.value.id) { "只能保存当前配置集，请先切换配置集" }
        persist(config)
        initializationError = null
    }

    @Synchronized fun setApiKey(channelId: String, value: String) {
        saveChannel(state.value, channelId, value)
    }

    fun getApiKey(channelId: String): String {
        val config = state.value
        val ref = config.providers.firstOrNull { it.id == channelId }?.credentialRef ?: return ""
        return resolveValue(ref, config)
    }

    fun resolve(ref: ModelRef): ModelEndpoint {
        val config = state.value
        return ParameterResolver.resolve(config, ref) { resolveValue(it, config) }
    }

    fun resolveDraft(channel: ChannelConfig, model: ModelConfig?, apiKeyOverride: String? = null): ModelEndpoint {
        require(apiKeyOverride?.startsWith("!") != true) { "不支持执行密钥命令" }
        val draftModel = model ?: ModelConfig("draft-catalog")
        val draftChannel = channel.copy(enabled = true, models = listOf(draftModel),
            credentialRef = if (apiKeyOverride != null) "" else channel.credentialRef)
        val draftConfig = state.value.copy(providers = listOf(draftChannel))
        val endpoint = ParameterResolver.resolve(draftConfig, ModelRef(channel.id, draftModel.id)) { resolveValue(it, draftConfig) }
        return endpoint.copy(modelId = model?.id.orEmpty(), apiKey = apiKeyOverride ?: endpoint.apiKey)
    }

    @Synchronized fun saveChannel(config: AppConfig, channelId: String, apiKeyOverride: String? = null) {
        require(config.id == state.value.id) { "只能保存当前配置集" }
        require(config.providers.any { it.id == channelId }) { "渠道已不存在" }
        require(apiKeyOverride?.startsWith("!") != true) { "不支持执行密钥命令" }
        val reference = "secret:channel:$channelId"
        val next = if (apiKeyOverride == null) config else config.copy(providers = config.providers.map {
            if (it.id == channelId) it.copy(credentialRef = reference) else it
        })
        persist(next, if (apiKeyOverride == null) emptyMap() else mapOf(reference to apiKeyOverride))
        initializationError = null
    }

    fun profiles(): List<ConfigProfile> = runCatching { files.profiles() }.getOrElse {
        initializationError = "无法读取配置集列表：${it.message ?: "存储不可用"}"
        listOf(ConfigProfile(state.value.id, state.value.name, isActive = true))
    }

    @Synchronized fun createProfile(name: String): ConfigProfile {
        val config = AppConfig(id = UUID.randomUUID().toString(), name = name.trim())
        ConfigValidator.validate(config)
        files.write(config)
        return ConfigProfile(config.id, config.name)
    }

    @Synchronized fun copyProfile(id: String, name: String): ConfigProfile {
        val copy = files.read(id).copy(id = UUID.randomUUID().toString(), name = name.trim())
        ConfigValidator.validate(copy)
        vault.copyProfile(id, copy.id)
        files.write(copy)
        return ConfigProfile(copy.id, copy.name)
    }

    @Synchronized fun switchProfile(id: String) {
        val config = files.read(id)
        files.setActive(id)
        mutableState.value = config
        initializationError = null
    }

    @Synchronized fun renameProfile(id: String, name: String) {
        val config = files.read(id).copy(name = name.trim())
        files.write(ConfigValidator.validate(config))
        if (state.value.id == id) mutableState.value = config
    }

    @Synchronized fun deleteProfile(id: String) {
        require(id != files.activeId) { "请先切换配置集再删除" }
        files.delete(id)
        vault.deleteProfile(id)
    }

    @Synchronized fun restorePrevious() {
        val restored = files.restore(files.activeId)
        mutableState.value = restored
        initializationError = null
    }

    fun importPreview(json: String): ImportPreview {
        val current = state.value
        val decoded = ConfigCodec.decode(json)
        val (safe, captured) = ConfigSecrets.protect(decoded.config)
        return ImportPreview(safe, decoded.warnings, ConfigMerge.changes(current, safe),
            credentials = decoded.credentials + captured, expectedConfig = current)
    }

    @Synchronized fun applyImport(preview: ImportPreview, mode: ImportMode) {
        val current = state.value
        require(preview.expectedConfig == null || preview.expectedConfig == current) { "配置已在预览后发生变化，请重新预览再应用" }
        val incoming = preview.config.copy(id = current.id, name = current.name)
        val next = if (preview.isRefresh || mode == ImportMode.REPLACE) incoming else ConfigMerge.merge(current, incoming)
        ConfigValidator.validate(next)
        val secrets = preview.credentials.filter { (ref, _) ->
            !preview.isRefresh || vault.get(current.id, ref).isBlank()
        }
        persist(next, secrets)
        initializationError = null
    }

    fun exportConfig(includeSecrets: Boolean = false, purePi: Boolean = false): String {
        val current = state.value
        return ConfigCodec.encode(current, purePi, if (includeSecrets) ({ value: String -> resolveValue(value, current) }) else null)
    }

    fun exportWarnings(purePi: Boolean): List<String> = ConfigEncoder.warnings(state.value, purePi)

    suspend fun fetchRemote(url: String): ImportPreview {
        val current = state.value
        val document = remote.fetch(url.trim())
        require(state.value == current) { "配置已在下载期间发生变化，请重新预览" }
        val preview = importPreview(document.json ?: error("远程配置没有返回内容"))
        val baseline = ConfigCodec.encode(preview.config.copy(source = null))
        val config = preview.config.copy(source = document.source.copy(baseline = baseline))
        return ImportPreview(config, preview.warnings, preview.changes, url.trim(), preview.credentials, baseline, expectedConfig = current)
    }

    suspend fun refreshRemote(): ImportPreview {
        val local = state.value
        val source = local.source ?: error("当前配置没有远程来源，请先导入远程配置")
        val document = remote.fetch(source.url, source)
        require(state.value == local) { "配置已在下载期间发生变化，请重新预览" }
        if (document.json == null) return ImportPreview(local, changes = listOf("远程配置没有变化"), sourceUrl = source.url,
            isRefresh = true, expectedConfig = local)
        val parsed = importPreview(document.json)
        val baseline = source.baseline?.let { ConfigCodec.decode(it).config }
        val combined = if (baseline == null) ConfigMerge.merge(local, parsed.config) else ConfigMerge.threeWay(baseline, local, parsed.config)
        val newBaseline = ConfigCodec.encode(parsed.config.copy(source = null))
        val next = combined.copy(source = document.source.copy(baseline = newBaseline))
        return ImportPreview(next, parsed.warnings + "更新已保留本地修改与本地密钥；冲突字段以本地值为准。",
            ConfigMerge.changes(local, next), source.url, parsed.credentials, newBaseline, isRefresh = true, expectedConfig = local)
    }

    private fun initialize() {
        val active = files.activeId
        // Recovery UI must retain the damaged profile's identity. Falling back to "default"
        // here could let a subsequent save overwrite an unrelated, healthy default profile.
        mutableState.value = AppConfig(id = active)
        if (files.exists(active)) {
            mutableState.value = files.read(active)
            return
        }
        require(!files.hasActiveSelection) { "当前配置集文件缺失，请恢复上版或切换配置集" }
        val preferences = app.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        val theme = app.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        if (preferences.all.isEmpty() && theme.all.isEmpty()) {
            val empty = AppConfig(id = active)
            files.write(empty)
            files.setActive(active)
            mutableState.value = empty
            return
        }
        // Preserve the exact original XML in encrypted storage before attempting to parse it.
        mapOf("ai_settings" to preferences, "theme_prefs" to theme).forEach { (name, old) ->
            val original = File(app.applicationInfo.dataDir, "shared_prefs/$name.xml")
            val backup = if (original.exists()) original.readText() else configGson.toJson(old.all)
            if (vault.get("migration", "original-$name").isEmpty()) vault.put("migration", "original-$name", backup)
        }
        val migrated = LegacyMigration.migrate(preferences.all, theme.all)
        persist(migrated.config.copy(id = active), migrated.credentials)
        files.setActive(active)
        // The encrypted original remains available; remove superseded plaintext credentials.
        check(preferences.edit().clear().commit()) { "配置已迁移，旧设置清理失败" }
    }

    private fun persist(config: AppConfig, credentials: Map<String, String> = emptyMap()) {
        val validated = ConfigValidator.cleanBindings(ConfigValidator.validate(config))
        val configured = validated.copy(providers = validated.providers.map {
            it.copy(parameters = ModelThinking.withoutChannelThinking(it.parameters).values)
        })
        val (safe, captured) = ConfigSecrets.protect(configured)
        // A corrupt current file blocks writes before any secret changes are made.
        if (files.exists(safe.id)) files.read(safe.id)
        vault.transaction(safe.id, credentials + captured) { files.write(safe) }
        mutableState.value = safe
    }

    private fun resolveValue(value: String, config: AppConfig): String =
        ConfigSecrets.resolve(value, config, read = { vault.get(config.id, it) })
}
