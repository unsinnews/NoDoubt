package com.nodoubt.core.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.nodoubt.core.model.ModelEndpoint

/** Translates the configured reasoning_effort into each OpenAI-compatible thinking format, as in pi. */
internal object ThinkingParameters {
    private val defaultBudgets = mapOf("minimal" to 1024, "low" to 2048, "medium" to 8192, "high" to 16384)
    private const val MIN_ANSWER_TOKENS = 1024
    private const val DEFAULT_OUTPUT_TOKENS = 16384
    /** Formats that additionally send reasoning_effort when the service accepts it. */
    private val effortFormats = setOf("openai", "deepseek", "zai", "qwen", "together", "baseten")

    fun apply(body: JsonObject, endpoint: ModelEndpoint, compat: CompletionsCompat) {
        val custom = body.remove("thinkingBudgets")?.objectOrNull()
        val effort = body.text("reasoning_effort") ?: return
        val enabled = effort !in setOf("none", "off")
        val budget = if (enabled) budget(effort, custom, body, endpoint) else null
        when (val format = compat.thinkingFormat) {
            "openrouter", "ant-ling" -> body.putPath("reasoning.effort", effort.json())
            "together" -> body.putPath("reasoning.enabled", JsonPrimitive(enabled))
            "deepseek", "zai" -> {
                body.putPath("thinking.type", (if (enabled) "enabled" else "disabled").json())
                if (format == "zai" && enabled) body.putPath("thinking.clear_thinking", JsonPrimitive(false))
            }
            "qwen" -> body.addProperty("enable_thinking", enabled)
            "qwen-chat-template" -> {
                body.putPath("chat_template_kwargs.enable_thinking", JsonPrimitive(enabled))
                body.putPath("chat_template_kwargs.preserve_thinking", JsonPrimitive(true))
            }
            "string-thinking" -> body.addProperty("thinking", effort)
            "chat-template", "baseten" -> {
                val source = if (format == "baseten") "chatTemplateArgs" else "chatTemplateKwargs"
                val target = if (format == "baseten") "chat_template_args" else "chat_template_kwargs"
                endpoint.compat.obj(source)?.let { template ->
                    val values = jsonObject {
                        template.entrySet().forEach { (key, value) -> resolve(value, enabled, effort, budget)?.let { add(key, it) } }
                    }
                    if (values.size() > 0) body.add(target, values)
                }
            }
        }
        if (compat.thinkingFormat !in effortFormats || !compat.supportsReasoningEffort) body.remove("reasoning_effort")
        if (budget != null && compat.thinkingBudgetField != null) body.addProperty(compat.thinkingBudgetField, budget)
    }

    /** Budgets share the answer's output ceiling, so at least 1024 tokens remain for the answer. */
    private fun budget(effort: String, custom: JsonObject?, body: JsonObject, endpoint: ModelEndpoint): Int? {
        val level = if (effort in setOf("xhigh", "max")) "high" else effort
        val requested = custom?.number(effort) ?: custom?.number(level) ?: defaultBudgets[level] ?: return null
        val ceiling = body.number("max_tokens") ?: body.number("max_completion_tokens")
            ?: endpoint.maxOutputTokens ?: DEFAULT_OUTPUT_TOKENS
        return requested.coerceAtMost((ceiling - MIN_ANSWER_TOKENS).coerceAtLeast(0)).takeIf { it > 0 }
    }

    private fun resolve(value: JsonElement, enabled: Boolean, effort: String, budget: Int?): JsonElement? {
        val variable = value.objectOrNull() ?: return value.deepCopy()
        if (!enabled && variable.flag("omitWhenOff") == true) return null
        return when (variable.text("\$var")) {
            "thinking.enabled" -> JsonPrimitive(enabled)
            "thinking.effort" -> effort.json()
            "thinking.budget" -> budget?.let(::JsonPrimitive)
            else -> value.deepCopy()
        }
    }
}
