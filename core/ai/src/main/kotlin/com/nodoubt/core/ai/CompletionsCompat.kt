package com.nodoubt.core.ai

import com.google.gson.JsonObject
import com.nodoubt.core.model.ModelEndpoint

/**
 * Effective Chat Completions compatibility, following pi: explicit compat values win, otherwise
 * defaults are detected from the service base URL. Model IDs are never inspected.
 */
internal data class CompletionsCompat(
    val supportsStore: Boolean,
    val supportsDeveloperRole: Boolean?,
    val supportsReasoningEffort: Boolean,
    val supportsUsageInStreaming: Boolean,
    val maxTokensField: String,
    val thinkingFormat: String,
    val thinkingBudgetField: String?,
    val cacheControlFormat: String?,
    val sendSessionAffinityHeaders: Boolean,
    val sessionAffinityFormat: String,
    val supportsLongCacheRetention: Boolean,
    val promptCacheKeyMode: String
) {
    companion object {
        fun of(endpoint: ModelEndpoint): CompletionsCompat {
            val url = endpoint.baseUrl.lowercase()
            val compat = endpoint.compat
            val zai = "api.z.ai" in url || "open.bigmodel.cn" in url
            val together = "api.together.ai" in url || "api.together.xyz" in url
            val moonshot = "api.moonshot." in url
            val openRouter = "openrouter.ai" in url
            val workersAi = "api.cloudflare.com" in url
            val aiGateway = "gateway.ai.cloudflare.com" in url
            val nvidia = "integrate.api.nvidia.com" in url
            val antLing = "api.ant-ling.com" in url
            val deepSeek = "deepseek.com" in url
            val grok = "api.x.ai" in url
            val chutes = "chutes.ai" in url
            val nonStandard = nvidia || "cerebras.ai" in url || grok || together || chutes || deepSeek || zai ||
                moonshot || "opencode.ai" in url || workersAi || aiGateway || antLing
            val useMaxTokens = chutes || deepSeek || moonshot || aiGateway || together || nvidia || antLing || zai
            val format = when {
                deepSeek -> "deepseek"
                zai -> "zai"
                together -> "together"
                antLing -> "ant-ling"
                openRouter -> "openrouter"
                else -> "openai"
            }
            return CompletionsCompat(
                supportsStore = compat.flag("supportsStore") ?: !nonStandard,
                supportsDeveloperRole = compat.flag("supportsDeveloperRole"),
                supportsReasoningEffort = compat.flag("supportsReasoningEffort")
                    ?: !(grok || zai || moonshot || together || aiGateway || nvidia || antLing),
                supportsUsageInStreaming = compat.flag("supportsUsageInStreaming") ?: true,
                maxTokensField = compat.text("maxTokensField")?.takeIf { it in setOf("max_tokens", "max_completion_tokens") }
                    ?: if (useMaxTokens) "max_tokens" else "max_completion_tokens",
                thinkingFormat = compat.text("thinkingFormat") ?: format,
                thinkingBudgetField = compat.text("thinkingTokenBudgetField")
                    ?: "thinking_token_budget".takeIf { compat.flag("supportsThinkingTokenBudget") == true },
                cacheControlFormat = compat.text("cacheControlFormat"),
                sendSessionAffinityHeaders = compat.flag("sendSessionAffinityHeaders") ?: openRouter,
                sessionAffinityFormat = compat.text("sessionAffinityFormat") ?: if (openRouter) "openrouter" else "openai",
                supportsLongCacheRetention = compat.flag("supportsLongCacheRetention")
                    ?: !(together || workersAi || aiGateway || nvidia || antLing),
                promptCacheKeyMode = compat.promptCacheKeyMode()
            )
        }
    }
}

internal fun JsonObject.promptCacheKeyMode(): String =
    text("promptCacheKeyMode")?.takeIf { it in setOf("auto", "enabled", "disabled") } ?: "auto"

internal fun isOpenRouter(endpoint: ModelEndpoint) = "openrouter.ai" in endpoint.baseUrl.lowercase()
