package com.nodoubt.core.model

import com.google.gson.JsonObject

/** null means follow the model; Default explicitly removes request thinking controls. */
sealed interface ThinkingOverride {
    data object Default : ThinkingOverride
    data class Effort(val value: String) : ThinkingOverride
    data class Budget(val tokens: Int) : ThinkingOverride
}

data class ThinkingOptions(val protocol: Protocol, val efforts: List<String>, val minimumBudget: Int? = null)

/** A snapshot of encoded request controls, never a claim about the server's internal reasoning. */
data class ThinkingSnapshot(
    val protocol: Protocol,
    val override: ThinkingOverride?,
    val label: String,
    val controls: JsonObject,
    val modelId: String
)
