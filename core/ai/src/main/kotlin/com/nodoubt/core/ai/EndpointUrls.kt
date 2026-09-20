package com.nodoubt.core.ai

import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

internal object EndpointUrls {
    fun root(endpoint: ModelEndpoint): HttpUrl {
        val parsed = endpoint.baseUrl.trim().toHttpUrl()
        require(parsed.scheme == "http" || parsed.scheme == "https") { "仅支持 HTTP 或 HTTPS 地址" }
        val segments = parsed.pathSegments.filter(String::isNotEmpty).toMutableList()
        when {
            segments.takeLast(2) == listOf("chat", "completions") -> repeat(2) { segments.removeAt(segments.lastIndex) }
            segments.lastOrNull() in setOf("responses", "messages", "models") -> segments.removeAt(segments.lastIndex)
            segments.lastOrNull()?.contains(":streamGenerateContent") == true ||
                segments.lastOrNull()?.contains(":generateContent") == true -> {
                segments.removeAt(segments.lastIndex)
                if (segments.lastOrNull() == "models") segments.removeAt(segments.lastIndex)
            }
        }
        if (segments.isEmpty()) segments.add(if (endpoint.protocol == Protocol.GOOGLE_GENERATIVE_AI) "v1beta" else "v1")
        return parsed.newBuilder().encodedPath("/").apply { segments.forEach(::addPathSegment) }.build()
    }

    fun stream(endpoint: ModelEndpoint): HttpUrl = root(endpoint).newBuilder().apply {
        when (endpoint.protocol) {
            Protocol.OPENAI_COMPLETIONS -> addPathSegments("chat/completions")
            Protocol.OPENAI_RESPONSES -> addPathSegment("responses")
            Protocol.ANTHROPIC_MESSAGES -> addPathSegment("messages")
            Protocol.GOOGLE_GENERATIVE_AI -> {
                addPathSegment("models")
                addPathSegment(endpoint.modelId.removePrefix("models/") + ":streamGenerateContent")
                setQueryParameter("alt", "sse")
            }
        }
    }.build()

    fun models(endpoint: ModelEndpoint, page: String? = null): HttpUrl = root(endpoint).newBuilder().apply {
        addPathSegment("models")
        if (page != null) setQueryParameter(if (endpoint.protocol == Protocol.ANTHROPIC_MESSAGES) "after_id" else "pageToken", page)
    }.build()

    fun headers(builder: Request.Builder, endpoint: ModelEndpoint) {
        builder.header("Accept", "text/event-stream, application/json")
        if (endpoint.protocol == Protocol.ANTHROPIC_MESSAGES) builder.header("anthropic-version", "2023-06-01")
        if (endpoint.apiKey.isNotBlank()) {
            when (endpoint.protocol) {
                Protocol.OPENAI_COMPLETIONS, Protocol.OPENAI_RESPONSES -> builder.header("Authorization", "Bearer ${endpoint.apiKey}")
                Protocol.ANTHROPIC_MESSAGES -> builder.header("x-api-key", endpoint.apiKey)
                Protocol.GOOGLE_GENERATIVE_AI -> builder.header("x-goog-api-key", endpoint.apiKey)
            }
            if (endpoint.authHeader == true) builder.header("Authorization", "Bearer ${endpoint.apiKey}")
        }
        endpoint.headers.forEach { (name, value) -> builder.header(name, value) }
    }
}
