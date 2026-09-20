package com.nodoubt.core.config

import com.google.gson.JsonParser
import com.nodoubt.core.model.*

/** Pure migration entry point, shared by Android storage and JVM fixture tests. */
object LegacyMigration {
    fun migrate(values: Map<String, *>, themeValues: Map<String, *> = emptyMap<String, Any>()): ImportPreview {
        val preferences = migratePreferences(values, themeValues)
        if (values.isEmpty()) return ImportPreview(AppConfig(preferences = preferences))
        val warnings = mutableListOf<String>()
        val credentials = mutableMapOf<String, String>()
        val channels = mutableListOf<ChannelConfig>()
        val bindings: Map<Usage, UsageBinding>
        val rawChannels = values.string("channels_json")
        if (rawChannels.isNotBlank()) {
            val array = runCatching { JsonParser.parseString(rawChannels) }.getOrElse {
                throw IllegalArgumentException("旧渠道 JSON 损坏，原始设置已保留，请恢复或导入配置。", it)
            }
            require(array.isJsonArray) { "旧渠道 JSON 必须是数组，原始设置未更改。" }
            array.asJsonArray.forEach { entry ->
                require(entry.isJsonObject) { "旧渠道条目损坏，原始设置未更改。" }
                val obj = entry.asJsonObject
                val id = obj.text("id").ifBlank { "migrated-${channels.size}" }
                val type = obj.text("type")
                if (type !in listOf("", "openai_chat_completions")) warnings += "$id：旧协议 $type 已按 OpenAI Chat Completions 迁移，请核对。"
                val channel = ChannelConfig(id, obj.text("name").ifBlank { "迁移渠道 ${channels.size + 1}" },
                    baseUrl = obj.text("baseUrl").ifBlank { Protocol.OPENAI_COMPLETIONS.defaultBaseUrl }, credentialRef = "secret:channel:$id")
                channels += channel
                obj.text("apiKey").takeIf { it.isNotBlank() }?.let { credentials[channel.credentialRef] = it }
            }
            bindings = channelBindings(values, channels)
            channels.replaceAll { channel ->
                val ids = bindings.values.flatMap { it.entries }.filter { it.channelId == channel.id }.map { it.modelId }.distinct()
                channel.copy(models = ids.map(::ModelConfig))
            }
        } else {
            val intermediate = values.keys.any { it.startsWith("default_channel_") }
            val prefix = if (intermediate) "default_channel_" else ""
            val id = values.string("${prefix}id").ifBlank { "migrated-default" }
            val base = first(values, "${prefix}base_url", "base_url", "ocr_base_url", "fast_base_url", "deep_base_url")
                .ifBlank { Protocol.OPENAI_COMPLETIONS.defaultBaseUrl }
            val ref = "secret:channel:$id"
            first(values, "${prefix}api_key", "api_key").takeIf { it.isNotBlank() }?.let { credentials[ref] = it }
            bindings = Usage.entries.associateWith { usage ->
                val key = usage.name.lowercase()
                val fallback = if (usage == Usage.FAST) "gpt-4o-mini" else "gpt-4o"
                val selected = first(values, "${prefix}${key}_model_id", "${key}_model_id").ifBlank { fallback }
                val list = first(values, "${prefix}${key}_model_list", "${key}_model_list")
                    .split('\n').map(String::trim).filter(String::isNotBlank).ifEmpty { listOf(selected) }
                val entries = (list + selected).distinct().map { ModelRef(id, it) }
                UsageBinding(entries, ModelRef(id, selected))
            }
            channels += ChannelConfig(id, values.string("${prefix}name").ifBlank { "已迁移渠道" }, baseUrl = base,
                credentialRef = ref, models = bindings.values.flatMap { it.entries }.map { it.modelId }.distinct().map(::ModelConfig))
        }
        val config = ConfigValidator.cleanBindings(ConfigValidator.validate(AppConfig(
            providers = channels,
            bindings = bindings,
            preferences = preferences
        )))
        return ImportPreview(config, warnings, credentials = credentials)
    }

    private fun migratePreferences(values: Map<String, *>, theme: Map<String, *>): AppPreferences = AppPreferences(
        theme = ThemeFamily.PICOVE,
        autoDeleteScreenshot = values["auto_delete_screenshot"] as? Boolean ?: true,
        bubbleSizeDp = ((theme["float_size"] as? Number)?.toInt() ?: 48).coerceIn(36, 80),
        nickname = theme.string("user_nickname").ifBlank { AppPreferences().nickname },
        avatar = listOf("spark", "brain", "star", "rocket").getOrElse((theme["avatar_index"] as? Number)?.toInt() ?: 0) { "spark" }
    )

    private fun channelBindings(values: Map<String, *>, channels: List<ChannelConfig>): Map<Usage, UsageBinding> {
        val raw = values.string("usage_model_pools_json")
        if (raw.isBlank()) return Usage.entries.associateWith { UsageBinding() }
        val parsed = runCatching { JsonParser.parseString(raw) }.getOrElse {
            throw IllegalArgumentException("旧用途模型 JSON 损坏，原始设置未更改。", it)
        }
        require(parsed.isJsonArray) { "旧用途模型 JSON 必须是数组" }
        val pools = parsed.asJsonArray.map {
            require(it.isJsonObject) { "旧用途模型条目损坏" }; it.asJsonObject
        }.associateBy { it.text("usage").uppercase() }
        return Usage.entries.associateWith { usage ->
            val pool = pools[usage.name]
            val entries = pool?.objects("entries")?.map { ModelRef(it.text("channelId"), it.text("modelId")) }
                ?.filter { ref -> channels.any { it.id == ref.channelId } && ref.modelId.isNotBlank() }?.distinct().orEmpty()
            val selected = pool?.let { ModelRef(it.text("selectedChannelId"), it.text("selectedModelId")) }
            UsageBinding(entries, selected?.takeIf { it in entries } ?: entries.firstOrNull())
        }
    }

    private fun Map<String, *>.string(key: String): String = this[key] as? String ?: ""
    private fun first(values: Map<String, *>, vararg keys: String): String = keys.map { values.string(it).trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
}
