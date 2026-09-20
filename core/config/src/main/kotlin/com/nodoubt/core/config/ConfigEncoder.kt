package com.nodoubt.core.config

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.nodoubt.core.model.*

internal object ConfigEncoder {
    private val samplingProtocols = setOf(Protocol.OPENAI_COMPLETIONS, Protocol.OPENAI_RESPONSES)
    private val clientParameters = setOf("timeoutMs", "maxRetries", "maxRetryDelayMs", "cacheRetention", "sessionId", "interleavedThinking", "extraBody")

    fun warnings(config: AppConfig, purePi: Boolean): List<String> {
        if (!purePi) return emptyList()
        val warnings = mutableListOf("纯 pi 文件不含用途绑定、界面设置、远程来源和本地变量映射；请在 pi 环境中配置导出的密钥变量。")
        config.providers.forEach { channel -> channel.models.forEach { model ->
            val parameters = ParameterResolver.parameters(channel.parameters, model.parameters)
            val protocol = model.protocol ?: channel.protocol
            val omitted = parameters.keySet().filter { protocol !in samplingProtocols || it in clientParameters }
            if (omitted.isNotEmpty()) warnings += "${channel.name}/${model.name}：纯 pi 无法表达参数 ${omitted.joinToString("、")}，将省略。"
        } }
        return warnings
    }

    fun encode(config: AppConfig, purePi: Boolean, resolve: ((String) -> String)?): String {
        val root = JsonObject()
        if (!purePi) root.addProperty("schemaVersion", config.schemaVersion)
        val providers = JsonObject()
        val channelExtensions = JsonObject()
        config.providers.forEach { channel ->
            providers.add(channel.id, JsonObject().apply {
                addProperty("api", channel.protocol.id)
                addProperty("name", channel.name)
                addProperty("baseUrl", resolve?.invoke(channel.baseUrl) ?: channel.baseUrl)
                val key = resolve?.invoke(channel.credentialRef) ?: exportReference(channel.credentialRef, purePi)
                addProperty("apiKey", key)
                channel.authHeader?.let { addProperty("authHeader", it) }
                if (channel.headers.isNotEmpty()) add("headers", exportHeaders(channel.headers, purePi, resolve))
                if (channel.compat.size() > 0) add("compat", channel.compat.deepCopy())
                add("models", JsonArray().apply { channel.models.forEach { model ->
                    val effective = if (purePi) model.copy(parameters = ParameterResolver.parameters(channel.parameters, model.parameters)
                        .entrySet().associate { (key, value) -> key to ParameterValue(ParameterMode.VALUE, value) }) else model
                    add(encodeModel(effective, model.protocol ?: channel.protocol, purePi, resolve))
                } })
            })
            channelExtensions.add(channel.id, JsonObject().apply {
                addProperty("name", channel.name)
                addProperty("enabled", channel.enabled)
                // pi's apiKey has the same information; this makes empty/local-only refs unambiguous.
                if (resolve == null) addProperty("credentialRef", channel.credentialRef)
                channel.modelCatalogUrl?.let { addProperty("modelCatalogUrl", it) }
                add("parameters", configGson.toJsonTree(channel.parameters))
                add("models", JsonObject().apply {
                    channel.models.forEach { model -> add(model.id, JsonObject().apply {
                        add("parameters", configGson.toJsonTree(model.parameters))
                    }) }
                })
            })
        }
        root.add("providers", providers)
        if (!purePi) root.add("nodoubt", JsonObject().apply {
            addProperty("id", config.id)
            addProperty("name", config.name)
            add("channels", channelExtensions)
            add("bindings", configGson.toJsonTree(config.bindings))
            add("preferences", configGson.toJsonTree(config.preferences))
            add("variables", jsonMap(config.variables.mapValues { (_, value) -> resolve?.invoke(value) ?: value }))
            config.source?.let { add("source", configGson.toJsonTree(it)) }
        })
        return configGson.toJson(root)
    }

    private fun encodeModel(model: ModelConfig, protocol: Protocol, purePi: Boolean, resolve: ((String) -> String)?): JsonObject = model.metadata.deepCopy().apply {
        addProperty("id", model.id)
        addProperty("name", model.name)
        model.protocol?.let { addProperty("api", it.id) }
        model.baseUrl?.let { addProperty("baseUrl", resolve?.invoke(it) ?: it) }
        model.contextWindow?.let { addProperty("contextWindow", it) }
        model.maxTokens?.let { addProperty("maxTokens", it) }
        if (model.headers.isNotEmpty()) add("headers", exportHeaders(model.headers, purePi, resolve))
        if (model.compat.size() > 0) add("compat", model.compat.deepCopy())
        if (purePi) {
            remove("samplingParams")
            val sampling = JsonObject()
            model.parameters.filter { (key, parameter) -> protocol in samplingProtocols &&
                key !in clientParameters && parameter.mode == ParameterMode.VALUE }.forEach { (key, parameter) ->
                parameter.value?.let { sampling.add(key, it.deepCopy()) }
            }
            if (sampling.size() > 0) add("samplingParams", sampling)
        }
    }

    private fun exportHeaders(values: Map<String, String>, purePi: Boolean, resolve: ((String) -> String)?): JsonObject =
        jsonMap(values.mapValues { (_, value) -> resolve?.invoke(value) ?: exportReference(value, purePi) })

    private fun exportReference(value: String, purePi: Boolean): String =
        if (purePi && value.startsWith("secret:")) "${'$'}NODOUBT_" + value.removePrefix("secret:")
            .uppercase().replace(Regex("[^A-Z0-9_]"), "_") else value
}
