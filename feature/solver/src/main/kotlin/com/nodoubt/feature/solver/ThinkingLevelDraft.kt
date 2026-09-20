package com.nodoubt.feature.solver

import com.nodoubt.core.model.ThinkingOptions
import com.nodoubt.core.model.ThinkingOverride

internal fun thinkingEffortLabel(effort: String): String = when (effort.lowercase()) {
    "none" -> "关闭"
    "minimal" -> "最低"
    "low" -> "低"
    "medium" -> "中"
    "high" -> "高"
    "xhigh" -> "很高"
    "max" -> "最高"
    else -> effort
}

internal fun thinkingChoice(override: ThinkingOverride?): String = when (override) {
    null -> "model"
    ThinkingOverride.Default -> "default"
    is ThinkingOverride.Effort -> "effort:${override.value}"
    is ThinkingOverride.Budget -> "budget"
}

/** Validates manual input before the controller checks the complete request. */
internal fun thinkingDraft(choice: String, budget: String, options: ThinkingOptions): Result<ThinkingOverride?> = runCatching {
    when {
        choice == "model" -> null
        choice == "default" -> ThinkingOverride.Default
        choice.startsWith("effort:") -> {
            val effort = choice.removePrefix("effort:")
            require(effort in options.efforts) { "当前协议不支持此思考强度" }
            ThinkingOverride.Effort(effort)
        }
        choice == "budget" -> {
            val minimum = requireNotNull(options.minimumBudget) { "当前协议不支持手动预算" }
            val tokens = requireNotNull(budget.trim().toIntOrNull()) { "请输入有效的整数预算" }
            require(tokens >= minimum) { if (minimum == -1) "预算需为 -1、0 或正整数" else "思考预算至少为 $minimum" }
            ThinkingOverride.Budget(tokens)
        }
        else -> error("请选择思考设置")
    }
}
