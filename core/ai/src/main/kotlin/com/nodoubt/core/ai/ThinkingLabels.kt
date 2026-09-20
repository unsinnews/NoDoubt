package com.nodoubt.core.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Labels come from wire fields after compatibility transforms and validation. */
internal object ThinkingLabels {
    fun describe(controls: JsonObject, budgetField: String?): String {
        if (controls.size() == 0) return "服务端默认"
        val google = controls.obj("generationConfig")?.obj("thinkingConfig")
        val thinking = controls.obj("thinking")
        val effort = controls.text("reasoning_effort") ?: controls.obj("reasoning")?.text("effort") ?:
            controls.obj("output_config")?.text("effort") ?: google?.text("thinkingLevel")
        val budget = thinking?.get("budget_tokens") ?: google?.get("thinkingBudget") ?:
            budgetField?.let { atPath(controls, it) } ?: controls.get("thinking_token_budget") ?:
            controls.get("thinking_budget") ?: controls.get("thinking_budget_tokens")
        val budgetLabel = budget?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.let {
            when (it.asString) { "-1" -> "动态预算（服务端）"; "0" -> "预算 0"; else -> "预算 ${it.asString}" }
        }
        val effortLabel = effort?.let(::effortLabel)
        if (effortLabel != null || budgetLabel != null) return listOfNotNull(effortLabel, budgetLabel).joinToString(" · ")
        when (thinking?.text("type")) {
            "adaptive" -> return "自适应"
            "enabled" -> return "已开启"
            "disabled" -> return "已关闭"
        }
        val enabled = controls.flag("enable_thinking") ?: controls.obj("reasoning")?.flag("enabled") ?:
            controls.obj("chat_template_kwargs")?.flag("enable_thinking") ?: controls.obj("chat_template_args")?.flag("enable_thinking")
        if (enabled != null) return if (enabled) "已开启" else "已关闭"
        controls.text("thinking")?.let { return effortLabel(it) }
        return if (ThinkingControls(budgetField).hasStrength(controls)) "自定义" else "服务端默认"
    }

    private fun effortLabel(value: String): String = when (value.lowercase()) {
        "none", "off", "disabled" -> "关闭"
        "minimal" -> "最低"
        "low" -> "低"
        "medium" -> "中"
        "high" -> "高"
        "xhigh" -> "很高"
        "max" -> "最高"
        else -> value
    }

    private fun atPath(source: JsonObject, path: String): JsonElement? {
        var value: JsonElement = source
        path.split('.').forEach { key -> value = value.objectOrNull()?.get(key) ?: return null }
        return value
    }
}
