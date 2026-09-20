package com.nodoubt.core.config

import com.google.gson.JsonObject
import com.nodoubt.core.model.ParameterValue
import com.nodoubt.core.model.ThinkingParameterPaths

/** Thinking is a model choice. Channel defaults, including extraBody, cannot enable it. */
internal object ModelThinking {
    data class ChannelParameters(val values: Map<String, ParameterValue>, val ignored: List<String>)

    fun withoutChannelThinking(parameters: Map<String, ParameterValue>): ChannelParameters {
        val ignored = mutableListOf<String>()
        val values = parameters.mapNotNull { (key, value) ->
            val path = key.split('.')
            when {
                ThinkingParameterPaths.isThinking(path) -> { ignored += key; null }
                value.value?.isJsonObject == true -> {
                    val original = requireNotNull(value.value).asJsonObject
                    val filtered = filterObject(original, path, ignored)
                    if (filtered.size() == 0 && original.size() > 0) null else key to value.copy(value = filtered)
                }
                else -> key to value
            }
        }.toMap()
        return ChannelParameters(values, ignored.distinct())
    }

    private fun filterObject(source: JsonObject, path: List<String>, ignored: MutableList<String>): JsonObject = JsonObject().apply {
        source.entrySet().forEach { (key, value) ->
            val childPath = path + key.split('.')
            if (ThinkingParameterPaths.isThinking(childPath)) ignored += childPath.joinToString(".")
            else if (value.isJsonObject) {
                val filtered = filterObject(value.asJsonObject, childPath, ignored)
                if (filtered.size() > 0 || value.asJsonObject.size() == 0) add(key, filtered)
            } else add(key, value.deepCopy())
        }
    }

}
