package com.nodoubt.core.ai

import com.google.gson.JsonObject
import java.math.BigInteger

/** Validate the final wire body, including the user's last-wins extraBody overrides. */
internal object AnthropicLimits {
    private val minimumBudget = BigInteger.valueOf(1024)

    fun apply(body: JsonObject) {
        val thinking = body.obj("thinking")
        val budget = if (thinking?.text("type") == "enabled") {
            thinking.integer("budget_tokens").also {
                require(it != null && it >= minimumBudget) { "请填写思考预算（至少1024），预算必须是整数。" }
            }
        } else null
        val maximum = body.integer("max_tokens")
        require(maximum != null && maximum > BigInteger.ZERO) { "Anthropic max_tokens 必须是大于 0 的整数。" }
        if (budget != null) require(maximum > budget) {
            "最大输出 Token（max_tokens=$maximum）必须大于思考预算（$budget），请调整输出上限或思考预算。"
        }
    }

    private fun JsonObject.integer(key: String): BigInteger? = get(key)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.runCatching { asBigDecimal.toBigIntegerExact() }?.getOrNull()
}
