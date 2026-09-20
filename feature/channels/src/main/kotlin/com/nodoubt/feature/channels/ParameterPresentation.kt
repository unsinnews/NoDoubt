package com.nodoubt.feature.channels

import com.nodoubt.core.model.ParameterMode
import com.nodoubt.core.model.ParameterValue

/** Show structured imports alongside their equivalent dotted parameter fields. */
internal fun parameterValueAt(values: Map<String, ParameterValue>, path: String): ParameterValue? {
    values[path]?.let { return it }
    val parent = values.keys.filter { path.startsWith("$it.") }.maxByOrNull { it.length } ?: return null
    val entry = values.getValue(parent)
    if (entry.mode != ParameterMode.VALUE) return entry
    var nested = entry.value
    path.removePrefix("$parent.").split('.').forEach { part ->
        nested = nested?.takeIf { it.isJsonObject }?.asJsonObject?.get(part)
    }
    return nested?.let { ParameterValue(ParameterMode.VALUE, it) }
}
