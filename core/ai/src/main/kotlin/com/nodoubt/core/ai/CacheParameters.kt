package com.nodoubt.core.ai

import com.google.gson.JsonObject
import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol

/** Prompt caching as in pi: short retention by default, "none" opts out. Gemini has no request-side cache. */
internal object CacheParameters {
    private const val CACHE_KEY_MAX_LENGTH = 64

    fun apply(body: JsonObject, endpoint: ModelEndpoint) {
        val retention = endpoint.parameters.text("cacheRetention") ?: "short"
        val session = endpoint.parameters.text("sessionId")?.takeIf { it.isNotBlank() }?.take(CACHE_KEY_MAX_LENGTH)
        when (endpoint.protocol) {
            Protocol.ANTHROPIC_MESSAGES -> if (retention != "none") {
                val long = retention == "long" && endpoint.compat.flag("supportsLongCacheRetention") != false
                anthropicMarkers(body, marker(long), textOnly = false)
            }
            Protocol.OPENAI_COMPLETIONS -> completions(body, endpoint, retention, session)
            Protocol.OPENAI_RESPONSES -> responses(body, endpoint, retention, session)
            Protocol.GOOGLE_GENERATIVE_AI -> Unit
        }
    }

    private fun completions(body: JsonObject, endpoint: ModelEndpoint, retention: String, session: String?) {
        if (retention == "none") return
        val compat = CompletionsCompat.of(endpoint)
        val long = retention == "long" && compat.supportsLongCacheRetention
        val sendKey = compat.promptCacheKeyMode == "enabled" || (compat.promptCacheKeyMode == "auto" &&
            ("api.openai.com" in endpoint.baseUrl || long))
        if (sendKey && session != null) body.addProperty("prompt_cache_key", session)
        if (long) body.addProperty("prompt_cache_retention", "24h")
        if (compat.cacheControlFormat == "anthropic") anthropicMarkers(body, marker(long), textOnly = true)
    }

    private fun responses(body: JsonObject, endpoint: ModelEndpoint, retention: String, session: String?) {
        val compat = endpoint.compat
        val explicit = compat.flag("supportsExplicitPromptCacheMode") == true
        val long = retention == "long" && compat.flag("supportsLongCacheRetention") != false
        if (retention != "none" && compat.promptCacheKeyMode() != "disabled" && session != null) {
            body.addProperty("prompt_cache_key", session)
        }
        if (long && !explicit) body.addProperty("prompt_cache_retention", "24h")
        if (explicit) when {
            retention == "none" -> body.putPath("prompt_cache_options.mode", "explicit".json())
            long -> body.putPath("prompt_cache_options.ttl", "30m".json())
        }
    }

    private fun marker(long: Boolean) = jsonObject { addProperty("type", "ephemeral"); if (long) addProperty("ttl", "1h") }

    /** Marks the system prompt and the last user message, matching pi's cache breakpoints. */
    private fun anthropicMarkers(body: JsonObject, marker: JsonObject, textOnly: Boolean) {
        body.text("system")?.let { system ->
            body.add("system", jsonArray(listOf(textBlock(system, marker))))
        }
        val messages = body.array("messages")
        messages.lastObject { it.text("role") in setOf("system", "developer") }?.takeIf { textOnly }?.let { mark(it, marker, true) }
        messages.lastObject { it.text("role") == "user" }?.let { mark(it, marker, textOnly) }
    }

    private fun mark(message: JsonObject, marker: JsonObject, textOnly: Boolean) {
        message.text("content")?.let { text ->
            if (text.isNotEmpty()) message.add("content", jsonArray(listOf(textBlock(text, marker))))
            return
        }
        val target = message.array("content").lastObject { !textOnly || it.text("type") == "text" } ?: return
        target.add("cache_control", marker.deepCopy())
    }

    private fun textBlock(text: String, marker: JsonObject) = jsonObject {
        addProperty("type", "text"); addProperty("text", text); add("cache_control", marker.deepCopy())
    }
}
