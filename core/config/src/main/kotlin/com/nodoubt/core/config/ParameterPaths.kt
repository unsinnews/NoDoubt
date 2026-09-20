package com.nodoubt.core.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.nodoubt.core.model.ParameterMode
import com.nodoubt.core.model.ParameterValue

/** Canonical nested JSON makes object editors and dotted-path fields share one precedence rule. */
internal object ParameterPaths {
    fun resolve(channel: Map<String, ParameterValue>, model: Map<String, ParameterValue>): JsonObject {
        val inherited = apply(JsonObject(), ModelThinking.withoutChannelThinking(channel).values, JsonObject())
        return apply(inherited.deepCopy(), model, inherited)
    }

    private fun apply(target: JsonObject, layer: Map<String, ParameterValue>, inherited: JsonObject): JsonObject {
        // More specific fields always win their parent, independently of the editor's map order.
        layer.entries.sortedBy { it.key.count { character -> character == '.' } }.forEach { (key, parameter) ->
            val path = key.split('.')
            require(path.all { it.isNotBlank() }) { "参数路径不能为空：$key" }
            when (parameter.mode) {
                ParameterMode.VALUE -> parameter.value?.let { put(target, path, it, merge = true) }
                ParameterMode.OMIT -> remove(target, path)
                ParameterMode.INHERIT -> {
                    val original = find(inherited, path)
                    if (original == null) remove(target, path) else put(target, path, original, merge = false)
                }
            }
        }
        return target
    }

    private fun find(root: JsonObject, path: List<String>): JsonElement? {
        var value: JsonElement = root
        path.forEach { key ->
            if (!value.isJsonObject) return null
            value = value.asJsonObject[key] ?: return null
        }
        return value
    }

    private fun put(root: JsonObject, path: List<String>, value: JsonElement, merge: Boolean) {
        var parent = root
        path.dropLast(1).forEach { key ->
            val child = parent[key]?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject().also { parent.add(key, it) }
            parent = child
        }
        val key = path.last()
        val current = parent[key]
        parent.add(key, if (merge && current?.isJsonObject == true && value.isJsonObject) {
            mergeJson(current.asJsonObject, value.asJsonObject)
        } else value.deepCopy())
    }

    private fun remove(root: JsonObject, path: List<String>) {
        val key = path.first()
        if (path.size == 1) {
            root.remove(key)
            return
        }
        val child = root[key]?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        remove(child, path.drop(1))
        if (child.size() == 0) root.remove(key)
    }
}
