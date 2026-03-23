package com.nodoubt.app.data

import android.content.Context
import android.content.SharedPreferences

object AISettings {
    private const val PREFS_NAME = "ai_settings"

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
    private const val DEFAULT_CHANNEL_NAME = "默认渠道"
    private val DEFAULT_CHANNEL_TYPE = AIChannelType.OPENAI_CHAT_COMPLETIONS
    private const val DEFAULT_OCR_MODEL = "gpt-4o"
    private const val DEFAULT_FAST_MODEL = "gpt-4o-mini"
    private const val DEFAULT_DEEP_MODEL = "gpt-4o"
    private const val MODEL_LIST_SEPARATOR = "\n"
    private val WHITESPACE_REGEX = "\\s+".toRegex()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun compactInput(value: String): String {
        return value.replace(WHITESPACE_REGEX, "")
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        return compactInput(baseUrl).trimEnd('/')
    }

    private fun defaultChannelBaseUrl(): String {
        return DEFAULT_CHANNEL_TYPE.defaultBaseUrl
    }

    private fun migrateDefaultChannelIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        val editor = prefs.edit()
        var changed = false

        val storedChannelId = prefs.getString(KEY_DEFAULT_CHANNEL_ID, "") ?: ""
        if (storedChannelId.isBlank()) {
            editor.putString(KEY_DEFAULT_CHANNEL_ID, DEFAULT_CHANNEL_ID)
            changed = true
        }

        val storedChannelName = prefs.getString(KEY_DEFAULT_CHANNEL_NAME, "") ?: ""
        if (storedChannelName.isBlank()) {
            editor.putString(KEY_DEFAULT_CHANNEL_NAME, DEFAULT_CHANNEL_NAME)
            changed = true
        }

        val storedChannelType = prefs.getString(KEY_DEFAULT_CHANNEL_TYPE, "") ?: ""
        if (storedChannelType.isBlank()) {
            editor.putString(KEY_DEFAULT_CHANNEL_TYPE, DEFAULT_CHANNEL_TYPE.storageValue)
            changed = true
        }

        val rawApiKey = prefs.getString(KEY_DEFAULT_CHANNEL_API_KEY, null)
        val normalizedApiKey = compactInput(rawApiKey ?: "")
        when {
            prefs.contains(KEY_DEFAULT_CHANNEL_API_KEY) && rawApiKey != normalizedApiKey -> {
                editor.putString(KEY_DEFAULT_CHANNEL_API_KEY, normalizedApiKey)
                changed = true
            }

            !prefs.contains(KEY_DEFAULT_CHANNEL_API_KEY) -> {
                val migratedApiKey = compactInput(prefs.getString(KEY_API_KEY_LEGACY, "") ?: "")
                if (migratedApiKey.isNotBlank()) {
                    editor.putString(KEY_DEFAULT_CHANNEL_API_KEY, migratedApiKey)
                    changed = true
                }
            }
        }

        val rawBaseUrl = prefs.getString(KEY_DEFAULT_CHANNEL_BASE_URL, null)
        val normalizedBaseUrl = normalizeBaseUrl(rawBaseUrl ?: "")
        when {
            prefs.contains(KEY_DEFAULT_CHANNEL_BASE_URL) && rawBaseUrl != normalizedBaseUrl -> {
                editor.putString(
                    KEY_DEFAULT_CHANNEL_BASE_URL,
                    normalizedBaseUrl.ifBlank { defaultChannelBaseUrl() }
                )
                changed = true
            }

            !prefs.contains(KEY_DEFAULT_CHANNEL_BASE_URL) -> {
                val migratedBaseUrl = listOf(
                    prefs.getString(KEY_BASE_URL_LEGACY, "") ?: "",
                    prefs.getString(KEY_OCR_BASE_URL_LEGACY, "") ?: "",
                    prefs.getString(KEY_FAST_BASE_URL_LEGACY, "") ?: "",
                    prefs.getString(KEY_DEEP_BASE_URL_LEGACY, "") ?: ""
                ).map { normalizeBaseUrl(it) }
                    .firstOrNull { it.isNotBlank() }
                    ?: defaultChannelBaseUrl()
                editor.putString(KEY_DEFAULT_CHANNEL_BASE_URL, migratedBaseUrl)
                changed = true
            }
        }

        changed = migrateOcrModelsIfNeeded(prefs, editor) || changed
        changed = migrateFastModelsIfNeeded(prefs, editor) || changed
        changed = migrateDeepModelsIfNeeded(prefs, editor) || changed

        if (changed) {
            editor.apply()
        }
    }

    private fun migrateOcrModelsIfNeeded(
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor
    ): Boolean {
        val storedList = prefs.getString(KEY_CHANNEL_OCR_MODEL_LIST, null)
        val storedSelected = compactInput(prefs.getString(KEY_CHANNEL_OCR_MODEL_ID, "") ?: "")
        if (!storedList.isNullOrBlank() && storedSelected.isNotBlank()) {
            return false
        }

        val legacySelected = compactInput(
            prefs.getString(KEY_OCR_MODEL_ID_LEGACY, DEFAULT_OCR_MODEL) ?: DEFAULT_OCR_MODEL
        ).ifBlank { DEFAULT_OCR_MODEL }
        val migratedModels = parseModelList(
            raw = prefs.getString(KEY_OCR_MODEL_LIST_LEGACY, storedList),
            fallbackModel = legacySelected
        )
        val selectedModel = if (migratedModels.contains(storedSelected)) storedSelected else migratedModels.first()
        editor.putString(KEY_CHANNEL_OCR_MODEL_LIST, serializeModelList(migratedModels))
        editor.putString(KEY_CHANNEL_OCR_MODEL_ID, selectedModel)
        return true
    }

    private fun migrateFastModelsIfNeeded(
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor
    ): Boolean {
        val storedList = prefs.getString(KEY_CHANNEL_FAST_MODEL_LIST, null)
        val storedSelected = compactInput(prefs.getString(KEY_CHANNEL_FAST_MODEL_ID, "") ?: "")
        if (!storedList.isNullOrBlank() && storedSelected.isNotBlank()) {
            return false
        }

        val legacySelected = compactInput(
            prefs.getString(KEY_FAST_MODEL_ID_LEGACY, DEFAULT_FAST_MODEL) ?: DEFAULT_FAST_MODEL
        ).ifBlank { DEFAULT_FAST_MODEL }
        val migratedModels = parseModelList(
            raw = prefs.getString(KEY_FAST_MODEL_LIST_LEGACY, storedList),
            fallbackModel = legacySelected
        )
        val selectedModel = if (migratedModels.contains(storedSelected)) storedSelected else legacySelected
            .takeIf { migratedModels.contains(it) }
            ?: migratedModels.first()
        editor.putString(KEY_CHANNEL_FAST_MODEL_LIST, serializeModelList(migratedModels))
        editor.putString(KEY_CHANNEL_FAST_MODEL_ID, selectedModel)
        return true
    }

    private fun migrateDeepModelsIfNeeded(
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor
    ): Boolean {
        val storedList = prefs.getString(KEY_CHANNEL_DEEP_MODEL_LIST, null)
        val storedSelected = compactInput(prefs.getString(KEY_CHANNEL_DEEP_MODEL_ID, "") ?: "")
        if (!storedList.isNullOrBlank() && storedSelected.isNotBlank()) {
            return false
        }

        val legacySelected = compactInput(
            prefs.getString(KEY_DEEP_MODEL_ID_LEGACY, DEFAULT_DEEP_MODEL) ?: DEFAULT_DEEP_MODEL
        ).ifBlank { DEFAULT_DEEP_MODEL }
        val migratedModels = parseModelList(
            raw = prefs.getString(KEY_DEEP_MODEL_LIST_LEGACY, storedList),
            fallbackModel = legacySelected
        )
        val selectedModel = if (migratedModels.contains(storedSelected)) storedSelected else legacySelected
            .takeIf { migratedModels.contains(it) }
            ?: migratedModels.first()
        editor.putString(KEY_CHANNEL_DEEP_MODEL_LIST, serializeModelList(migratedModels))
        editor.putString(KEY_CHANNEL_DEEP_MODEL_ID, selectedModel)
        return true
    }

    fun getDefaultChannel(context: Context): AIChannel {
        migrateDefaultChannelIfNeeded(context)
        val prefs = getPrefs(context)
        val type = AIChannelType.fromStorageValue(prefs.getString(KEY_DEFAULT_CHANNEL_TYPE, null))
        return AIChannel(
            id = prefs.getString(KEY_DEFAULT_CHANNEL_ID, DEFAULT_CHANNEL_ID) ?: DEFAULT_CHANNEL_ID,
            name = prefs.getString(KEY_DEFAULT_CHANNEL_NAME, DEFAULT_CHANNEL_NAME) ?: DEFAULT_CHANNEL_NAME,
            type = type,
            baseUrl = normalizeBaseUrl(
                prefs.getString(KEY_DEFAULT_CHANNEL_BASE_URL, type.defaultBaseUrl) ?: type.defaultBaseUrl
            ).ifBlank { type.defaultBaseUrl },
            apiKey = compactInput(prefs.getString(KEY_DEFAULT_CHANNEL_API_KEY, "") ?: "")
        )
    }

    fun saveDefaultChannel(context: Context, channel: AIChannel) {
        val normalizedType = channel.type
        getPrefs(context).edit()
            .putString(KEY_DEFAULT_CHANNEL_ID, compactInput(channel.id).ifBlank { DEFAULT_CHANNEL_ID })
            .putString(KEY_DEFAULT_CHANNEL_NAME, channel.name.trim().ifBlank { DEFAULT_CHANNEL_NAME })
            .putString(KEY_DEFAULT_CHANNEL_TYPE, normalizedType.storageValue)
            .putString(KEY_DEFAULT_CHANNEL_API_KEY, compactInput(channel.apiKey))
            .putString(
                KEY_DEFAULT_CHANNEL_BASE_URL,
                normalizeBaseUrl(channel.baseUrl).ifBlank { normalizedType.defaultBaseUrl }
            )
            .apply()
    }

    fun getApiKey(context: Context): String {
        return getDefaultChannel(context).apiKey
    }

    fun saveApiKey(context: Context, apiKey: String) {
        val channel = getDefaultChannel(context)
        saveDefaultChannel(context, channel.copy(apiKey = apiKey))
    }

    fun getBaseUrl(context: Context): String {
        return getDefaultChannel(context).baseUrl
    }

    fun saveBaseUrl(context: Context, baseUrl: String) {
        val channel = getDefaultChannel(context)
        saveDefaultChannel(context, channel.copy(baseUrl = baseUrl))
    }

    fun getOCRConfig(context: Context): AIConfig {
        return getDefaultChannel(context).toConfig(getSelectedOCRModel(context))
    }

    fun saveOCRConfig(context: Context, modelId: String) {
        val normalizedModel = compactInput(modelId).ifBlank { DEFAULT_OCR_MODEL }
        val mergedModels = normalizeModelList(listOf(normalizedModel) + getOCRModelList(context), DEFAULT_OCR_MODEL)
        getPrefs(context).edit()
            .putString(KEY_CHANNEL_OCR_MODEL_ID, mergedModels.first())
            .putString(KEY_CHANNEL_OCR_MODEL_LIST, serializeModelList(mergedModels))
            .apply()
    }

    fun getOCRModelList(context: Context): List<String> {
        migrateDefaultChannelIfNeeded(context)
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_CHANNEL_OCR_MODEL_LIST, null)
        val fallback = compactInput(
            prefs.getString(KEY_CHANNEL_OCR_MODEL_ID, DEFAULT_OCR_MODEL) ?: DEFAULT_OCR_MODEL
        ).ifBlank { DEFAULT_OCR_MODEL }
        return parseModelList(raw, fallback)
    }

    fun saveOCRModelList(context: Context, modelIds: List<String>) {
        val normalized = normalizeModelList(modelIds, DEFAULT_OCR_MODEL)
        getPrefs(context).edit()
            .putString(KEY_CHANNEL_OCR_MODEL_LIST, serializeModelList(normalized))
            .putString(KEY_CHANNEL_OCR_MODEL_ID, normalized.first())
            .apply()
    }

    fun getSelectedOCRModel(context: Context): String {
        val models = getOCRModelList(context)
        val selectedRaw = getPrefs(context).getString(KEY_CHANNEL_OCR_MODEL_ID, models.first()) ?: models.first()
        val selected = compactInput(selectedRaw)
        return if (models.contains(selected)) selected else models.first()
    }

    fun setSelectedOCRModel(context: Context, modelId: String) {
        val normalized = compactInput(modelId)
        if (normalized.isBlank()) return
        val models = getOCRModelList(context)
        if (!models.contains(normalized)) return
        val reordered = mutableListOf(normalized).apply {
            addAll(models.filter { it != normalized })
        }
        saveOCRModelList(context, reordered)
    }

    fun getFastConfig(context: Context): AIConfig {
        return getDefaultChannel(context).toConfig(getSelectedFastModel(context))
    }

    fun saveFastConfig(context: Context, modelId: String) {
        val normalizedModel = compactInput(modelId).ifBlank { DEFAULT_FAST_MODEL }
        val mergedModels = normalizeModelList(getFastModelList(context) + normalizedModel, DEFAULT_FAST_MODEL)
        getPrefs(context).edit()
            .putString(KEY_CHANNEL_FAST_MODEL_ID, normalizedModel)
            .putString(KEY_CHANNEL_FAST_MODEL_LIST, serializeModelList(mergedModels))
            .apply()
    }

    fun getFastModelList(context: Context): List<String> {
        migrateDefaultChannelIfNeeded(context)
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_CHANNEL_FAST_MODEL_LIST, null)
        val fallback = compactInput(
            prefs.getString(KEY_CHANNEL_FAST_MODEL_ID, DEFAULT_FAST_MODEL) ?: DEFAULT_FAST_MODEL
        ).ifBlank { DEFAULT_FAST_MODEL }
        return parseModelList(raw, fallback)
    }

    fun saveFastModelList(context: Context, modelIds: List<String>) {
        val prefs = getPrefs(context)
        val normalized = normalizeModelList(modelIds, DEFAULT_FAST_MODEL)
        val selectedRaw = prefs.getString(KEY_CHANNEL_FAST_MODEL_ID, normalized.first()) ?: normalized.first()
        val selected = compactInput(selectedRaw)
        val finalSelected = if (normalized.contains(selected)) selected else normalized.first()
        prefs.edit()
            .putString(KEY_CHANNEL_FAST_MODEL_LIST, serializeModelList(normalized))
            .putString(KEY_CHANNEL_FAST_MODEL_ID, finalSelected)
            .apply()
    }

    fun getSelectedFastModel(context: Context): String {
        val models = getFastModelList(context)
        val selectedRaw = getPrefs(context).getString(KEY_CHANNEL_FAST_MODEL_ID, models.first()) ?: models.first()
        val selected = compactInput(selectedRaw)
        return if (models.contains(selected)) selected else models.first()
    }

    fun setSelectedFastModel(context: Context, modelId: String) {
        val normalized = compactInput(modelId)
        if (normalized.isBlank()) return
        val models = getFastModelList(context)
        val finalSelected = if (models.contains(normalized)) normalized else models.first()
        getPrefs(context).edit().putString(KEY_CHANNEL_FAST_MODEL_ID, finalSelected).apply()
    }

    fun getDeepConfig(context: Context): AIConfig {
        return getDefaultChannel(context).toConfig(getSelectedDeepModel(context))
    }

    fun saveDeepConfig(context: Context, modelId: String) {
        val normalizedModel = compactInput(modelId).ifBlank { DEFAULT_DEEP_MODEL }
        val mergedModels = normalizeModelList(getDeepModelList(context) + normalizedModel, DEFAULT_DEEP_MODEL)
        getPrefs(context).edit()
            .putString(KEY_CHANNEL_DEEP_MODEL_ID, normalizedModel)
            .putString(KEY_CHANNEL_DEEP_MODEL_LIST, serializeModelList(mergedModels))
            .apply()
    }

    fun getDeepModelList(context: Context): List<String> {
        migrateDefaultChannelIfNeeded(context)
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_CHANNEL_DEEP_MODEL_LIST, null)
        val fallback = compactInput(
            prefs.getString(KEY_CHANNEL_DEEP_MODEL_ID, DEFAULT_DEEP_MODEL) ?: DEFAULT_DEEP_MODEL
        ).ifBlank { DEFAULT_DEEP_MODEL }
        return parseModelList(raw, fallback)
    }

    fun saveDeepModelList(context: Context, modelIds: List<String>) {
        val prefs = getPrefs(context)
        val normalized = normalizeModelList(modelIds, DEFAULT_DEEP_MODEL)
        val selectedRaw = prefs.getString(KEY_CHANNEL_DEEP_MODEL_ID, normalized.first()) ?: normalized.first()
        val selected = compactInput(selectedRaw)
        val finalSelected = if (normalized.contains(selected)) selected else normalized.first()
        prefs.edit()
            .putString(KEY_CHANNEL_DEEP_MODEL_LIST, serializeModelList(normalized))
            .putString(KEY_CHANNEL_DEEP_MODEL_ID, finalSelected)
            .apply()
    }

    fun getSelectedDeepModel(context: Context): String {
        val models = getDeepModelList(context)
        val selectedRaw = getPrefs(context).getString(KEY_CHANNEL_DEEP_MODEL_ID, models.first()) ?: models.first()
        val selected = compactInput(selectedRaw)
        return if (models.contains(selected)) selected else models.first()
    }

    fun setSelectedDeepModel(context: Context, modelId: String) {
        val normalized = compactInput(modelId)
        if (normalized.isBlank()) return
        val models = getDeepModelList(context)
        val finalSelected = if (models.contains(normalized)) normalized else models.first()
        getPrefs(context).edit().putString(KEY_CHANNEL_DEEP_MODEL_ID, finalSelected).apply()
    }

    fun isAutoDeleteScreenshot(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_DELETE_SCREENSHOT, true)
    }

    fun setAutoDeleteScreenshot(context: Context, autoDelete: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_DELETE_SCREENSHOT, autoDelete).apply()
    }

    private fun parseModelList(raw: String?, fallbackModel: String): List<String> {
        if (raw.isNullOrBlank()) {
            return listOf(fallbackModel.ifBlank { DEFAULT_FAST_MODEL })
        }
        return normalizeModelList(raw.split(MODEL_LIST_SEPARATOR), fallbackModel)
    }

    private fun serializeModelList(modelIds: List<String>): String {
        return modelIds.map { compactInput(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(MODEL_LIST_SEPARATOR)
    }

    private fun normalizeModelList(modelIds: List<String>, fallbackModel: String): List<String> {
        val normalized = modelIds.map { compactInput(it) }.filter { it.isNotBlank() }.distinct()
        val fallback = compactInput(fallbackModel).ifBlank { DEFAULT_FAST_MODEL }
        return if (normalized.isEmpty()) listOf(fallback) else normalized
    }
}
