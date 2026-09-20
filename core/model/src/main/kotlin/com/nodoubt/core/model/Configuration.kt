package com.nodoubt.core.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class ParameterValue(val mode: ParameterMode = ParameterMode.INHERIT, val value: JsonElement? = null)

data class ModelConfig(
    val id: String,
    val name: String = id,
    val protocol: Protocol? = null,
    val baseUrl: String? = null,
    val contextWindow: Int? = null,
    val maxTokens: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val parameters: Map<String, ParameterValue> = emptyMap(),
    val compat: JsonObject = JsonObject(),
    val metadata: JsonObject = JsonObject()
)

data class ChannelConfig(
    val id: String,
    val name: String,
    val protocol: Protocol = Protocol.OPENAI_COMPLETIONS,
    val baseUrl: String = protocol.defaultBaseUrl,
    val credentialRef: String = "channel:$id",
    val enabled: Boolean = true,
    val authHeader: Boolean? = null,
    val headers: Map<String, String> = emptyMap(),
    val models: List<ModelConfig> = emptyList(),
    val parameters: Map<String, ParameterValue> = emptyMap(),
    val compat: JsonObject = JsonObject(),
    val modelCatalogUrl: String? = null
)

data class ModelRef(val channelId: String, val modelId: String)
data class UsageBinding(val entries: List<ModelRef> = emptyList(), val selected: ModelRef? = null)
data class AppPreferences(
    val theme: ThemeFamily = ThemeFamily.APPLE,
    val appearance: Appearance = Appearance.SYSTEM,
    val autoDeleteScreenshot: Boolean = true,
    val bubbleSizeDp: Int = 48,
    val nickname: String = "不惑",
    val avatar: String = "spark",
    val answerFontSize: Int = 16
)

data class RemoteSource(
    val url: String,
    val updatedAt: Long = 0,
    val etag: String? = null,
    val lastModified: String? = null,
    val baseline: String? = null
)

data class AppConfig(
    val schemaVersion: Int = 1,
    val id: String = "default",
    val name: String = "默认配置",
    val providers: List<ChannelConfig> = emptyList(),
    val bindings: Map<Usage, UsageBinding> = Usage.entries.associateWith { UsageBinding() },
    val preferences: AppPreferences = AppPreferences(),
    val variables: Map<String, String> = emptyMap(),
    val source: RemoteSource? = null
)

data class ModelEndpoint(
    val protocol: Protocol,
    val baseUrl: String,
    val modelId: String,
    val apiKey: String = "",
    val headers: Map<String, String> = emptyMap(),
    val parameters: JsonObject = JsonObject(),
    val compat: JsonObject = JsonObject(),
    val maxOutputTokens: Int? = null,
    val authHeader: Boolean? = null
)
