package com.nodoubt.app.network

import android.text.InputType
import com.nodoubt.app.data.AIChannel
import com.nodoubt.app.data.AIChannelType
import com.nodoubt.app.data.AIConfig
import com.nodoubt.app.data.AIProviderFieldDef

interface AIChannelProvider {
    val type: AIChannelType
    val displayName: String
    val shortDescription: String
    val fields: List<AIProviderFieldDef>

    fun normalizeChannel(channel: AIChannel): AIChannel
    fun createClient(config: AIConfig): OpenAIClient
}

object AIProviderRegistry {
    private val openAIChatCompletionsProvider = object : AIChannelProvider {
        override val type: AIChannelType = AIChannelType.OPENAI_CHAT_COMPLETIONS
        override val displayName: String = type.displayName
        override val shortDescription: String =
            "兼容 OpenAI Chat Completions 的 API Key、Base URL 与模型访问。"
        override val fields: List<AIProviderFieldDef> = listOf(
            AIProviderFieldDef(
                key = "apiKey",
                label = "API Key",
                hint = "输入 OpenAI Chat Completions 的 API Key",
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                isSecret = true
            ),
            AIProviderFieldDef(
                key = "baseUrl",
                label = "Base URL",
                hint = "https://api.openai.com/v1",
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            )
        )

        override fun normalizeChannel(channel: AIChannel): AIChannel {
            val normalizedUrl = channel.baseUrl.trim().trimEnd('/').ifBlank { type.defaultBaseUrl }
            return channel.copy(
                type = type,
                name = channel.name.trim().ifBlank { displayName },
                baseUrl = normalizedUrl,
                apiKey = channel.apiKey.trim()
            )
        }

        override fun createClient(config: AIConfig): OpenAIClient {
            return OpenAIClient(config)
        }
    }

    fun getProvider(type: AIChannelType): AIChannelProvider {
        return when (type) {
            AIChannelType.OPENAI_CHAT_COMPLETIONS -> openAIChatCompletionsProvider
        }
    }

    fun getProvider(channel: AIChannel): AIChannelProvider {
        return getProvider(channel.type)
    }

    fun getProvider(config: AIConfig): AIChannelProvider {
        return getProvider(config.channelType)
    }

    fun getVisibleProviders(): List<AIChannelProvider> {
        return listOf(openAIChatCompletionsProvider)
    }
}
