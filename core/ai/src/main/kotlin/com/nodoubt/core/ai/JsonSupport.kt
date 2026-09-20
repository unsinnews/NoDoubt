package com.nodoubt.core.ai

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

internal fun jsonObject(block: JsonObject.() -> Unit): JsonObject = JsonObject().apply(block)
internal fun jsonArray(items: Iterable<JsonElement>): JsonArray = JsonArray().apply { items.forEach(::add) }
internal fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
internal fun JsonObject.number(key: String): Int? = get(key)?.takeIf { it.isJsonPrimitive }?.runCatching { asInt }?.getOrNull()
internal fun JsonObject.flag(key: String): Boolean? = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
internal fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
internal fun JsonObject.array(key: String): JsonArray = get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
internal fun JsonElement.objectOrNull(): JsonObject? = takeIf { isJsonObject }?.asJsonObject

internal fun JsonObject.merge(other: JsonObject) {
    other.entrySet().forEach { (key, value) ->
        val current = obj(key)
        if (current != null && value.isJsonObject) current.merge(value.asJsonObject)
        else add(key, value.deepCopy())
    }
}

internal fun JsonObject.putPath(path: String, value: JsonElement) {
    val parts = path.split('.')
    require(parts.all { it.isNotBlank() }) { "参数路径不能为空：$path" }
    var target = this
    parts.dropLast(1).forEach { part ->
        val next = target.obj(part) ?: JsonObject().also { target.add(part, it) }
        target = next
    }
    target.add(parts.last(), value.deepCopy())
}

internal val clientParameterKeys = setOf("timeoutMs", "maxRetries", "maxRetryDelayMs", "cacheRetention", "sessionId", "interleavedThinking")

internal fun expandedParameters(parameters: JsonObject): JsonObject = jsonObject {
    parameters.entrySet().filter { it.key !in clientParameterKeys && it.key != "extraBody" }.forEach { (key, value) ->
        putPath(key, value)
    }
}

internal fun JsonObject.rename(from: String, to: String) {
    if (has(from) && from != to) add(to, remove(from))
}

internal fun contentText(value: JsonElement?): String = when {
    value == null || value.isJsonNull -> ""
    value.isJsonPrimitive -> value.asString
    value.isJsonArray -> value.asJsonArray.joinToString("") { contentText(it) }
    value.isJsonObject -> value.asJsonObject.text("text") ?: ""
    else -> ""
}

internal fun String.json(): JsonPrimitive = JsonPrimitive(this)
