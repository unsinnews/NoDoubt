package com.nodoubt.feature.channels

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.nodoubt.core.ai.ParameterDefinition
import com.nodoubt.core.model.ParameterMode
import com.nodoubt.core.model.ParameterValue
import com.nodoubt.core.model.ThinkingParameterPaths

internal fun isThinkingPath(path: String): Boolean = ThinkingParameterPaths.isThinking(path)

internal fun ParameterDefinition.isThinkingParameter(): Boolean = isThinkingPath(path)

internal fun clearThinkingParameters(values: Map<String, ParameterValue>): Map<String, ParameterValue> =
    filterParameterFields(values, ::isThinkingPath)

internal fun hasThinkingParameters(values: Map<String, ParameterValue>): Boolean {
    val explicit = values.filterValues { it.mode == ParameterMode.VALUE }
    return clearThinkingParameters(explicit) != explicit
}

internal fun clearThinkingField(values: Map<String, ParameterValue>, path: String): Map<String, ParameterValue> =
    filterParameterFields(values) { ThinkingParameterPaths.matchesField(it, path) }

private fun filterParameterFields(
    values: Map<String, ParameterValue>, remove: (String) -> Boolean
): Map<String, ParameterValue> = values.mapNotNull { (path, entry) ->
    if (remove(path)) null
    else if (entry.value == null) path to entry
    else filterJsonFields(requireNotNull(entry.value), path, remove)?.let { path to entry.copy(value = it) }
}.toMap()

private fun filterJsonFields(value: JsonElement, path: String, remove: (String) -> Boolean): JsonElement? {
    if (remove(path)) return null
    if (!value.isJsonObject) return value.deepCopy()
    val cleaned = JsonObject()
    value.asJsonObject.entrySet().forEach { (name, child) ->
        filterJsonFields(child, "$path.$name", remove)?.let { cleaned.add(name, it) }
    }
    return cleaned.takeUnless { it.size() == 0 && value.asJsonObject.size() > 0 }
}
