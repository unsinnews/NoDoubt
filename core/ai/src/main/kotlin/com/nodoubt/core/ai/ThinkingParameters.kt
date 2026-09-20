package com.nodoubt.core.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

internal object ThinkingParameters {
    fun apply(body: JsonObject, compat: JsonObject) {
        val effort = body.text("reasoning_effort") ?: run { body.remove("thinkingBudgets"); return }
        val enabled = effort !in setOf("none", "off")
        val format = compat.text("thinkingFormat") ?: "openai"
        when (format) {
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
                compat.obj(source)?.let { template ->
                    body.add(target, jsonObject {
                        template.entrySet().forEach { (key, value) ->
                            val resolved = resolve(value, enabled, effort, body)
                            if (resolved != null) add(key, resolved)
                        }
                    })
                }
            }
        }
        if (format != "openai" && compat.flag("supportsReasoningEffort") != true) body.remove("reasoning_effort")
        val budgetField = compat.text("thinkingTokenBudgetField")
            ?: "thinking_token_budget".takeIf { compat.flag("supportsThinkingTokenBudget") == true }
        val budgets = body.remove("thinkingBudgets")?.objectOrNull()
        if (enabled && budgetField != null) {
            val budget = budgets?.number(effort)
            if (budget != null) {
                val maximum = body.number("max_completion_tokens") ?: body.number("max_tokens")
                body.addProperty(budgetField, if (maximum != null) budget.coerceAtMost((maximum - 1024).coerceAtLeast(0)) else budget)
            }
        }
    }

    private fun resolve(value: JsonElement, enabled: Boolean, effort: String, body: JsonObject): JsonElement? {
        val variable = value.objectOrNull() ?: return value.deepCopy()
        if (!enabled && variable.flag("omitWhenOff") == true) return null
        return when (variable.text("\$var")) {
            "thinking.enabled" -> JsonPrimitive(enabled)
            "thinking.effort" -> effort.json()
            "thinking.budget" -> body.obj("thinkingBudgets")?.get(effort)?.deepCopy()
            else -> value.deepCopy()
        }
    }
}
