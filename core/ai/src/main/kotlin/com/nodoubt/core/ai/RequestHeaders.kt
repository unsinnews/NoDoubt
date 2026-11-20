package com.nodoubt.core.ai

import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol
import okhttp3.Request

/** Protocol headers that depend on the final body: Anthropic betas and session affinity, as in pi. */
internal object RequestHeaders {
    fun apply(builder: Request.Builder, endpoint: ModelEndpoint, body: com.google.gson.JsonObject) {
        if (endpoint.protocol == Protocol.ANTHROPIC_MESSAGES) {
            val interleaved = endpoint.parameters.flag("interleavedThinking") != false &&
                body.obj("thinking")?.text("type") == "enabled"
            if (interleaved && endpoint.headers.keys.none { it.equals("anthropic-beta", true) }) {
                builder.header("anthropic-beta", "interleaved-thinking-2025-05-14")
            }
        }
        val session = endpoint.parameters.text("sessionId")?.takeIf { it.isNotBlank() } ?: return
        if (endpoint.parameters.text("cacheRetention") == "none") return
        val compat = endpoint.compat
        when (endpoint.protocol) {
            Protocol.OPENAI_COMPLETIONS -> {
                val resolved = CompletionsCompat.of(endpoint)
                if (!resolved.sendSessionAffinityHeaders) return
                if (resolved.sessionAffinityFormat == "openrouter") builder.header("x-session-id", session)
                else {
                    if (resolved.sessionAffinityFormat == "openai") builder.header("session_id", session)
                    builder.header("x-client-request-id", session)
                    builder.header("x-session-affinity", session)
                }
            }
            Protocol.OPENAI_RESPONSES -> {
                val format = compat.text("sessionAffinityFormat") ?: if (isOpenRouter(endpoint)) "openrouter" else "openai"
                if (format == "openrouter") builder.header("x-session-id", session)
                else {
                    if (format == "openai") builder.header("session-id", session)
                    builder.header("x-client-request-id", session)
                }
            }
            Protocol.ANTHROPIC_MESSAGES -> {
                if (compat.flag("sendSessionAffinityHeaders") ?: isOpenRouter(endpoint)) {
                    val openRouter = (compat.text("sessionAffinityFormat") ?: if (isOpenRouter(endpoint)) "openrouter" else null) == "openrouter"
                    builder.header(if (openRouter) "x-session-id" else "x-session-affinity", session)
                }
            }
            Protocol.GOOGLE_GENERATIVE_AI -> Unit
        }
    }
}
