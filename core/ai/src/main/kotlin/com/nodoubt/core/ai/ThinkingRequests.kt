package com.nodoubt.core.ai

import com.google.gson.JsonPrimitive
import com.nodoubt.core.model.AiRequest
import com.nodoubt.core.model.Protocol
import com.nodoubt.core.model.ThinkingOptions
import com.nodoubt.core.model.ThinkingOverride
import com.nodoubt.core.model.ThinkingSnapshot

data class ThinkingRequest(val request: AiRequest, val snapshot: ThinkingSnapshot)

/** Prepares and locally validates one immutable request without contacting a model service. */
object ThinkingRequests {
    fun options(protocol: Protocol): ThinkingOptions {
        val (field, minimum) = when (protocol) {
            Protocol.OPENAI_COMPLETIONS -> "reasoning_effort" to null
            Protocol.OPENAI_RESPONSES -> "reasoning.effort" to null
            Protocol.ANTHROPIC_MESSAGES -> "output_config.effort" to 1024
            Protocol.GOOGLE_GENERATIVE_AI -> "generationConfig.thinkingConfig.thinkingLevel" to -1
        }
        return ThinkingOptions(protocol, ParameterCatalog.parameters(protocol).first { it.path == field }.choices, minimum)
    }

    fun prepare(request: AiRequest, override: ThinkingOverride?): ThinkingRequest {
        val endpoint = request.endpoint
        val filter = ThinkingControls(endpoint.compat.text("thinkingTokenBudgetField"))
        val parameters = if (override == null) endpoint.parameters.deepCopy() else filter.remove(endpoint.parameters)
        val compat = endpoint.compat.deepCopy()
        if (override is ThinkingOverride.Effort || override is ThinkingOverride.Budget) {
            filter.preserveAuxiliaries(endpoint.parameters, parameters)
        }
        when (override) {
            is ThinkingOverride.Effort -> {
                require(override.value in options(endpoint.protocol).efforts) { "当前协议不支持此思考层级。" }
                when (endpoint.protocol) {
                    Protocol.OPENAI_COMPLETIONS -> parameters.addProperty("reasoning_effort", override.value)
                    Protocol.OPENAI_RESPONSES -> parameters.putPath("reasoning.effort", override.value.json())
                    Protocol.ANTHROPIC_MESSAGES -> {
                        parameters.putPath("thinking.type", "adaptive".json())
                        parameters.putPath("output_config.effort", override.value.json())
                    }
                    Protocol.GOOGLE_GENERATIVE_AI -> parameters.putPath("generationConfig.thinkingConfig.thinkingLevel", override.value.json())
                }
            }
            is ThinkingOverride.Budget -> {
                val minimum = options(endpoint.protocol).minimumBudget
                require(minimum != null && override.tokens >= minimum) {
                    if (endpoint.protocol == Protocol.ANTHROPIC_MESSAGES) "思考预算必须至少为 1024。" else "当前协议的思考预算无效。"
                }
                when (endpoint.protocol) {
                    Protocol.ANTHROPIC_MESSAGES -> {
                        parameters.putPath("thinking.type", "enabled".json())
                        parameters.putPath("thinking.budget_tokens", JsonPrimitive(override.tokens))
                        // A temporary explicit budget must not be silently converted to adaptive.
                        compat.remove("forceAdaptiveThinking")
                    }
                    Protocol.GOOGLE_GENERATIVE_AI -> parameters.putPath("generationConfig.thinkingConfig.thinkingBudget", JsonPrimitive(override.tokens))
                    else -> error("当前协议不支持思考预算。")
                }
            }
            ThinkingOverride.Default, null -> Unit
        }
        val prepared = request.copy(endpoint = endpoint.copy(parameters = parameters, compat = compat))
        val body = ProtocolRequests.encode(prepared)
        val controls = filter.extract(body)
        return ThinkingRequest(prepared, ThinkingSnapshot(endpoint.protocol, override,
            ThinkingLabels.describe(controls, endpoint.compat.text("thinkingTokenBudgetField")), controls, endpoint.modelId))
    }
}
