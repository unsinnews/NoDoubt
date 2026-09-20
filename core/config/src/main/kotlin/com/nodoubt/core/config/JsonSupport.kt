package com.nodoubt.core.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness

internal val configGson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

internal fun parseObject(json: String): JsonObject {
    require(json.toByteArray().size <= 2 * 1024 * 1024) { "配置文件不能超过 2 MB" }
    val reader = com.google.gson.stream.JsonReader(json.reader()).apply { strictness = Strictness.STRICT }
    val parsed = JsonParser.parseReader(reader)
    require(reader.peek() == com.google.gson.stream.JsonToken.END_DOCUMENT) { "配置 JSON 存在多余内容" }
    require(parsed.isJsonObject) { "配置必须是 JSON 对象" }
    return parsed.asJsonObject
}

internal fun JsonObject.obj(key: String): JsonObject = get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
internal fun JsonObject.text(key: String, default: String = ""): String =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: default
internal fun JsonObject.intOrNull(key: String): Int? = get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }
internal fun JsonObject.boolOrNull(key: String): Boolean? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
internal fun JsonObject.strings(): Map<String, String> = entrySet().associate { (key, value) ->
    require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "$key 必须是字符串" }
    key to value.asString
}
internal fun JsonObject.objects(key: String): List<JsonObject> {
    val value = get(key) ?: return emptyList()
    require(value.isJsonArray) { "$key 必须是数组" }
    return value.asJsonArray.map { require(it.isJsonObject) { "$key 中的条目必须是对象" }; it.asJsonObject }
}

/** Object fields merge recursively, while arrays and scalars are explicitly replaced. */
fun mergeJson(base: JsonObject, override: JsonObject): JsonObject = base.deepCopy().apply {
    override.entrySet().forEach { (key, value) ->
        add(key, if (get(key)?.isJsonObject == true && value.isJsonObject) {
            mergeJson(getAsJsonObject(key), value.asJsonObject)
        } else value.deepCopy())
    }
}

internal fun jsonMap(values: Map<String, String>): JsonObject = JsonObject().apply {
    values.forEach { (key, value) -> addProperty(key, value) }
}

internal fun JsonElement?.copyOrNull(): JsonElement? = this?.deepCopy()
