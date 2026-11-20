package com.nodoubt.core.config

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.nodoubt.core.model.AppConfig
import com.nodoubt.core.model.Usage

object ConfigMerge {
    fun merge(local: AppConfig, incoming: AppConfig): AppConfig {
        val combined = mergeElements(document(local), document(incoming)).asJsonObject
        val decoded = decodeDocument(combined)
        return ConfigValidator.cleanBindings(decoded.copy(
            id = local.id, name = local.name, preferences = local.preferences,
            bindings = Usage.entries.associateWith { usage ->
                incoming.bindings.getValue(usage).takeIf { it.entries.isNotEmpty() } ?: local.bindings.getValue(usage)
            }, source = incoming.source ?: local.source
        ))
    }

    /** Field-level three-way merge; changes made locally since the baseline win conflicts. */
    fun threeWay(baseline: AppConfig, local: AppConfig, remote: AppConfig): AppConfig {
        val merged = threeWayValue(document(baseline), document(local), document(remote))!!.asJsonObject
        return ConfigValidator.cleanBindings(decodeDocument(merged).copy(id = local.id, name = local.name))
    }

    fun changes(before: AppConfig, after: AppConfig): List<String> {
        val result = mutableListOf<String>()
        val oldChannels = before.providers.associateBy { it.id }
        val newChannels = after.providers.associateBy { it.id }
        (oldChannels.keys + newChannels.keys).forEach { id ->
            val old = oldChannels[id]
            val new = newChannels[id]
            when {
                old == null -> result += "新增服务：${new!!.name}（${new.models.size} 个模型）"
                new == null -> result += "删除服务：${old.name}"
                old != new -> {
                    result += "修改服务：${new.name}"
                    val added = new.models.map { it.id }.toSet() - old.models.map { it.id }.toSet()
                    val removed = old.models.map { it.id }.toSet() - new.models.map { it.id }.toSet()
                    added.forEach { result += "新增模型：${new.name}/$it" }
                    removed.forEach { result += "移除模型：${new.name}/$it" }
                }
            }
        }
        if (before.bindings != after.bindings) result += "修改解题模型"
        if (before.preferences != after.preferences) result += "修改应用设置"
        if (before.variables != after.variables) result += "修改变量映射"
        return result.ifEmpty { listOf("与当前配置相同") }
    }

    // Co-locate extensions with their entity while merging. Otherwise deleting a remote model
    // could incorrectly delete one whose only local change lives in nodoubt.parameters.
    private fun document(config: AppConfig): JsonObject {
        val root = parseObject(ConfigCodec.encode(config.copy(source = null)))
        val extensions = root.obj("nodoubt").obj("channels")
        root.obj("providers").entrySet().forEach { (id, value) ->
            val channel = value.asJsonObject
            val extension = extensions.obj(id).deepCopy()
            channel.objects("models").forEach { model -> model.add("_nodoubt", extension.obj("models").obj(model.text("id")).deepCopy()) }
            extension.remove("models")
            channel.add("_nodoubt", extension)
        }
        root.obj("nodoubt").remove("channels")
        return root
    }

    private fun decodeDocument(root: JsonObject): AppConfig {
        val extensions = JsonObject()
        root.obj("providers").entrySet().forEach { (id, value) ->
            val channel = value.asJsonObject
            val extension = channel.remove("_nodoubt")?.asJsonObject ?: JsonObject()
            extension.add("models", JsonObject().apply {
                channel.objects("models").forEach { model ->
                    add(model.text("id"), model.remove("_nodoubt") ?: JsonObject())
                }
            })
            extensions.add(id, extension)
        }
        root.obj("nodoubt").add("channels", extensions)
        return ConfigCodec.decode(root.toString()).config
    }

    private fun threeWayValue(base: JsonElement?, local: JsonElement?, remote: JsonElement?): JsonElement? {
        if (local == base) return remote.copyOrNull()
        if (remote == base || local == remote) return local.copyOrNull()
        if (local?.isJsonObject == true && remote?.isJsonObject == true) {
            val b = base?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
            val l = local.asJsonObject
            val r = remote.asJsonObject
            return JsonObject().apply {
                (b.keySet() + l.keySet() + r.keySet()).forEach { key ->
                    threeWayValue(b[key], l[key], r[key])?.let { add(key, it) }
                }
            }
        }
        if (local?.isJsonArray == true && remote?.isJsonArray == true && keyed(local) && keyed(remote)) {
            val b = keyedMap(base)
            val l = keyedMap(local)
            val r = keyedMap(remote)
            return JsonArray().apply { (l.keys + r.keys + b.keys).forEach { id ->
                threeWayValue(b[id], l[id], r[id])?.let(::add)
            } }
        }
        return local.copyOrNull()
    }

    private fun mergeElements(left: JsonElement, right: JsonElement): JsonElement {
        if (left.isJsonObject && right.isJsonObject) return left.asJsonObject.deepCopy().apply {
            right.asJsonObject.entrySet().forEach { (key, value) ->
                add(key, get(key)?.let { mergeElements(it, value) } ?: value.deepCopy())
            }
        }
        if (left.isJsonArray && right.isJsonArray && keyed(left) && keyed(right)) {
            val first = keyedMap(left)
            val second = keyedMap(right)
            return JsonArray().apply { (first.keys + second.keys).forEach { key ->
                add(when {
                    first[key] == null -> second.getValue(key).deepCopy()
                    second[key] == null -> first.getValue(key).deepCopy()
                    else -> mergeElements(first.getValue(key), second.getValue(key))
                })
            } }
        }
        return right.deepCopy()
    }

    private fun keyed(element: JsonElement): Boolean = element.asJsonArray.all {
        it.isJsonObject && it.asJsonObject.text("id").isNotBlank()
    }

    private fun keyedMap(element: JsonElement?): Map<String, JsonElement> =
        element?.takeIf { it.isJsonArray && keyed(it) }?.asJsonArray?.associateBy { it.asJsonObject.text("id") } ?: emptyMap()
}
