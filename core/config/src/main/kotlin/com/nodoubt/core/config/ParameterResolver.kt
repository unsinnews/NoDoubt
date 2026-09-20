package com.nodoubt.core.config

import com.google.gson.JsonObject
import com.nodoubt.core.model.*
import java.net.URI

object ParameterResolver {
    fun parameters(channel: Map<String, ParameterValue>, model: Map<String, ParameterValue>): JsonObject =
        ParameterPaths.resolve(channel, model)

    fun resolve(config: AppConfig, ref: ModelRef, value: (String) -> String = { it }): ModelEndpoint {
        val channel = config.providers.firstOrNull { it.id == ref.channelId }
            ?: error("渠道已不存在：${ref.channelId}")
        require(channel.enabled) { "渠道已停用：${channel.name}" }
        val model = channel.models.firstOrNull { it.id == ref.modelId }
            ?: error("模型已不存在：${ref.modelId}")
        val mergedHeaders = linkedMapOf<String, Pair<String, String>>()
        (channel.headers.entries + model.headers.entries).forEach { (key, headerValue) ->
            mergedHeaders[key.lowercase()] = key to value(headerValue)
        }
        return ModelEndpoint(
            protocol = model.protocol ?: channel.protocol,
            baseUrl = value(model.baseUrl ?: channel.baseUrl).trimEnd('/'),
            modelId = model.id,
            apiKey = value(channel.credentialRef),
            headers = mergedHeaders.values.toMap(),
            parameters = parameters(channel.parameters, model.parameters),
            compat = mergeJson(channel.compat, model.compat),
            maxOutputTokens = model.maxTokens,
            authHeader = channel.authHeader
        )
    }
}

object ConfigValidator {
    fun validate(config: AppConfig): AppConfig {
        require(config.schemaVersion == 1) { "不支持的配置版本：${config.schemaVersion}" }
        require(config.id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "配置集 ID 无效" }
        require(config.name.isNotBlank()) { "配置集名称不能为空" }
        require(config.providers.map { it.id }.distinct().size == config.providers.size) { "渠道 ID 重复" }
        config.providers.forEach { channel ->
            require(channel.id.isNotBlank() && channel.name.isNotBlank()) { "渠道 ID 和名称不能为空" }
            validateUrl(channel.baseUrl)
            require(channel.models.map { it.id }.distinct().size == channel.models.size) { "${channel.name} 中有重复模型 ID" }
            validateParameters(channel.parameters)
            channel.models.forEach { model ->
                require(model.id.isNotBlank()) { "模型 ID 不能为空" }
                model.baseUrl?.let(::validateUrl)
                require(model.contextWindow?.let { it > 0 } != false) { "上下文窗口必须大于 0" }
                require(model.maxTokens?.let { it > 0 } != false) { "最大输出必须大于 0" }
                validateParameters(model.parameters)
            }
        }
        return config
    }

    fun cleanBindings(config: AppConfig): AppConfig {
        val valid = config.providers.flatMap { c -> c.models.map { ModelRef(c.id, it.id) } }.toSet()
        return config.copy(bindings = Usage.entries.associateWith { usage ->
            val old = config.bindings[usage] ?: UsageBinding()
            val entries = old.entries.filter { it in valid }.distinct()
            UsageBinding(entries, old.selected?.takeIf { it in entries } ?: entries.firstOrNull())
        })
    }

    private fun validateParameters(values: Map<String, ParameterValue>) {
        values.forEach { (key, value) ->
            require(key.split('.').all { it.isNotBlank() }) { "参数名和路径不能为空" }
            require(value.mode != ParameterMode.VALUE || value.value != null) { "$key 已启用但未填写值" }
        }
    }

    private fun validateUrl(value: String) {
        if (ConfigValueTemplate.references(value).isNotEmpty() || value.startsWith("secret:")) return
        val uri = runCatching { URI(value) }.getOrNull()
        require(uri != null && uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()) {
            "服务地址必须是完整的 HTTP 或 HTTPS URL"
        }
        require(uri.userInfo == null) { "请将凭据填写在密钥或请求头中" }
    }
}
