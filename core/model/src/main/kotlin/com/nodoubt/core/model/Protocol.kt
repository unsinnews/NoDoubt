package com.nodoubt.core.model

enum class Protocol(val id: String, val label: String, val defaultBaseUrl: String) {
    OPENAI_COMPLETIONS("openai-completions", "OpenAI Chat Completions", "https://api.openai.com/v1"),
    OPENAI_RESPONSES("openai-responses", "OpenAI Responses", "https://api.openai.com/v1"),
    ANTHROPIC_MESSAGES("anthropic-messages", "Anthropic Messages", "https://api.anthropic.com/v1"),
    GOOGLE_GENERATIVE_AI("google-generative-ai", "Gemini", "https://generativelanguage.googleapis.com/v1beta");

    companion object {
        fun fromId(id: String): Protocol = entries.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("不支持的协议：$id")
    }
}

enum class Usage(val label: String) { OCR("截图识题"), FAST("极速回答"), DEEP("深度回答") }
enum class ParameterMode { INHERIT, VALUE, OMIT }
enum class ThemeFamily { PICOVE, VERCEL, APPLE }
enum class Appearance { SYSTEM, LIGHT, DARK }
