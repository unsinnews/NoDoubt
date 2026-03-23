package com.nodoubt.app.data

enum class AIChannelType(
    val storageValue: String,
    val displayName: String,
    val defaultBaseUrl: String
) {
    OPENAI_CHAT_COMPLETIONS(
        storageValue = "openai_chat_completions",
        displayName = "OpenAI Chat Completions",
        defaultBaseUrl = "https://api.openai.com/v1"
    );

    companion object {
        fun fromStorageValue(value: String?): AIChannelType {
            return values().firstOrNull { it.storageValue == value } ?: OPENAI_CHAT_COMPLETIONS
        }
    }
}

data class AIChannel(
    val id: String,
    val name: String,
    val type: AIChannelType,
    val baseUrl: String,
    val apiKey: String = ""
) {
    fun toConfig(modelId: String): AIConfig {
        return AIConfig(
            baseUrl = baseUrl,
            modelId = modelId,
            apiKey = apiKey
        )
    }
}
