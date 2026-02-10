package com.nodoubt.app.data

import android.content.Context
import android.content.SharedPreferences

object AISettings {
    private const val PREFS_NAME = "ai_settings"

    // API Key
    private const val KEY_API_KEY = "api_key"

    // OCR AI
    private const val KEY_OCR_BASE_URL = "ocr_base_url"
    private const val KEY_OCR_MODEL_ID = "ocr_model_id"

    // Fast Mode AI
    private const val KEY_FAST_BASE_URL = "fast_base_url"
    private const val KEY_FAST_MODEL_ID = "fast_model_id"
    private const val KEY_FAST_MODEL_LIST = "fast_model_list"

    // Deep Mode AI
    private const val KEY_DEEP_BASE_URL = "deep_base_url"
    private const val KEY_DEEP_MODEL_ID = "deep_model_id"
    private const val KEY_DEEP_MODEL_LIST = "deep_model_list"

    // Screenshot Settings
    private const val KEY_AUTO_DELETE_SCREENSHOT = "auto_delete_screenshot"

    // Default values
    private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    private const val DEFAULT_OCR_MODEL = "gpt-4o"
    private const val DEFAULT_FAST_MODEL = "gpt-4o-mini"
    private const val DEFAULT_DEEP_MODEL = "gpt-4o"
    private const val MODEL_LIST_SEPARATOR = "\n"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // API Key
    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }

    fun saveApiKey(context: Context, apiKey: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, apiKey).apply()
    }

    // OCR Config
    fun getOCRConfig(context: Context): AIConfig {
        val prefs = getPrefs(context)
        return AIConfig(
            baseUrl = prefs.getString(KEY_OCR_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
            modelId = prefs.getString(KEY_OCR_MODEL_ID, DEFAULT_OCR_MODEL) ?: DEFAULT_OCR_MODEL,
            apiKey = getApiKey(context)
        )
    }

    fun saveOCRConfig(context: Context, baseUrl: String, modelId: String) {
        getPrefs(context).edit()
            .putString(KEY_OCR_BASE_URL, baseUrl)
            .putString(KEY_OCR_MODEL_ID, modelId)
            .apply()
    }

    // Fast Mode Config
    fun getFastConfig(context: Context): AIConfig {
        val prefs = getPrefs(context)
        return AIConfig(
            baseUrl = prefs.getString(KEY_FAST_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
            modelId = getSelectedFastModel(context),
            apiKey = getApiKey(context)
        )
    }

    fun saveFastConfig(context: Context, baseUrl: String, modelId: String) {
        val prefs = getPrefs(context)
        val normalizedModel = modelId.trim().ifBlank { DEFAULT_FAST_MODEL }
        val mergedModels = normalizeModelList(getFastModelList(context) + normalizedModel, DEFAULT_FAST_MODEL)
        prefs.edit()
            .putString(KEY_FAST_BASE_URL, baseUrl)
            .putString(KEY_FAST_MODEL_ID, normalizedModel)
            .putString(KEY_FAST_MODEL_LIST, serializeModelList(mergedModels))
            .apply()
    }

    fun getFastModelList(context: Context): List<String> {
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_FAST_MODEL_LIST, null)
        val fallback = prefs.getString(KEY_FAST_MODEL_ID, DEFAULT_FAST_MODEL) ?: DEFAULT_FAST_MODEL
        return parseModelList(raw, fallback.ifBlank { DEFAULT_FAST_MODEL })
    }

    fun saveFastModelList(context: Context, modelIds: List<String>) {
        val prefs = getPrefs(context)
        val normalized = normalizeModelList(modelIds, DEFAULT_FAST_MODEL)
        val selected = prefs.getString(KEY_FAST_MODEL_ID, normalized.first()) ?: normalized.first()
        val finalSelected = if (normalized.contains(selected)) selected else normalized.first()
        prefs.edit()
            .putString(KEY_FAST_MODEL_LIST, serializeModelList(normalized))
            .putString(KEY_FAST_MODEL_ID, finalSelected)
            .apply()
    }

    fun getSelectedFastModel(context: Context): String {
        val models = getFastModelList(context)
        val selected = getPrefs(context).getString(KEY_FAST_MODEL_ID, models.first()) ?: models.first()
        return if (models.contains(selected)) selected else models.first()
    }

    fun setSelectedFastModel(context: Context, modelId: String) {
        val normalized = modelId.trim()
        if (normalized.isBlank()) return
        val models = getFastModelList(context)
        val finalSelected = if (models.contains(normalized)) normalized else models.first()
        getPrefs(context).edit().putString(KEY_FAST_MODEL_ID, finalSelected).apply()
    }

    // Deep Mode Config
    fun getDeepConfig(context: Context): AIConfig {
        val prefs = getPrefs(context)
        return AIConfig(
            baseUrl = prefs.getString(KEY_DEEP_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
            modelId = getSelectedDeepModel(context),
            apiKey = getApiKey(context)
        )
    }

    fun saveDeepConfig(context: Context, baseUrl: String, modelId: String) {
        val prefs = getPrefs(context)
        val normalizedModel = modelId.trim().ifBlank { DEFAULT_DEEP_MODEL }
        val mergedModels = normalizeModelList(getDeepModelList(context) + normalizedModel, DEFAULT_DEEP_MODEL)
        prefs.edit()
            .putString(KEY_DEEP_BASE_URL, baseUrl)
            .putString(KEY_DEEP_MODEL_ID, normalizedModel)
            .putString(KEY_DEEP_MODEL_LIST, serializeModelList(mergedModels))
            .apply()
    }

    fun getDeepModelList(context: Context): List<String> {
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_DEEP_MODEL_LIST, null)
        val fallback = prefs.getString(KEY_DEEP_MODEL_ID, DEFAULT_DEEP_MODEL) ?: DEFAULT_DEEP_MODEL
        return parseModelList(raw, fallback.ifBlank { DEFAULT_DEEP_MODEL })
    }

    fun saveDeepModelList(context: Context, modelIds: List<String>) {
        val prefs = getPrefs(context)
        val normalized = normalizeModelList(modelIds, DEFAULT_DEEP_MODEL)
        val selected = prefs.getString(KEY_DEEP_MODEL_ID, normalized.first()) ?: normalized.first()
        val finalSelected = if (normalized.contains(selected)) selected else normalized.first()
        prefs.edit()
            .putString(KEY_DEEP_MODEL_LIST, serializeModelList(normalized))
            .putString(KEY_DEEP_MODEL_ID, finalSelected)
            .apply()
    }

    fun getSelectedDeepModel(context: Context): String {
        val models = getDeepModelList(context)
        val selected = getPrefs(context).getString(KEY_DEEP_MODEL_ID, models.first()) ?: models.first()
        return if (models.contains(selected)) selected else models.first()
    }

    fun setSelectedDeepModel(context: Context, modelId: String) {
        val normalized = modelId.trim()
        if (normalized.isBlank()) return
        val models = getDeepModelList(context)
        val finalSelected = if (models.contains(normalized)) normalized else models.first()
        getPrefs(context).edit().putString(KEY_DEEP_MODEL_ID, finalSelected).apply()
    }

    // Auto Delete Screenshot (default: true)
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
        return modelIds.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(MODEL_LIST_SEPARATOR)
    }

    private fun normalizeModelList(modelIds: List<String>, fallbackModel: String): List<String> {
        val normalized = modelIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return if (normalized.isEmpty()) listOf(fallbackModel) else normalized
    }
}
