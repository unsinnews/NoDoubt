package com.nodoubt.core.ai

import com.google.gson.JsonObject
import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol

internal object CacheParameters {
    fun apply(body: JsonObject, endpoint: ModelEndpoint) {
        val retention = endpoint.parameters.text("cacheRetention") ?: return
        if (retention == "none") return
        val long = retention == "long" && endpoint.compat.flag("supportsLongCacheRetention") != false
        val anthropic = endpoint.protocol == Protocol.ANTHROPIC_MESSAGES || endpoint.compat.text("cacheControlFormat") == "anthropic"
        if (anthropic) {
            val marker = jsonObject { addProperty("type", "ephemeral"); if (long) addProperty("ttl", "1h") }
            if (body.get("system")?.isJsonPrimitive == true) {
                body.add("system", jsonArray(listOf(jsonObject {
                    addProperty("type", "text"); addProperty("text", body.text("system")); add("cache_control", marker.deepCopy())
                })))
            }
            body.array("messages").lastOrNull()?.objectOrNull()?.let { message ->
                message.array("content").lastOrNull()?.objectOrNull()?.add("cache_control", marker.deepCopy())
            }
            if (endpoint.compat.flag("supportsCacheControlOnTools") != false) {
                body.array("tools").lastOrNull()?.objectOrNull()?.add("cache_control", marker.deepCopy())
            }
        } else if (endpoint.protocol in setOf(Protocol.OPENAI_COMPLETIONS, Protocol.OPENAI_RESPONSES)) {
            endpoint.parameters.text("sessionId")?.let { body.addProperty("prompt_cache_key", it) }
            if (long) {
                if (endpoint.compat.flag("supportsExplicitPromptCacheMode") == true) body.putPath("prompt_cache_options.ttl", "30m".json())
                else body.addProperty("prompt_cache_retention", "24h")
            }
        }
    }
}
