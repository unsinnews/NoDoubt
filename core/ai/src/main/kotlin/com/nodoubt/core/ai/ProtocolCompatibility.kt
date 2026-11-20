package com.nodoubt.core.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol

/** Wire defaults and compatibility transforms, mirroring pi's four request adapters. */
internal object ProtocolCompatibility {
    private const val ANTHROPIC_DEFAULT_MAX_TOKENS = 16384
    private const val RESPONSES_MIN_OUTPUT_TOKENS = 16

    fun apply(body: JsonObject, endpoint: ModelEndpoint) {
        when (endpoint.protocol) {
            Protocol.OPENAI_COMPLETIONS -> completions(body, endpoint)
            Protocol.OPENAI_RESPONSES -> responses(body, endpoint)
            Protocol.ANTHROPIC_MESSAGES -> anthropic(body, endpoint)
            Protocol.GOOGLE_GENERATIVE_AI -> google(body, endpoint)
        }
        body.remove("thinkingBudgets")
        CacheParameters.apply(body, endpoint)
    }

    private fun completions(body: JsonObject, endpoint: ModelEndpoint) {
        val compat = CompletionsCompat.of(endpoint)
        body.rename(if (compat.maxTokensField == "max_tokens") "max_completion_tokens" else "max_tokens", compat.maxTokensField)
        if (!body.has(compat.maxTokensField)) endpoint.maxOutputTokens?.let { body.addProperty(compat.maxTokensField, it) }
        if (compat.supportsStore && !body.has("store")) body.addProperty("store", false)
        if (compat.supportsUsageInStreaming) body.putPath("stream_options.include_usage", JsonPrimitive(true))
        else body.remove("stream_options")
        compat.supportsDeveloperRole?.let { developer ->
            body.array("messages").forEach { element ->
                val message = element.objectOrNull() ?: return@forEach
                if (message.text("role") in setOf("system", "developer")) message.addProperty("role", if (developer) "developer" else "system")
            }
        }
        ThinkingParameters.apply(body, endpoint, compat)
        strictTools(body, endpoint.compat.flag("supportsStrictMode"), nested = true)
    }

    private fun responses(body: JsonObject, endpoint: ModelEndpoint) {
        val compat = endpoint.compat
        if (compat.flag("supportsDeveloperRole") == false) body.array("input").forEach { element ->
            element.objectOrNull()?.takeIf { it.text("role") == "developer" }?.addProperty("role", "system")
        }
        if (!body.has("max_output_tokens")) endpoint.maxOutputTokens?.let { body.addProperty("max_output_tokens", it) }
        body.number("max_output_tokens")?.let { body.addProperty("max_output_tokens", maxOf(it, RESPONSES_MIN_OUTPUT_TOKENS)) }
        if (compat.flag("supportsMaxOutputTokens") == false) body.remove("max_output_tokens")
        if (!body.has("store")) body.addProperty("store", false)
        val reasoning = body.obj("reasoning")
        val effort = reasoning?.text("effort")
        if (reasoning != null && effort != null && effort != "none" && !reasoning.has("summary")) reasoning.addProperty("summary", "auto")
        strictTools(body, compat.flag("supportsStrictMode"), nested = false)
    }

    private fun anthropic(body: JsonObject, endpoint: ModelEndpoint) {
        if (!body.has("max_tokens")) body.addProperty("max_tokens", endpoint.maxOutputTokens ?: ANTHROPIC_DEFAULT_MAX_TOKENS)
        body.obj("thinking")?.let { thinking ->
            val type = thinking.text("type")
            if (endpoint.compat.flag("forceAdaptiveThinking") == true && type != null && type != "disabled") {
                thinking.addProperty("type", "adaptive")
                thinking.remove("budget_tokens")
            } else if (type == "enabled" && !thinking.has("budget_tokens")) thinking.addProperty("budget_tokens", 1024)
            if (thinking.text("type") in setOf("enabled", "adaptive") && !thinking.has("display")) {
                thinking.addProperty("display", "summarized")
            }
        }
        strictTools(body, endpoint.compat.flag("supportsStrictTools"), nested = false)
    }

    private fun google(body: JsonObject, endpoint: ModelEndpoint) {
        if (body.obj("generationConfig")?.has("maxOutputTokens") != true) endpoint.maxOutputTokens?.let {
            body.putPath("generationConfig.maxOutputTokens", JsonPrimitive(it))
        }
        val thinking = body.obj("generationConfig")?.obj("thinkingConfig") ?: return
        val enabled = thinking.has("thinkingLevel") || (thinking.has("thinkingBudget") && thinking.number("thinkingBudget") != 0)
        if (enabled && !thinking.has("includeThoughts")) thinking.addProperty("includeThoughts", true)
    }

    private fun strictTools(body: JsonObject, enabled: Boolean?, nested: Boolean) {
        if (enabled == null) return
        body.array("tools").forEach { element ->
            val tool = element.objectOrNull() ?: return@forEach
            val definition = if (nested) tool.obj("function") ?: tool else tool
            if (enabled) definition.addProperty("strict", true) else definition.remove("strict")
        }
    }
}

internal fun JsonArray.lastObject(predicate: (JsonObject) -> Boolean): JsonObject? =
    toList().asReversed().mapNotNull { it.objectOrNull() }.firstOrNull(predicate)
