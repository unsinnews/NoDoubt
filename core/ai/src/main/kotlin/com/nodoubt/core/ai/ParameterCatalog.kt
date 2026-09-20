package com.nodoubt.core.ai

import com.nodoubt.core.model.Protocol

enum class ParameterType { STRING, NUMBER, INTEGER, BOOLEAN, OBJECT, ARRAY }

data class ParameterDefinition(
    val path: String,
    val label: String,
    val type: ParameterType,
    val choices: List<String> = emptyList(),
    val description: String = "未启用时不发送，使用服务端行为。",
    val required: Boolean = false,
    val suggestedValue: String? = null
)

/** Suggested values are hints only. A definition never enables or adds a parameter. */
object ParameterCatalog {
    fun parameters(protocol: Protocol): List<ParameterDefinition> = when (protocol) {
        Protocol.OPENAI_COMPLETIONS -> ProtocolParameters.completions
        Protocol.OPENAI_RESPONSES -> ProtocolParameters.responses
        Protocol.ANTHROPIC_MESSAGES -> ProtocolParameters.anthropic
        Protocol.GOOGLE_GENERATIVE_AI -> ProtocolParameters.google
    } + common

    fun compatibility(protocol: Protocol): List<ParameterDefinition> = when (protocol) {
        Protocol.OPENAI_COMPLETIONS -> CompatibilityParameters.completions
        Protocol.OPENAI_RESPONSES -> CompatibilityParameters.responses
        Protocol.ANTHROPIC_MESSAGES -> CompatibilityParameters.anthropic
        Protocol.GOOGLE_GENERATIVE_AI -> emptyList()
    }

    private val common = listOf(
        ParameterDefinition("timeoutMs", "请求超时（毫秒）", ParameterType.INTEGER, suggestedValue = "120000"),
        ParameterDefinition("maxRetries", "失败重试次数", ParameterType.INTEGER, description = "仅重试尚未产生内容的 408/429/5xx；默认 0 次。", suggestedValue = "2"),
        ParameterDefinition("maxRetryDelayMs", "重试等待上限（毫秒）", ParameterType.INTEGER, suggestedValue = "60000"),
        ParameterDefinition("extraBody", "自定义请求字段", ParameterType.OBJECT, description = "最后合并到协议请求体；嵌套对象逐键覆盖。模型与流式开关由应用管理。")
    )
}

internal fun parameter(path: String, label: String, type: ParameterType = ParameterType.STRING, vararg choices: String) =
    ParameterDefinition(path, label, type, choices.toList())
