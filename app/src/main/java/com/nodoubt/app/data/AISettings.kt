package com.nodoubt.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

object AISettings {
    private const val PREFS_NAME = "ai_settings"

    private const val KEY_CHANNELS_JSON = "channels_json"
    private const val KEY_SELECTED_CHANNEL_ID = "selected_channel_id"
    private const val KEY_USAGE_MODEL_POOLS_JSON = "usage_model_pools_json"

    private const val KEY_DEFAULT_CHANNEL_ID = "default_channel_id"
    private const val KEY_DEFAULT_CHANNEL_NAME = "default_channel_name"
    private const val KEY_DEFAULT_CHANNEL_TYPE = "default_channel_type"
    private const val KEY_DEFAULT_CHANNEL_API_KEY = "default_channel_api_key"
    private const val KEY_DEFAULT_CHANNEL_BASE_URL = "default_channel_base_url"

    private const val KEY_CHANNEL_OCR_MODEL_ID = "default_channel_ocr_model_id"
    private const val KEY_CHANNEL_OCR_MODEL_LIST = "default_channel_ocr_model_list"
    private const val KEY_CHANNEL_FAST_MODEL_ID = "default_channel_fast_model_id"
    private const val KEY_CHANNEL_FAST_MODEL_LIST = "default_channel_fast_model_list"
    private const val KEY_CHANNEL_DEEP_MODEL_ID = "default_channel_deep_model_id"
    private const val KEY_CHANNEL_DEEP_MODEL_LIST = "default_channel_deep_model_list"

    private const val KEY_API_KEY_LEGACY = "api_key"
    private const val KEY_BASE_URL_LEGACY = "base_url"
    private const val KEY_OCR_BASE_URL_LEGACY = "ocr_base_url"
    private const val KEY_FAST_BASE_URL_LEGACY = "fast_base_url"
    private const val KEY_DEEP_BASE_URL_LEGACY = "deep_base_url"
    private const val KEY_OCR_MODEL_ID_LEGACY = "ocr_model_id"
    private const val KEY_OCR_MODEL_LIST_LEGACY = "ocr_model_list"
    private const val KEY_FAST_MODEL_ID_LEGACY = "fast_model_id"
    private const val KEY_FAST_MODEL_LIST_LEGACY = "fast_model_list"
    private const val KEY_DEEP_MODEL_ID_LEGACY = "deep_model_id"
    private const val KEY_DEEP_MODEL_LIST_LEGACY = "deep_model_list"

    private const val KEY_AUTO_DELETE_SCREENSHOT = "auto_delete_screenshot"

    private const val DEFAULT_CHANNEL_ID = "default_openai_channel"
    private const val DEFAULT_CHANNEL_NAME = "OpenAI Chat Completions"
    private const val DEFAULT_OCR_MODEL = "gpt-4o"
    private const val DEFAULT_FAST_MODEL = "gpt-4o-mini"
    private const val DEFAULT_DEEP_MODEL = "gpt-4o"
    private const val MODEL_LIST_SEPARATOR = "\n"

    private val DEFAULT_CHANNEL_TYPE = AIChannelType.OPENAI_CHAT_COMPLETIONS
    private val WHITESPACE_REGEX = "\\s+".toRegex()
    private val gson = Gson()
    private val channelsType = object : TypeToken<List<StoredChannel>>() {}.type
    private val usagePoolsType = object : TypeToken<List<StoredUsageModelPool>>() {}.type

    private data class StoredChannel(
        val id: String = "",
        val name: String = "",
        val type: String = "",
        val baseUrl: String = "",
        val apiKey: String = ""
    )

    private data class StoredUsageModelEntry(
        val channelId: String = "",
        val modelId: String = ""
    )

    private data class StoredUsageModelPool(
        val usage: String = "",
        val selectedChannelId: String = "",
        val selectedModelId: String = "",
        val entries: List<StoredUsageModelEntry> = emptyList()
    )

    private data class SettingsState(
        val channels: List<AIChannel>,
        val selectedChannelId: String,
        val usagePools: Map<AIUsage, AIUsageModelPool>
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun compactInput(value: String): String {
        return value.replace(WHITESPACE_REGEX, "")
    }

    private fun sanitizeModelId(value: String): String {
        return compactInput(value)
    }

    private fun sanitizeChannelId(value: String): String {
        return compactInput(value)
    }

    private fun normalizeBaseUrl(baseUrl: String, type: AIChannelType): String {
        return compactInput(baseUrl).trimEnd('/').ifBlank { type.defaultBaseUrl }
    }

    private fun sanitizeChannel(channel: AIChannel, fallbackName: String): AIChannel {
        return AIChannel(
            id = sanitizeChannelId(channel.id).ifBlank { UUID.randomUUID().toString() },
            name = channel.name.trim().ifBlank { fallbackName },
            type = channel.type,
            baseUrl = normalizeBaseUrl(channel.baseUrl, channel.type),
            apiKey = compactInput(channel.apiKey)
        )
    }

    private fun sanitizeEntry(entry: AIUsageModelEntry): AIUsageModelEntry {
        return AIUsageModelEntry(
            channelId = sanitizeChannelId(entry.channelId),
            modelId = sanitizeModelId(entry.modelId)
        )
    }

    private fun defaultModelForUsage(usage: AIUsage): String {
        return when (usage) {
            AIUsage.OCR -> DEFAULT_OCR_MODEL
            AIUsage.FAST -> DEFAULT_FAST_MODEL
            AIUsage.DEEP -> DEFAULT_DEEP_MODEL
        }
    }

    private fun emptyConfig(): AIConfig {
        return AIConfig(baseUrl = "", modelId = "", apiKey = "")
    }

    private fun placeholderChannel(): AIChannel {
        return AIChannel(
            id = DEFAULT_CHANNEL_ID,
            name = DEFAULT_CHANNEL_NAME,
            type = DEFAULT_CHANNEL_TYPE,
            baseUrl = DEFAULT_CHANNEL_TYPE.defaultBaseUrl,
            apiKey = ""
        )
    }

    private fun firstNonBlank(vararg values: String?): String? {
        values.forEach { value ->
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return null
    }

    private fun parseModelList(raw: String?, fallbackModel: String): List<String> {
        if (raw.isNullOrBlank()) {
            return listOf(fallbackModel.ifBlank { DEFAULT_FAST_MODEL })
        }
        return raw.split(MODEL_LIST_SEPARATOR)
            .map(::sanitizeModelId)
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf(fallbackModel.ifBlank { DEFAULT_FAST_MODEL }) }
    }

    private fun parseStoredChannels(raw: String?): List<StoredChannel> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<StoredChannel>>(raw, channelsType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseStoredUsagePools(raw: String?): List<StoredUsageModelPool> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<StoredUsageModelPool>>(raw, usagePoolsType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapStoredChannels(channels: List<StoredChannel>): List<AIChannel> {
        val seenIds = HashSet<String>()
        val normalized = mutableListOf<AIChannel>()
        channels.forEach { stored ->
            val type = AIChannelType.fromStorageValue(stored.type)
            val channel = sanitizeChannel(
                AIChannel(
                    id = stored.id,
                    name = stored.name,
                    type = type,
                    baseUrl = stored.baseUrl,
                    apiKey = stored.apiKey
                ),
                fallbackName = type.displayName
            )
            if (seenIds.add(channel.id)) {
                normalized.add(channel)
            }
        }
        return normalized
    }

    private fun normalizeEntries(
        entries: List<AIUsageModelEntry>,
        validChannelIds: Set<String>
    ): List<AIUsageModelEntry> {
        val seen = HashSet<String>()
        val normalized = mutableListOf<AIUsageModelEntry>()
        entries.forEach { rawEntry ->
            val entry = sanitizeEntry(rawEntry)
            if (!entry.isValid() || !validChannelIds.contains(entry.channelId)) {
                return@forEach
            }
            val key = "${entry.channelId}::${entry.modelId}"
            if (seen.add(key)) {
                normalized.add(entry)
            }
        }
        return normalized
    }

    private fun mapStoredUsagePools(
        pools: List<StoredUsageModelPool>,
        channels: List<AIChannel>,
        seedDefaults: Boolean
    ): Map<AIUsage, AIUsageModelPool> {
        val channelIds = channels.map { it.id }.toSet()
        val poolsByUsage = pools.associateBy { AIUsage.fromStorageValue(it.usage) }
        val firstChannelId = channels.firstOrNull()?.id.orEmpty()
        return AIUsage.values().associateWith { usage ->
            val storedPool = poolsByUsage[usage]
            val entries = when {
                storedPool != null -> normalizeEntries(
                    storedPool.entries.map {
                        AIUsageModelEntry(channelId = it.channelId, modelId = it.modelId)
                    },
                    channelIds
                )

                seedDefaults && firstChannelId.isNotBlank() -> listOf(
                    AIUsageModelEntry(
                        channelId = firstChannelId,
                        modelId = defaultModelForUsage(usage)
                    )
                )

                else -> emptyList()
            }
            val selectedFromStore = sanitizeEntry(
                AIUsageModelEntry(
                    channelId = storedPool?.selectedChannelId.orEmpty(),
                    modelId = storedPool?.selectedModelId.orEmpty()
                )
            )
            val selected = when {
                entries.isEmpty() -> null
                entries.contains(selectedFromStore) -> selectedFromStore
                else -> entries.first()
            }
            AIUsageModelPool(
                usage = usage,
                selectedEntry = selected,
                entries = entries
            )
        }
    }

    private fun serializeState(state: SettingsState): Pair<String, String> {
        val channelsJson = gson.toJson(
            state.channels.map {
                StoredChannel(
                    id = it.id,
                    name = it.name,
                    type = it.type.storageValue,
                    baseUrl = it.baseUrl,
                    apiKey = it.apiKey
                )
            }
        )
        val usagePoolsJson = gson.toJson(
            AIUsage.values().map { usage ->
                val pool = state.usagePools[usage]
                    ?: AIUsageModelPool(usage = usage, selectedEntry = null, entries = emptyList())
                StoredUsageModelPool(
                    usage = usage.storageValue,
                    selectedChannelId = pool.selectedEntry?.channelId.orEmpty(),
                    selectedModelId = pool.selectedEntry?.modelId.orEmpty(),
                    entries = pool.entries.map {
                        StoredUsageModelEntry(
                            channelId = it.channelId,
                            modelId = it.modelId
                        )
                    }
                )
            }
        )
        return channelsJson to usagePoolsJson
    }

    private fun persistState(context: Context, state: SettingsState) {
        val (channelsJson, usagePoolsJson) = serializeState(state)
        getPrefs(context).edit()
            .putString(KEY_CHANNELS_JSON, channelsJson)
            .putString(KEY_SELECTED_CHANNEL_ID, state.selectedChannelId)
            .putString(KEY_USAGE_MODEL_POOLS_JSON, usagePoolsJson)
            .apply()
    }

    private fun migrateLegacyState(prefs: SharedPreferences): SettingsState {
        val type = AIChannelType.fromStorageValue(
            prefs.getString(KEY_DEFAULT_CHANNEL_TYPE, DEFAULT_CHANNEL_TYPE.storageValue)
        )
        val channel = sanitizeChannel(
            AIChannel(
                id = prefs.getString(KEY_DEFAULT_CHANNEL_ID, DEFAULT_CHANNEL_ID).orEmpty(),
                name = prefs.getString(KEY_DEFAULT_CHANNEL_NAME, DEFAULT_CHANNEL_NAME).orEmpty(),
                type = type,
                baseUrl = firstNonBlank(
                    prefs.getString(KEY_DEFAULT_CHANNEL_BASE_URL, null),
                    prefs.getString(KEY_BASE_URL_LEGACY, null),
                    prefs.getString(KEY_OCR_BASE_URL_LEGACY, null),
                    prefs.getString(KEY_FAST_BASE_URL_LEGACY, null),
                    prefs.getString(KEY_DEEP_BASE_URL_LEGACY, null),
                    type.defaultBaseUrl
                ).orEmpty(),
                apiKey = firstNonBlank(
                    prefs.getString(KEY_DEFAULT_CHANNEL_API_KEY, null),
                    prefs.getString(KEY_API_KEY_LEGACY, null),
                    ""
                ).orEmpty()
            ),
            fallbackName = type.displayName
        )

        val pools = AIUsage.values().associateWith { usage ->
            val fallbackModel = defaultModelForUsage(usage)
            val selectedModel = sanitizeModelId(
                when (usage) {
                    AIUsage.OCR -> firstNonBlank(
                        prefs.getString(KEY_CHANNEL_OCR_MODEL_ID, null),
                        prefs.getString(KEY_OCR_MODEL_ID_LEGACY, null),
                        fallbackModel
                    )

                    AIUsage.FAST -> firstNonBlank(
                        prefs.getString(KEY_CHANNEL_FAST_MODEL_ID, null),
                        prefs.getString(KEY_FAST_MODEL_ID_LEGACY, null),
                        fallbackModel
                    )

                    AIUsage.DEEP -> firstNonBlank(
                        prefs.getString(KEY_CHANNEL_DEEP_MODEL_ID, null),
                        prefs.getString(KEY_DEEP_MODEL_ID_LEGACY, null),
                        fallbackModel
                    )
                }.orEmpty()
            ).ifBlank { fallbackModel }

            val rawList = when (usage) {
                AIUsage.OCR -> firstNonBlank(
                    prefs.getString(KEY_CHANNEL_OCR_MODEL_LIST, null),
                    prefs.getString(KEY_OCR_MODEL_LIST_LEGACY, null)
                )

                AIUsage.FAST -> firstNonBlank(
                    prefs.getString(KEY_CHANNEL_FAST_MODEL_LIST, null),
                    prefs.getString(KEY_FAST_MODEL_LIST_LEGACY, null)
                )

                AIUsage.DEEP -> firstNonBlank(
                    prefs.getString(KEY_CHANNEL_DEEP_MODEL_LIST, null),
                    prefs.getString(KEY_DEEP_MODEL_LIST_LEGACY, null)
                )
            }

            val entries = parseModelList(rawList, selectedModel).map {
                AIUsageModelEntry(channelId = channel.id, modelId = it)
            }
            val selectedEntry = entries.firstOrNull { it.modelId == selectedModel } ?: entries.firstOrNull()
            AIUsageModelPool(
                usage = usage,
                selectedEntry = selectedEntry,
                entries = entries
            )
        }

        return SettingsState(
            channels = listOf(channel),
            selectedChannelId = channel.id,
            usagePools = pools
        )
    }

    private fun loadState(context: Context): SettingsState {
        val prefs = getPrefs(context)
        val hasChannels = prefs.contains(KEY_CHANNELS_JSON)
        val hasPools = prefs.contains(KEY_USAGE_MODEL_POOLS_JSON)

        if (!hasChannels && !hasPools) {
            val migrated = migrateLegacyState(prefs)
            persistState(context, migrated)
            return migrated
        }

        val channels = mapStoredChannels(parseStoredChannels(prefs.getString(KEY_CHANNELS_JSON, null)))
        val usagePools = mapStoredUsagePools(
            pools = parseStoredUsagePools(prefs.getString(KEY_USAGE_MODEL_POOLS_JSON, null)),
            channels = channels,
            seedDefaults = false
        )
        val selectedChannelId = prefs.getString(KEY_SELECTED_CHANNEL_ID, "").orEmpty()
            .takeIf { id -> channels.any { it.id == id } }
            ?: channels.firstOrNull()?.id.orEmpty()
        val state = SettingsState(
            channels = channels,
            selectedChannelId = selectedChannelId,
            usagePools = usagePools
        )

        val (channelsJson, usagePoolsJson) = serializeState(state)
        val shouldPersist =
            !hasChannels ||
                !hasPools ||
                prefs.getString(KEY_SELECTED_CHANNEL_ID, "").orEmpty() != state.selectedChannelId ||
                prefs.getString(KEY_CHANNELS_JSON, null).orEmpty() != channelsJson ||
                prefs.getString(KEY_USAGE_MODEL_POOLS_JSON, null).orEmpty() != usagePoolsJson

        if (shouldPersist) {
            persistState(context, state)
        }
        return state
    }

    private fun updateState(context: Context, transform: (SettingsState) -> SettingsState): SettingsState {
        val nextState = transform(loadState(context))
        persistState(context, nextState)
        return nextState
    }

    private fun withSelectedOrPlaceholderChannel(context: Context): AIChannel {
        val state = loadState(context)
        return state.channels.firstOrNull { it.id == state.selectedChannelId }
            ?: state.channels.firstOrNull()
            ?: placeholderChannel()
    }

    private fun ensureEditableSelectedChannel(context: Context): AIChannel {
        val state = loadState(context)
        val selected = state.channels.firstOrNull { it.id == state.selectedChannelId }
            ?: state.channels.firstOrNull()
        if (selected != null) return selected
        return createChannel(context, DEFAULT_CHANNEL_TYPE)
    }

    private fun uniqueChannelName(existingNames: List<String>, type: AIChannelType): String {
        val trimmed = existingNames.map { it.trim() }.toSet()
        if (!trimmed.contains(type.displayName)) {
            return type.displayName
        }
        var index = 2
        while (true) {
            val candidate = "${type.displayName} $index"
            if (!trimmed.contains(candidate)) {
                return candidate
            }
            index++
        }
    }

    fun getChannels(context: Context): List<AIChannel> {
        return loadState(context).channels
    }

    fun getChannels(type: AIChannelType, context: Context): List<AIChannel> {
        return getChannels(context).filter { it.type == type }
    }

    fun getChannelById(context: Context, channelId: String): AIChannel? {
        val normalizedId = sanitizeChannelId(channelId)
        return loadState(context).channels.firstOrNull { it.id == normalizedId }
    }

    fun getSelectedChannelId(context: Context): String {
        return loadState(context).selectedChannelId
    }

    fun setSelectedChannelId(context: Context, channelId: String) {
        val normalizedId = sanitizeChannelId(channelId)
        updateState(context) { state ->
            val selected = state.channels.firstOrNull { it.id == normalizedId }?.id
                ?: state.channels.firstOrNull()?.id.orEmpty()
            state.copy(selectedChannelId = selected)
        }
    }

    fun createChannel(
        context: Context,
        type: AIChannelType = DEFAULT_CHANNEL_TYPE,
        preferredName: String? = null
    ): AIChannel {
        lateinit var created: AIChannel
        updateState(context) { state ->
            val name = preferredName?.trim().takeUnless { it.isNullOrBlank() }
                ?: uniqueChannelName(state.channels.map { it.name }, type)
            created = sanitizeChannel(
                AIChannel(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = type,
                    baseUrl = type.defaultBaseUrl,
                    apiKey = ""
                ),
                fallbackName = type.displayName
            )
            state.copy(
                channels = state.channels + created,
                selectedChannelId = created.id
            )
        }
        return created
    }

    fun upsertChannel(context: Context, channel: AIChannel): AIChannel {
        val sanitized = sanitizeChannel(channel, fallbackName = channel.type.displayName)
        updateState(context) { state ->
            val index = state.channels.indexOfFirst { it.id == sanitized.id }
            val nextChannels = state.channels.toMutableList()
            if (index >= 0) {
                nextChannels[index] = sanitized
            } else {
                nextChannels.add(sanitized)
            }
            state.copy(
                channels = nextChannels,
                selectedChannelId = sanitized.id
            )
        }
        return sanitized
    }

    fun deleteChannel(context: Context, channelId: String) {
        val normalizedId = sanitizeChannelId(channelId)
        updateState(context) { state ->
            val nextChannels = state.channels.filter { it.id != normalizedId }
            val validIds = nextChannels.map { it.id }.toSet()
            val nextPools = state.usagePools.mapValues { (_, pool) ->
                val entries = normalizeEntries(pool.entries, validIds)
                val selected = pool.selectedEntry
                    ?.let(::sanitizeEntry)
                    ?.takeIf { entries.contains(it) }
                    ?: entries.firstOrNull()
                pool.copy(selectedEntry = selected, entries = entries)
            }
            val selectedId = state.selectedChannelId
                .takeIf { id -> nextChannels.any { it.id == id } }
                ?: nextChannels.firstOrNull()?.id.orEmpty()
            state.copy(
                channels = nextChannels,
                selectedChannelId = selectedId,
                usagePools = nextPools
            )
        }
    }

    fun getSelectedChannel(context: Context): AIChannel {
        return withSelectedOrPlaceholderChannel(context)
    }

    fun getDefaultChannel(context: Context): AIChannel {
        return getSelectedChannel(context)
    }

    fun saveDefaultChannel(context: Context, channel: AIChannel) {
        upsertChannel(context, channel)
    }

    fun getApiKey(context: Context): String {
        return withSelectedOrPlaceholderChannel(context).apiKey
    }

    fun saveApiKey(context: Context, apiKey: String) {
        val channel = ensureEditableSelectedChannel(context)
        upsertChannel(context, channel.copy(apiKey = apiKey))
    }

    fun getBaseUrl(context: Context): String {
        return withSelectedOrPlaceholderChannel(context).baseUrl
    }

    fun saveBaseUrl(context: Context, baseUrl: String) {
        val channel = ensureEditableSelectedChannel(context)
        upsertChannel(context, channel.copy(baseUrl = baseUrl))
    }

    fun getUsageModelPool(context: Context, usage: AIUsage): AIUsageModelPool {
        return loadState(context).usagePools[usage]
            ?: AIUsageModelPool(usage = usage, selectedEntry = null, entries = emptyList())
    }

    fun getResolvedUsageModels(context: Context, usage: AIUsage): List<ResolvedUsageModelEntry> {
        val state = loadState(context)
        val pool = state.usagePools[usage] ?: return emptyList()
        val channelsById = state.channels.associateBy { it.id }
        return pool.entries.mapNotNull { entry ->
            val channel = channelsById[entry.channelId] ?: return@mapNotNull null
            ResolvedUsageModelEntry(
                usage = usage,
                channel = channel,
                modelId = entry.modelId,
                isSelected = pool.selectedEntry == entry
            )
        }
    }

    fun getSelectedResolvedUsageModel(context: Context, usage: AIUsage): ResolvedUsageModelEntry? {
        return getResolvedUsageModels(context, usage).firstOrNull { it.isSelected }
            ?: getResolvedUsageModels(context, usage).firstOrNull()
    }

    fun resolveUsageModelEntry(
        context: Context,
        usage: AIUsage,
        entry: AIUsageModelEntry
    ): ResolvedUsageModelEntry? {
        val normalizedEntry = sanitizeEntry(entry)
        return getResolvedUsageModels(context, usage).firstOrNull { it.entry == normalizedEntry }
    }

    fun setUsageModelEntries(
        context: Context,
        usage: AIUsage,
        entries: List<AIUsageModelEntry>,
        selectedEntry: AIUsageModelEntry? = null
    ) {
        updateState(context) { state ->
            val validChannelIds = state.channels.map { it.id }.toSet()
            val normalizedEntries = normalizeEntries(entries, validChannelIds)
            val currentPool = state.usagePools[usage]
                ?: AIUsageModelPool(usage = usage, selectedEntry = null, entries = emptyList())
            val preferredSelected = selectedEntry?.let(::sanitizeEntry)
                ?: currentPool.selectedEntry?.let(::sanitizeEntry)
            val nextSelected = when {
                normalizedEntries.isEmpty() -> null
                preferredSelected != null && normalizedEntries.contains(preferredSelected) -> preferredSelected
                else -> normalizedEntries.first()
            }
            state.copy(
                usagePools = state.usagePools + mapOf(
                    usage to currentPool.copy(
                        selectedEntry = nextSelected,
                        entries = normalizedEntries
                    )
                )
            )
        }
    }

    fun addUsageModelEntry(
        context: Context,
        usage: AIUsage,
        entry: AIUsageModelEntry,
        selectAfterAdd: Boolean = false
    ): Boolean {
        val normalizedEntry = sanitizeEntry(entry)
        if (!normalizedEntry.isValid()) return false
        val currentPool = getUsageModelPool(context, usage)
        if (currentPool.entries.contains(normalizedEntry)) {
            if (selectAfterAdd) {
                setSelectedUsageModel(context, usage, normalizedEntry)
            }
            return false
        }
        val entries = currentPool.entries + normalizedEntry
        val selected = when {
            selectAfterAdd -> normalizedEntry
            currentPool.selectedEntry != null -> currentPool.selectedEntry
            else -> normalizedEntry
        }
        setUsageModelEntries(context, usage, entries, selected)
        return true
    }

    fun removeUsageModelEntry(context: Context, usage: AIUsage, entry: AIUsageModelEntry) {
        val normalizedEntry = sanitizeEntry(entry)
        val currentPool = getUsageModelPool(context, usage)
        val nextEntries = currentPool.entries.filter { it != normalizedEntry }
        val nextSelected = currentPool.selectedEntry
            ?.takeIf { it != normalizedEntry }
            ?.takeIf { nextEntries.contains(it) }
            ?: nextEntries.firstOrNull()
        setUsageModelEntries(context, usage, nextEntries, nextSelected)
    }

    fun setSelectedUsageModel(context: Context, usage: AIUsage, entry: AIUsageModelEntry) {
        val normalizedEntry = sanitizeEntry(entry)
        if (!normalizedEntry.isValid()) return
        val currentPool = getUsageModelPool(context, usage)
        val nextEntries = mutableListOf<AIUsageModelEntry>().apply {
            add(normalizedEntry)
            addAll(currentPool.entries.filter { it != normalizedEntry })
        }
        setUsageModelEntries(context, usage, nextEntries, normalizedEntry)
    }

    fun getUsageConfig(context: Context, usage: AIUsage): AIConfig {
        return getUsageFallbackConfigs(context, usage).firstOrNull() ?: emptyConfig()
    }

    fun getUsageFallbackConfigs(context: Context, usage: AIUsage): List<AIConfig> {
        return getResolvedUsageModels(context, usage)
            .filter { it.channel.isConfigured() && it.modelId.isNotBlank() }
            .map { it.toConfig() }
    }

    fun hasConfiguredChannels(context: Context): Boolean {
        return getChannels(context).any { it.isConfigured() }
    }

    fun hasReadyUsage(context: Context, usage: AIUsage): Boolean {
        return getResolvedUsageModels(context, usage).any { resolved ->
            resolved.channel.isConfigured() && resolved.modelId.isNotBlank()
        }
    }

    fun getOCRConfig(context: Context): AIConfig {
        return getUsageConfig(context, AIUsage.OCR)
    }

    fun saveOCRConfig(context: Context, modelId: String) {
        val channel = ensureEditableSelectedChannel(context)
        addUsageModelEntry(
            context,
            AIUsage.OCR,
            AIUsageModelEntry(channelId = channel.id, modelId = modelId),
            selectAfterAdd = true
        )
    }

    fun getOCRModelList(context: Context): List<String> {
        return getResolvedUsageModels(context, AIUsage.OCR).map { it.modelId }
    }

    fun saveOCRModelList(context: Context, modelIds: List<String>) {
        val channel = ensureEditableSelectedChannel(context)
        setUsageModelEntries(
            context,
            AIUsage.OCR,
            modelIds.map { AIUsageModelEntry(channelId = channel.id, modelId = it) }
        )
    }

    fun getSelectedOCRModel(context: Context): String {
        return getSelectedResolvedUsageModel(context, AIUsage.OCR)?.modelId.orEmpty()
    }

    fun setSelectedOCRModel(context: Context, modelId: String) {
        val channel = ensureEditableSelectedChannel(context)
        setSelectedUsageModel(
            context,
            AIUsage.OCR,
            AIUsageModelEntry(channelId = channel.id, modelId = modelId)
        )
    }

    fun getFastConfig(context: Context): AIConfig {
        return getUsageConfig(context, AIUsage.FAST)
    }

    fun saveFastConfig(context: Context, modelId: String) {
        val channel = ensureEditableSelectedChannel(context)
        addUsageModelEntry(
            context,
            AIUsage.FAST,
            AIUsageModelEntry(channelId = channel.id, modelId = modelId),
            selectAfterAdd = true
        )
    }

    fun getFastModelList(context: Context): List<String> {
        return getResolvedUsageModels(context, AIUsage.FAST).map { it.modelId }
    }

    fun saveFastModelList(context: Context, modelIds: List<String>) {
        val channel = ensureEditableSelectedChannel(context)
        setUsageModelEntries(
            context,
            AIUsage.FAST,
            modelIds.map { AIUsageModelEntry(channelId = channel.id, modelId = it) }
        )
    }

    fun getSelectedFastModel(context: Context): String {
        return getSelectedResolvedUsageModel(context, AIUsage.FAST)?.modelId.orEmpty()
    }

    fun setSelectedFastModel(context: Context, modelId: String) {
        val channel = ensureEditableSelectedChannel(context)
        setSelectedUsageModel(
            context,
            AIUsage.FAST,
            AIUsageModelEntry(channelId = channel.id, modelId = modelId)
        )
    }

    fun getDeepConfig(context: Context): AIConfig {
        return getUsageConfig(context, AIUsage.DEEP)
    }

    fun saveDeepConfig(context: Context, modelId: String) {
        val channel = ensureEditableSelectedChannel(context)
        addUsageModelEntry(
            context,
            AIUsage.DEEP,
            AIUsageModelEntry(channelId = channel.id, modelId = modelId),
            selectAfterAdd = true
        )
    }

    fun getDeepModelList(context: Context): List<String> {
        return getResolvedUsageModels(context, AIUsage.DEEP).map { it.modelId }
    }

    fun saveDeepModelList(context: Context, modelIds: List<String>) {
        val channel = ensureEditableSelectedChannel(context)
        setUsageModelEntries(
            context,
            AIUsage.DEEP,
            modelIds.map { AIUsageModelEntry(channelId = channel.id, modelId = it) }
        )
    }

    fun getSelectedDeepModel(context: Context): String {
        return getSelectedResolvedUsageModel(context, AIUsage.DEEP)?.modelId.orEmpty()
    }

    fun setSelectedDeepModel(context: Context, modelId: String) {
        val channel = ensureEditableSelectedChannel(context)
        setSelectedUsageModel(
            context,
            AIUsage.DEEP,
            AIUsageModelEntry(channelId = channel.id, modelId = modelId)
        )
    }

    fun isAutoDeleteScreenshot(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_DELETE_SCREENSHOT, true)
    }

    fun setAutoDeleteScreenshot(context: Context, autoDelete: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_DELETE_SCREENSHOT, autoDelete).apply()
    }
}
