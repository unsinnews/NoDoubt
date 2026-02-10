package com.nodoubt.app.data

data class AIConfig(
    val baseUrl: String,
    val modelId: String,
    val apiKey: String = ""
) {
    fun isValid(): Boolean {
        return baseUrl.isNotBlank() && modelId.isNotBlank()
    }
}
