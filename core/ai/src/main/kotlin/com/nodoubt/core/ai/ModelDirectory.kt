package com.nodoubt.core.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nodoubt.core.model.ModelConfig
import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class ModelDirectory(private val client: OkHttpClient) {
    suspend fun list(endpoint: ModelEndpoint, catalogUrl: String?): List<ModelConfig> {
        val result = linkedMapOf<String, ModelConfig>()
        val custom = catalogUrl?.takeIf { it.isNotBlank() }?.toHttpUrl()
        val origin = endpoint.baseUrl.toHttpUrl()
        val sameOrigin = custom == null || (custom.scheme == origin.scheme && custom.host == origin.host && custom.port == origin.port)
        var page: String? = null
        val visited = mutableSetOf<String>()
        repeat(100) {
            val url = custom?.newBuilder()?.apply {
                if (page != null) setQueryParameter(if (endpoint.protocol == Protocol.ANTHROPIC_MESSAGES) "after_id" else "pageToken", page)
            }?.build() ?: EndpointUrls.models(endpoint, page)
            val builder = Request.Builder().url(url)
            if (sameOrigin) EndpointUrls.headers(builder, endpoint)
            builder.header("Accept", "application/json")
            val root = get(builder.build())
            checkProviderError(root)
            val models = if (root.has("models")) root.array("models") else root.array("data")
            models.forEach { element ->
                val model = element.objectOrNull() ?: return@forEach
                val rawId = model.text("id") ?: model.text("name") ?: return@forEach
                val id = if (endpoint.protocol == Protocol.GOOGLE_GENERATIVE_AI) rawId.removePrefix("models/") else rawId
                result[id] = ModelConfig(
                    id = id,
                    name = model.text("display_name") ?: model.text("displayName") ?: id,
                    contextWindow = model.number("inputTokenLimit"),
                    maxTokens = model.number("outputTokenLimit")
                )
            }
            page = if (endpoint.protocol == Protocol.ANTHROPIC_MESSAGES && root.flag("has_more") == true) {
                root.text("last_id") ?: models.lastOrNull()?.objectOrNull()?.text("id")
            } else root.text("nextPageToken")
            if (page.isNullOrEmpty()) return result.values.toList()
            if (!visited.add(page!!)) throw AiException("模型目录返回了重复的分页标记")
        }
        throw AiException("模型列表过大，请手动添加")
    }

    private suspend fun get(request: Request): JsonObject = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(AiException(error.message ?: "网络请求失败", cause = error))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val root = response.use {
                        val body = it.body?.string().orEmpty()
                        if (!it.isSuccessful) throw responseError(body, it.code)
                        runCatching {
                            val parsed = JsonParser.parseString(body)
                            if (parsed.isJsonArray) jsonObject { add("data", parsed) } else parsed.asJsonObject
                        }
                            .getOrElse { cause -> throw AiException("模型目录不是有效的 JSON", cause = cause) }
                    }
                    if (continuation.isActive) continuation.resume(root)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
}
