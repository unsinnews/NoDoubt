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

enum class AIUsage(
    val storageValue: String,
    val displayName: String,
    val shortDescription: String
) {
    OCR(
        storageValue = "ocr",
        displayName = "OCR 识别",
        shortDescription = "识别截图题目，优先使用支持视觉能力的模型。"
    ),
    FAST(
        storageValue = "fast",
        displayName = "极速模式",
        shortDescription = "共同展示并切换适合快速解题的模型。"
    ),
    DEEP(
        storageValue = "deep",
        displayName = "深度模式",
        shortDescription = "共同展示并切换适合复杂推理的模型。"
    );

    companion object {
        fun fromStorageValue(value: String?): AIUsage {
            return values().firstOrNull { it.storageValue == value } ?: FAST
        }
    }
}

data class AIUsageModelEntry(
    val channelId: String,
    val modelId: String
) {
    fun isValid(): Boolean {
        return channelId.isNotBlank() && modelId.isNotBlank()
    }
}

data class AIUsageModelPool(
    val usage: AIUsage,
    val selectedEntry: AIUsageModelEntry?,
    val entries: List<AIUsageModelEntry>
)

data class ResolvedUsageModelEntry(
    val usage: AIUsage,
    val channel: AIChannel,
    val modelId: String,
    val isSelected: Boolean
) {
    val entry: AIUsageModelEntry
        get() = AIUsageModelEntry(channelId = channel.id, modelId = modelId)

    fun toConfig(): AIConfig {
        return AIConfig(
            baseUrl = channel.baseUrl,
            modelId = modelId,
            apiKey = channel.apiKey,
            channelId = channel.id,
            channelType = channel.type
        )
    }

    fun displayTitle(): String {
        return "$modelId · ${channel.name}"
    }
}

data class AIProviderFieldDef(
    val key: String,
    val label: String,
    val hint: String,
    val inputType: Int,
    val isSecret: Boolean = false
)

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
            apiKey = apiKey,
            channelId = id,
            channelType = type
        )
    }

    fun isConfigured(): Boolean {
        return apiKey.isNotBlank() && baseUrl.isNotBlank()
    }
}
