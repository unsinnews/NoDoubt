package com.nodoubt.core.ai

import com.nodoubt.core.model.Protocol

enum class ParameterType { STRING, NUMBER, INTEGER, BOOLEAN, OBJECT, ARRAY }

data class ParameterDefinition(
    val path: String,
    val label: String,
    val type: ParameterType,
    val choices: List<String> = emptyList(),
    val description: String = "未设置时不发送",
    val required: Boolean = false,
    val suggestedValue: String? = null,
    val choiceLabels: Map<String, String> = emptyMap()
) {
    fun labelOf(value: String): String = choiceLabels[value] ?: when (type) {
        ParameterType.BOOLEAN -> if (value == "true") "开启" else if (value == "false") "关闭" else value
        else -> value
    }
}

/** Suggested values are hints only. A definition never enables or adds a parameter. */
object ParameterCatalog {
    fun parameters(protocol: Protocol): List<ParameterDefinition> = when (protocol) {
        Protocol.OPENAI_COMPLETIONS -> ProtocolParameters.completions
        Protocol.OPENAI_RESPONSES -> ProtocolParameters.responses
        Protocol.ANTHROPIC_MESSAGES -> ProtocolParameters.anthropic
        Protocol.GOOGLE_GENERATIVE_AI -> ProtocolParameters.google
    } + common(protocol)

    fun compatibility(protocol: Protocol): List<ParameterDefinition> = when (protocol) {
        Protocol.OPENAI_COMPLETIONS -> CompatibilityParameters.completions
        Protocol.OPENAI_RESPONSES -> CompatibilityParameters.responses
        Protocol.ANTHROPIC_MESSAGES -> CompatibilityParameters.anthropic
        Protocol.GOOGLE_GENERATIVE_AI -> emptyList()
    }

    private fun common(protocol: Protocol) = listOfNotNull(
        ParameterDefinition("timeoutMs", "请求超时（毫秒）", ParameterType.INTEGER, description = "默认空闲 120 秒超时", suggestedValue = "120000"),
        ParameterDefinition("maxRetries", "失败重试次数", ParameterType.INTEGER,
            description = "默认 2 次，仅重试尚未输出内容的 408/429/5xx", suggestedValue = "2"),
        if (protocol == Protocol.GOOGLE_GENERATIVE_AI) null else ParameterDefinition("cacheRetention", "提示缓存",
            ParameterType.STRING, listOf("none", "short", "long"), "默认短期缓存",
            choiceLabels = mapOf("none" to "不缓存", "short" to "短期", "long" to "长期")),
        if (protocol == Protocol.GOOGLE_GENERATIVE_AI) null else ParameterDefinition("sessionId", "缓存会话标识",
            ParameterType.STRING, description = "用于缓存键和会话亲和请求头"),
        ParameterDefinition("extraBody", "自定义请求字段", ParameterType.OBJECT,
            description = "最后合并到请求体，嵌套对象逐键覆盖；model 与 stream 由应用管理")
    )
}

internal fun parameter(path: String, label: String, type: ParameterType = ParameterType.STRING, vararg choices: String) =
    ParameterDefinition(path, label, type, choices.toList())
