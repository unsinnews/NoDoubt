package com.nodoubt.core.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException

class AiException(message: String, val status: Int? = null, val code: String? = null, cause: Throwable? = null) :
    IOException(message, cause)

internal fun responseError(body: String, status: Int? = null): AiException {
    val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
    val error = root?.obj("error") ?: root
    val message = error?.text("message") ?: root?.text("error") ?: body.take(1000).ifBlank { "服务端未返回错误详情" }
    return AiException(if (status != null) "HTTP $status：$message" else message, status, error?.text("code") ?: error?.text("type"))
}

internal fun checkProviderError(json: JsonObject) {
    if (json.has("error") && !json.get("error").isJsonNull) throw responseError(json.toString())
    if (json.text("type") == "error") throw responseError(json.toString())
}
