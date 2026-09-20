package com.nodoubt.core.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol
import okhttp3.Request

internal object ProtocolCompatibility {
    fun apply(body: JsonObject, endpoint: ModelEndpoint) {
        val compat = endpoint.compat
        when (endpoint.protocol) {
            Protocol.OPENAI_COMPLETIONS -> completions(body, compat)
            Protocol.OPENAI_RESPONSES -> {
                if (compat.flag("supportsMaxOutputTokens") == false) body.remove("max_output_tokens")
                if (compat.flag("supportsExplicitPromptCacheMode") == false) body.remove("prompt_cache_options")
                roles(body.array("input"), compat)
                strictTools(body, compat.flag("supportsStrictMode"), false)
            }
            Protocol.ANTHROPIC_MESSAGES -> anthropic(body, compat)
            Protocol.GOOGLE_GENERATIVE_AI -> Unit
        }
        CacheParameters.apply(body, endpoint)
    }

    private fun completions(body: JsonObject, compat: JsonObject) {
        if (compat.flag("supportsStore") == false) body.remove("store")
        when (compat.flag("supportsUsageInStreaming")) {
            true -> body.putPath("stream_options.include_usage", com.google.gson.JsonPrimitive(true))
            false -> body.remove("stream_options")
            null -> Unit
        }
        compat.text("maxTokensField")?.takeIf { it in setOf("max_tokens", "max_completion_tokens") }?.let {
            body.rename(if (it == "max_tokens") "max_completion_tokens" else "max_tokens", it)
        }
        roles(body.array("messages"), compat)
        body.array("messages").forEach { element ->
            val message = element.objectOrNull() ?: return@forEach
            if (message.text("role") == "assistant" && compat.flag("requiresReasoningContentOnAssistantMessages") == true) {
                if (!message.has("reasoning_content")) message.addProperty("reasoning_content", "")
            }
        }
        ThinkingParameters.apply(body, compat)
        if (compat.flag("supportsReasoningEffort") == false) body.remove("reasoning_effort")
        compat.obj("openRouterRouting")?.let { body.add("provider", it.deepCopy()) }
        compat.obj("vercelGatewayRouting")?.let { body.putPath("providerOptions.gateway", it) }
        compat.get("vllmPriority")?.let { body.add("priority", it.deepCopy()) }
        if (compat.flag("zaiToolStream") == true && body.array("tools").size() > 0) body.addProperty("tool_stream", true)
        strictTools(body, compat.flag("supportsStrictMode"), true)
    }

    private fun roles(messages: JsonArray, compat: JsonObject) {
        messages.forEach { element ->
            val message = element.objectOrNull() ?: return@forEach
            if (message.text("role") in setOf("system", "developer") && compat.has("supportsDeveloperRole")) {
                message.addProperty("role", if (compat.flag("supportsDeveloperRole") == true) "developer" else "system")
            }
        }
    }

    private fun anthropic(body: JsonObject, compat: JsonObject) {
        if (compat.flag("supportsTemperature") == false) body.remove("temperature")
        val thinking = body.obj("thinking")
        if (compat.flag("forceAdaptiveThinking") == true && thinking != null && thinking.text("type") != "disabled") {
            thinking.addProperty("type", "adaptive")
            thinking.remove("budget_tokens")
        }
        body.array("tools").forEach { element ->
            val tool = element.objectOrNull() ?: return@forEach
            compat.flag("supportsEagerToolInputStreaming")?.let {
                if (it) tool.addProperty("eager_input_streaming", true) else tool.remove("eager_input_streaming")
            }
            if (compat.flag("supportsCacheControlOnTools") == false) tool.remove("cache_control")
        }
        strictTools(body, compat.flag("supportsStrictTools"), false)
        compat.get("allowedFallbackModels")?.takeIf { it.isJsonArray }?.asJsonArray?.let { models ->
            if (models.isEmpty) body.remove("fallbacks")
            else body.add("fallbacks", jsonArray(models.take(3).map { model -> jsonObject {
                addProperty("model", model.asJsonObject.text("model"))
            } }))
        }
    }

    private fun strictTools(body: JsonObject, enabled: Boolean?, nested: Boolean) {
        if (enabled == null) return
        body.array("tools").forEach { element ->
            val tool = element.objectOrNull() ?: return@forEach
            val definition = if (nested) tool.obj("function") ?: tool else tool
            if (enabled) definition.addProperty("strict", true) else definition.remove("strict")
        }
    }

    fun headers(builder: Request.Builder, endpoint: ModelEndpoint, body: JsonObject) {
        val compat = endpoint.compat
        if (endpoint.protocol == Protocol.ANTHROPIC_MESSAGES) {
            val betas = mutableListOf<String>()
            if (endpoint.parameters.flag("interleavedThinking") == true && body.obj("thinking")?.text("type") != "adaptive") {
                betas.add("interleaved-thinking-2025-05-14")
            }
            if (body.array("fallbacks").size() > 0) betas.add("server-side-fallback-2026-07-01")
            if (compat.flag("supportsEagerToolInputStreaming") == false && body.array("tools").size() > 0) {
                betas.add("fine-grained-tool-streaming-2025-05-14")
            }
            if (betas.isNotEmpty() && endpoint.headers.keys.none { it.equals("anthropic-beta", true) }) {
                builder.header("anthropic-beta", betas.joinToString(","))
            }
        }
        val session = endpoint.parameters.text("sessionId") ?: return
        if (compat.flag("sendSessionAffinityHeaders") != true && !compat.has("sessionAffinityFormat")) return
        when (compat.text("sessionAffinityFormat")) {
            "openrouter" -> builder.header("x-session-id", session)
            "openai", "openai-nosession" -> {
                if (compat.text("sessionAffinityFormat") == "openai") builder.header("session_id", session)
                builder.header("x-client-request-id", session)
                if (endpoint.protocol == Protocol.OPENAI_COMPLETIONS) builder.header("x-session-affinity", session)
            }
            else -> builder.header("x-session-affinity", session)
        }
    }
}
