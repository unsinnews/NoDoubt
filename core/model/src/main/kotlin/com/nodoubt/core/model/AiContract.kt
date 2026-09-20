package com.nodoubt.core.model

sealed interface ContentPart {
    data class Text(val text: String) : ContentPart
    data class Image(val base64: String, val mimeType: String = "image/jpeg") : ContentPart
}

data class AiMessage(val role: String, val content: List<ContentPart>) {
    constructor(role: String, text: String) : this(role, listOf(ContentPart.Text(text)))
}

data class AiRequest(
    val endpoint: ModelEndpoint,
    val messages: List<AiMessage>,
    val system: String = ""
)

sealed interface AiEvent {
    data class TextDelta(val text: String) : AiEvent
    data class ThinkingDelta(val text: String) : AiEvent
    data class Tokens(val input: Int = 0, val output: Int = 0) : AiEvent
    data object Complete : AiEvent
}
