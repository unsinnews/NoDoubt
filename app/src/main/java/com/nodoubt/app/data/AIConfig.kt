package com.nodoubt.app.data

data class AIConfig(
    val baseUrl: String,
    val modelId: String,
    val apiKey: String = "",
    val channelId: String = "",
    val channelType: AIChannelType = AIChannelType.OPENAI_CHAT_COMPLETIONS
) {
    fun isValid(): Boolean {
        return baseUrl.isNotBlank() && modelId.isNotBlank()
    }
}
