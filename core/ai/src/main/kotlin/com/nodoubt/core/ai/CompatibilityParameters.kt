package com.nodoubt.core.ai

import com.nodoubt.core.ai.ParameterType.ARRAY
import com.nodoubt.core.ai.ParameterType.BOOLEAN
import com.nodoubt.core.ai.ParameterType.INTEGER
import com.nodoubt.core.ai.ParameterType.OBJECT
import com.nodoubt.core.ai.ParameterType.STRING

internal object CompatibilityParameters {
    private val cache = listOf(
        parameter("supportsLongCacheRetention", "支持长时缓存", BOOLEAN),
        parameter("sendSessionAffinityHeaders", "发送会话亲和头", BOOLEAN),
        parameter("sessionAffinityFormat", "会话头格式", STRING, "openai", "openai-nosession", "openrouter")
    )
    val completions = listOf(
        parameter("supportsStore", "接受 store 字段", BOOLEAN),
        parameter("supportsDeveloperRole", "使用 developer 角色", BOOLEAN),
        parameter("supportsReasoningEffort", "接受 reasoning_effort", BOOLEAN),
        parameter("supportsUsageInStreaming", "流式返回用量", BOOLEAN),
        parameter("supportsFinishReason", "流包含完成原因", BOOLEAN),
        parameter("maxTokensField", "输出上限字段", STRING, "max_tokens", "max_completion_tokens"),
        parameter("requiresReasoningContentOnAssistantMessages", "助手历史附带 reasoning_content", BOOLEAN),
        parameter("thinkingFormat", "思考参数格式", STRING, "openai", "openrouter", "deepseek", "together", "baseten", "zai", "qwen", "chat-template", "qwen-chat-template", "string-thinking", "ant-ling"),
        parameter("chatTemplateKwargs", "Chat Template Kwargs", OBJECT),
        parameter("chatTemplateArgs", "Chat Template Args", OBJECT),
        parameter("thinkingTokenBudgetField", "思考预算字段", STRING, "thinking_token_budget", "thinking_budget", "thinking_budget_tokens"),
        parameter("supportsThinkingTokenBudget", "兼容 vLLM 思考预算", BOOLEAN),
        parameter("cacheControlFormat", "缓存格式", STRING, "anthropic"),
        parameter("supportsStrictMode", "严格工具定义", BOOLEAN),
        parameter("zaiToolStream", "z.ai 工具流", BOOLEAN),
        parameter("vllmPriority", "vLLM 调度优先级", INTEGER),
        parameter("openRouterRouting", "OpenRouter 路由", OBJECT),
        parameter("vercelGatewayRouting", "Vercel Gateway 路由", OBJECT)
    ) + cache

    val responses = listOf(
        parameter("supportsDeveloperRole", "使用 developer 角色", BOOLEAN),
        parameter("supportsMaxOutputTokens", "接受输出 Token 上限", BOOLEAN),
        parameter("supportsExplicitPromptCacheMode", "接受显式缓存模式", BOOLEAN),
        parameter("supportsStrictMode", "严格工具定义", BOOLEAN)
    ) + cache

    val anthropic = listOf(
        parameter("supportsTemperature", "接受 temperature", BOOLEAN),
        parameter("supportsEagerToolInputStreaming", "接受工具输入流", BOOLEAN),
        parameter("supportsCacheControlOnTools", "接受工具缓存标记", BOOLEAN),
        parameter("forceAdaptiveThinking", "显式思考使用 adaptive 格式", BOOLEAN),
        parameter("supportsStrictTools", "严格工具定义", BOOLEAN),
        parameter("allowedFallbackModels", "服务端备用模型", ARRAY)
    ) + cache
}
