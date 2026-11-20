package com.nodoubt.core.ai

import com.nodoubt.core.ai.ParameterType.BOOLEAN
import com.nodoubt.core.ai.ParameterType.OBJECT
import com.nodoubt.core.ai.ParameterType.STRING

/** Compatibility switches, mirroring pi. Unset values use defaults detected from the service base URL. */
internal object CompatibilityParameters {
    private const val AUTO = "未设置时按服务地址自动判断"

    private fun flag(path: String, label: String, description: String = AUTO) =
        ParameterDefinition(path, label, BOOLEAN, description = description)

    private fun choice(path: String, label: String, description: String, vararg pairs: Pair<String, String>) =
        ParameterDefinition(path, label, STRING, pairs.map { it.first }, description, choiceLabels = pairs.toMap())

    private val affinityFormat = choice("sessionAffinityFormat", "会话请求头格式", AUTO,
        "openai" to "OpenAI", "openai-nosession" to "OpenAI（无 session）", "openrouter" to "OpenRouter")
    private val cacheKeyMode = choice("promptCacheKeyMode", "缓存键 prompt_cache_key", "默认自动",
        "auto" to "自动", "enabled" to "始终发送", "disabled" to "不发送")

    val completions = listOf(
        choice("thinkingFormat", "思考参数格式", AUTO,
            "openai" to "OpenAI", "openrouter" to "OpenRouter", "deepseek" to "DeepSeek", "zai" to "智谱 Z.ai",
            "qwen" to "通义千问", "qwen-chat-template" to "通义千问（Chat Template）", "together" to "Together",
            "baseten" to "Baseten", "chat-template" to "Chat Template", "string-thinking" to "字符串 thinking",
            "ant-ling" to "Ant Ling"),
        flag("supportsReasoningEffort", "发送 reasoning_effort"),
        choice("thinkingTokenBudgetField", "思考预算字段", "默认不发送思考预算",
            "thinking_token_budget" to "thinking_token_budget（vLLM）",
            "thinking_budget" to "thinking_budget（Qwen、SGLang）",
            "thinking_budget_tokens" to "thinking_budget_tokens（llama.cpp）"),
        ParameterDefinition("chatTemplateKwargs", "Chat Template 参数", OBJECT,
            description = "思考格式为 Chat Template 时发送"),
        ParameterDefinition("chatTemplateArgs", "Baseten 模板参数", OBJECT, description = "思考格式为 Baseten 时发送"),
        choice("maxTokensField", "输出上限字段", AUTO,
            "max_completion_tokens" to "max_completion_tokens", "max_tokens" to "max_tokens"),
        flag("supportsDeveloperRole", "使用 developer 角色", "默认使用 system 角色"),
        flag("supportsStore", "发送 store: false"),
        flag("supportsUsageInStreaming", "流式返回用量", "默认开启"),
        flag("supportsStrictMode", "严格工具定义", "默认关闭"),
        choice("cacheControlFormat", "缓存标记格式", "默认不加缓存标记", "anthropic" to "Anthropic cache_control"),
        flag("supportsLongCacheRetention", "支持长期缓存"),
        cacheKeyMode,
        flag("sendSessionAffinityHeaders", "发送会话亲和请求头", "默认仅 OpenRouter 发送"),
        affinityFormat
    )

    val responses = listOf(
        flag("supportsDeveloperRole", "使用 developer 角色", "默认开启"),
        flag("supportsMaxOutputTokens", "发送输出上限", "默认开启"),
        flag("supportsStrictMode", "严格工具定义", "默认关闭"),
        flag("supportsLongCacheRetention", "支持长期缓存", "默认开启"),
        flag("supportsExplicitPromptCacheMode", "显式缓存模式", "默认关闭"),
        cacheKeyMode,
        affinityFormat
    )

    val anthropic = listOf(
        flag("forceAdaptiveThinking", "思考使用 adaptive 格式", "默认关闭"),
        flag("supportsStrictTools", "严格工具定义", "默认关闭"),
        flag("supportsLongCacheRetention", "支持长期缓存", "默认开启"),
        flag("sendSessionAffinityHeaders", "发送会话亲和请求头", "默认仅 OpenRouter 发送"),
        choice("sessionAffinityFormat", "会话请求头格式", "默认 x-session-affinity", "openrouter" to "OpenRouter")
    )
}
