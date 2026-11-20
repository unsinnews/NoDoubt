package com.nodoubt.core.config

import com.google.gson.JsonObject
import com.nodoubt.core.model.*

/** Reads pi models.json and the NoDoubt extension without relying on Gson's unsafe constructors. */
object ConfigCodec {
    fun decode(json: String): ImportPreview {
        val root = parseObject(json)
        require(root.has("providers") && root["providers"].isJsonObject) { "缺少 providers 对象" }
        val version = root.intOrNull("schemaVersion") ?: 1
        require(version == 1) { "不支持的配置版本：$version" }
        val extension = root.obj("nodoubt")
        val warnings = mutableListOf<String>()
        val credentials = linkedMapOf<String, String>()
        val channels = root.obj("providers").entrySet().map { (id, element) ->
            require(element.isJsonObject) { "渠道 $id 必须是对象" }
            channel(id, element.asJsonObject, extension.obj("channels").obj(id), warnings, credentials)
        }
        val raw = AppConfig(
            schemaVersion = version,
            id = extension.text("id", "default"),
            name = extension.text("name", "导入配置"),
            providers = channels,
            bindings = decodeBindings(extension.obj("bindings")),
            preferences = decodePreferences(extension.obj("preferences")),
            variables = extension.obj("variables").strings(),
            source = decodeSource(extension.obj("source"))
        )
        val clean = ConfigValidator.cleanBindings(ConfigValidator.validate(raw))
        if (raw.bindings != clean.bindings) warnings += "已移除无效的解题模型，请检查"
        return ImportPreview(clean, warnings.distinct(), credentials = credentials)
    }

    fun encode(config: AppConfig, purePi: Boolean = false, resolve: ((String) -> String)? = null): String =
        ConfigEncoder.encode(ConfigValidator.validate(config), purePi, resolve)

    private fun channel(
        id: String,
        source: JsonObject,
        extension: JsonObject,
        warnings: MutableList<String>,
        credentials: MutableMap<String, String>
    ): ChannelConfig {
        require(id.isNotBlank()) { "渠道 ID 不能为空" }
        val defaultApi = when (id.lowercase()) {
            "anthropic" -> "anthropic-messages"
            "google", "gemini" -> "google-generative-ai"
            else -> "openai-completions"
        }
        val api = source.text("api", defaultApi)
        if (!source.has("api")) warnings += "$id：未指定渠道协议，当前使用 ${Protocol.fromId(api).label}，请在模型服务中核对。"
        val protocol = Protocol.fromId(api)
        val ref = credential(extension.text("credentialRef").ifBlank { source.text("apiKey") }, id, warnings, credentials)
        if (source.has("oauth")) warnings += "$id：订阅登录不受支持，请配置 API 密钥。"
        val overrides = source.obj("modelOverrides")
        val listed = source.objects("models").map { model ->
            val modelId = model.text("id")
            model(id, mergeJson(model, overrides.obj(modelId)), extension.obj("models").obj(modelId), warnings)
        }
        val extra = overrides.entrySet().filter { (key, _) -> listed.none { it.id == key } }.map { (key, entry) ->
            require(entry.isJsonObject) { "$id 的 modelOverrides.$key 必须是对象" }
            warnings += "$id/$key：未附带模型目录，已使用覆盖字段创建可编辑模型。"
            val objectValue = entry.asJsonObject.deepCopy().apply { addProperty("id", key) }
            model(id, objectValue, extension.obj("models").obj(key), warnings)
        }
        val baseUrl = source.text("baseUrl", protocol.defaultBaseUrl)
        rejectCommand(baseUrl, "$id 的服务地址", warnings)
        ConfigValueTemplate.references(baseUrl).forEach { warnings += "$id 的服务地址：请配置变量 $$it。" }
        val parameters = ModelThinking.withoutChannelThinking(decodeParameters(extension.obj("parameters")))
        if (parameters.ignored.isNotEmpty()) warnings += "$id：渠道级思考配置已忽略（${parameters.ignored.joinToString("、")}）；请在具体模型中设置。"
        return ChannelConfig(
            id = id, name = extension.text("name", source.text("name", id)), protocol = protocol,
            baseUrl = baseUrl.takeUnless { it.startsWith("!") } ?: protocol.defaultBaseUrl,
            credentialRef = ref, enabled = extension.boolOrNull("enabled") ?: true,
            authHeader = source.boolOrNull("authHeader"), headers = headers(source.obj("headers"), id, warnings),
            models = listed + extra, parameters = parameters.values,
            compat = source.obj("compat").deepCopy(), modelCatalogUrl = extension.text("modelCatalogUrl").ifBlank { null }
        )
    }

    private fun model(provider: String, source: JsonObject, extension: JsonObject, warnings: MutableList<String>): ModelConfig {
        val id = source.text("id")
        require(id.isNotBlank()) { "$provider 中的模型缺少 ID" }
        val metadata = source.deepCopy()
        listOf("id", "name", "api", "baseUrl", "contextWindow", "maxTokens", "headers", "compat").forEach(metadata::remove)
        var baseUrl = source.text("baseUrl").ifBlank { null }
        if (baseUrl?.startsWith("!") == true) {
            warnings += "$provider/$id：拒绝执行服务地址命令，请手动配置。"
            baseUrl = null
        }
        val sampling = source.obj("samplingParams").entrySet().associate { (key, value) ->
            key to ParameterValue(ParameterMode.VALUE, value.deepCopy())
        }
        return ModelConfig(
            id, source.text("name", id), source.text("api").ifBlank { null }?.let(Protocol::fromId), baseUrl,
            source.intOrNull("contextWindow"), source.intOrNull("maxTokens"),
            headers(source.obj("headers"), "$provider/$id", warnings),
            sampling + decodeParameters(extension.obj("parameters")), source.obj("compat").deepCopy(), metadata
        )
    }

    private fun credential(raw: String, id: String, warnings: MutableList<String>, credentials: MutableMap<String, String>): String {
        val localRef = "secret:channel:$id"
        if (raw.isBlank()) return localRef
        if (raw.startsWith("!")) {
            warnings += "$id：拒绝执行 apiKey 命令；请在密钥设置中手动填写。"
            return localRef
        }
        if (raw.startsWith("secret:")) return raw
        val variables = ConfigValueTemplate.references(raw)
        if (variables.isNotEmpty()) {
            variables.forEach { name -> warnings += "$id：变量 $$name 需要在本地变量映射中填写；Android 不读取桌面环境变量。" }
            return raw
        }
        credentials[localRef] = raw
        return localRef
    }

    private fun headers(raw: JsonObject, path: String, warnings: MutableList<String>): Map<String, String> =
        raw.strings().mapValues { (key, value) ->
            if (value.startsWith("!")) {
                warnings += "$path 的请求头 $key：拒绝执行命令，请手动填写。"
                ""
            } else {
                ConfigValueTemplate.references(value).forEach { name -> warnings += "$path 的请求头 $key：请配置变量 $$name。" }
                value
            }
        }

    private fun rejectCommand(value: String, path: String, warnings: MutableList<String>) {
        if (value.startsWith("!")) warnings += "$path：拒绝执行命令，已恢复默认地址，请手动填写。"
    }

    private fun decodeParameters(raw: JsonObject): Map<String, ParameterValue> = raw.entrySet().associate { (key, entry) ->
        require(entry.isJsonObject) { "$key 参数必须声明 mode 和 value" }
        val obj = entry.asJsonObject
        val mode = runCatching { ParameterMode.valueOf(obj.text("mode", "INHERIT").uppercase()) }
            .getOrElse { error("$key 的参数模式无效") }
        key to ParameterValue(mode, obj["value"].copyOrNull())
    }

    private fun decodeBindings(raw: JsonObject): Map<Usage, UsageBinding> = Usage.entries.associateWith { usage ->
        val value = raw.obj(usage.name).takeIf { it.size() > 0 } ?: raw.obj(usage.name.lowercase())
        val entries = value.objects("entries").map { ModelRef(it.text("channelId"), it.text("modelId")) }
        val selected = value.obj("selected").takeIf { it.size() > 0 }?.let { ModelRef(it.text("channelId"), it.text("modelId")) }
        UsageBinding(entries, selected)
    }

    private fun decodePreferences(raw: JsonObject): AppPreferences = AppPreferences(
        theme = runCatching { ThemeFamily.valueOf(raw.text("theme", "PICOVE")) }.getOrDefault(ThemeFamily.PICOVE),
        appearance = runCatching { Appearance.valueOf(raw.text("appearance", "SYSTEM")) }.getOrDefault(Appearance.SYSTEM),
        autoDeleteScreenshot = raw.boolOrNull("autoDeleteScreenshot") ?: true,
        bubbleSizeDp = (raw.intOrNull("bubbleSizeDp") ?: 48).coerceIn(36, 80),
        nickname = raw.text("nickname", AppPreferences().nickname), avatar = raw.text("avatar", "spark"),
        answerFontSize = (raw.intOrNull("answerFontSize") ?: 16).coerceIn(12, 28)
    )

    private fun decodeSource(raw: JsonObject): RemoteSource? = raw.text("url").takeIf { it.isNotBlank() }?.let {
        RemoteSource(it, raw["updatedAt"]?.let { value -> runCatching { value.asLong }.getOrNull() } ?: 0,
            raw.text("etag").ifBlank { null }, raw.text("lastModified").ifBlank { null }, raw.text("baseline").ifBlank { null })
    }
}
