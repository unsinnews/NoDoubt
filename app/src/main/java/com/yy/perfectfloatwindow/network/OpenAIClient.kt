package com.yy.perfectfloatwindow.network

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yy.perfectfloatwindow.data.AIConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAIClient(private val config: AIConfig) {

    private val reasoningFieldKeys = listOf(
        "reasoning_content",
        "reasoning",
        "reasoningContent",
        "reasoning_text",
        "reasoningText",
        "reasoning_output",
        "reasoningOutput",
        "reasoning_details",
        "chain_of_thought",
        "chainOfThought",
        "thoughts",
        "thinking",
        "thinking_content",
        "thinkingContent",
        "thought",
        "analysis"
    )

    private val reasoningBlockTypes = setOf(
        "reasoning",
        "reasoning_content",
        "reasoning_text",
        "reasoning_delta",
        "reasoning_delta_text",
        "reasoning_summary",
        "thinking",
        "thinking_content",
        "thinking_delta",
        "thinking_delta_text",
        "summary",
        "summary_text",
        "analysis",
        "thought"
    )

    private val contentFieldKeys = listOf(
        "content",
        "text",
        "output_text",
        "answer",
        "value",
        "token",
        "parts",
        "delta"
    )

    private val contentBlockTypes = setOf(
        "text",
        "text_delta",
        "output_text",
        "output_text_delta",
        "answer",
        "final_answer",
        "final",
        "assistant_response",
        "assistant",
        "message",
        "content",
        "response_text",
        "final_text"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun chatCompletion(
        messages: List<Map<String, Any>>,
        stream: Boolean = false
    ): Response {
        val requestBody = buildChatRequest(messages, stream)
        val request = buildRequest("/chat/completions", requestBody)
        return client.newCall(request).execute()
    }

    fun streamChatCompletion(
        messages: List<Map<String, Any>>,
        callback: StreamingCallback
    ): Call {
        val requestBody = buildChatRequest(messages, stream = true)
        val request = buildRequest("/chat/completions", requestBody)

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    callback.onError(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (call.isCanceled()) return

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    callback.onError(IOException("API error ${response.code}: $errorBody"))
                    return
                }

                try {
                    response.body?.let { body ->
                        val reader = BufferedReader(body.charStream())
                        var line: String? = null
                        var thinkingActive = false
                        while (!call.isCanceled() && reader.readLine().also { line = it } != null) {
                            val currentLine = line ?: continue
                            if (currentLine.startsWith("data: ")) {
                                val data = currentLine.substring(6).trim()
                                if (data == "[DONE]") {
                                    if (!call.isCanceled()) {
                                        if (thinkingActive) {
                                            callback.onThinkingComplete()
                                            thinkingActive = false
                                        }
                                        callback.onComplete()
                                    }
                                    break
                                }
                                try {
                                    val json = JsonParser.parseString(data).asJsonObject
                                    val choices = json.getAsJsonArray("choices")
                                    if (choices != null && choices.size() > 0) {
                                        val choice = choices[0].asJsonObject
                                        val delta = choice.getAsJsonObject("delta")

                                        val reasoningContent = extractReasoningText(choice, delta)
                                        if (!reasoningContent.isNullOrEmpty() && !call.isCanceled()) {
                                            if (!thinkingActive) {
                                                thinkingActive = true
                                                callback.onThinkingStart()
                                            }
                                            callback.onThinkingChunk(reasoningContent)
                                        }

                                        val content = extractContentText(choice, delta)
                                        if (!content.isNullOrEmpty() && !call.isCanceled()) {
                                            if (thinkingActive) {
                                                callback.onThinkingComplete()
                                                thinkingActive = false
                                            }
                                            callback.onChunk(content)
                                        }

                                        val finishReason = choice.get("finish_reason")
                                        if (thinkingActive && finishReason != null && !finishReason.isJsonNull && !call.isCanceled()) {
                                            callback.onThinkingComplete()
                                            thinkingActive = false
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Skip malformed chunks
                                }
                            }
                        }
                        reader.close()
                    }
                } catch (e: Exception) {
                    if (!call.isCanceled()) {
                        callback.onError(e)
                    }
                }
            }
        })
        return call
    }

    private fun extractReasoningText(choice: JsonObject, delta: JsonObject?): String? {
        if (delta == null) return null

        for (key in reasoningFieldKeys) {
            val text = extractTextFromElement(delta.get(key))
            if (!text.isNullOrEmpty()) {
                return text
            }
        }

        val reasoningFromBlocks = extractTextFromTypedBlocks(
            element = delta.get("content"),
            includeTypes = reasoningBlockTypes,
            allowUntyped = false
        )
        if (!reasoningFromBlocks.isNullOrEmpty()) {
            return reasoningFromBlocks
        }

        for (key in reasoningFieldKeys) {
            val text = extractTextFromElement(choice.get(key))
            if (!text.isNullOrEmpty()) {
                return text
            }
        }

        return null
    }

    private fun extractContentText(choice: JsonObject, delta: JsonObject?): String? {
        if (delta == null) return null

        val rawContent = delta.get("content")
        val contentFromBlocks = extractTextFromTypedBlocks(
            element = rawContent,
            includeTypes = contentBlockTypes,
            excludeTypes = reasoningBlockTypes,
            allowUntyped = true
        )
        if (!contentFromBlocks.isNullOrEmpty()) {
            return contentFromBlocks
        }

        val contentFromPrimitives = extractTextFromPrimitiveArray(rawContent)
        if (!contentFromPrimitives.isNullOrEmpty()) {
            return contentFromPrimitives
        }

        if (rawContent != null && !rawContent.isJsonNull && rawContent.isJsonPrimitive) {
            val primitiveContent = extractTextFromElement(rawContent)
            if (!primitiveContent.isNullOrEmpty()) {
                return primitiveContent
            }
        }

        for (key in contentFieldKeys) {
            if (key == "content") continue
            val text = extractTextFromElement(
                element = delta.get(key),
                preferredKeys = contentFieldKeys,
                excludedKeys = reasoningFieldKeys.toSet()
            )
            if (!text.isNullOrEmpty()) {
                return text
            }
        }

        val choiceText = extractTextFromElement(choice.get("text"))
        if (!choiceText.isNullOrEmpty()) {
            return choiceText
        }

        return null
    }

    private fun extractTextFromPrimitiveArray(element: JsonElement?): String? {
        if (element == null || element.isJsonNull || !element.isJsonArray) return null
        val text = buildString {
            element.asJsonArray.forEach { item ->
                if (item.isJsonPrimitive) {
                    val itemText = extractTextFromElement(item)
                    if (!itemText.isNullOrEmpty()) {
                        append(itemText)
                    }
                }
            }
        }
        return text.ifEmpty { null }
    }

    private fun extractTextFromTypedBlocks(
        element: JsonElement?,
        includeTypes: Set<String>,
        excludeTypes: Set<String> = emptySet(),
        allowUntyped: Boolean = false
    ): String? {
        if (element == null || element.isJsonNull) return null

        if (element.isJsonArray) {
            val text = buildString {
                element.asJsonArray.forEach { block ->
                    val blockText = extractTextFromTypedBlocks(block, includeTypes, excludeTypes, allowUntyped)
                    if (!blockText.isNullOrEmpty()) {
                        append(blockText)
                    }
                }
            }
            return text.ifEmpty { null }
        }

        if (!element.isJsonObject) return null
        val block = element.asJsonObject
        val typeElement = block.get("type")
        val type = if (
            typeElement != null &&
            !typeElement.isJsonNull &&
            typeElement.isJsonPrimitive &&
            typeElement.asJsonPrimitive.isString
        ) {
            normalizeType(typeElement.asString)
        } else {
            ""
        }
        if (type.isNotEmpty()) {
            if (excludeTypes.contains(type)) return null
            if (!includeTypes.contains(type)) return null
        } else if (!allowUntyped) {
            return null
        }

        return extractTextFromElement(
            element = block,
            preferredKeys = contentFieldKeys + reasoningFieldKeys,
            excludedKeys = setOf("type")
        )
    }

    private fun extractTextFromElement(
        element: JsonElement?,
        preferredKeys: List<String> = contentFieldKeys + reasoningFieldKeys,
        excludedKeys: Set<String> = emptySet()
    ): String? {
        if (element == null || element.isJsonNull) return null

        if (element.isJsonPrimitive) {
            val primitive = element.asJsonPrimitive
            if (primitive.isString) return primitive.asString
            if (primitive.isNumber || primitive.isBoolean) return primitive.toString()
            return null
        }

        if (element.isJsonArray) {
            val text = buildString {
                element.asJsonArray.forEach { item ->
                    val itemText = extractTextFromElement(
                        element = item,
                        preferredKeys = preferredKeys,
                        excludedKeys = excludedKeys
                    )
                    if (!itemText.isNullOrEmpty()) {
                        append(itemText)
                    }
                }
            }
            return text.ifEmpty { null }
        }

        if (element.isJsonObject) {
            val obj = element.asJsonObject
            for (key in preferredKeys) {
                if (excludedKeys.contains(key)) continue
                val text = extractTextFromElement(
                    element = obj.get(key),
                    preferredKeys = preferredKeys,
                    excludedKeys = excludedKeys
                )
                if (!text.isNullOrEmpty()) {
                    return text
                }
            }

            val text = buildString {
                obj.entrySet().forEach { (key, value) ->
                    if (excludedKeys.contains(key)) return@forEach
                    val valueText = extractTextFromElement(
                        element = value,
                        preferredKeys = preferredKeys,
                        excludedKeys = excludedKeys
                    )
                    if (!valueText.isNullOrEmpty()) {
                        append(valueText)
                    }
                }
            }
            return text.ifEmpty { null }
        }

        return null
    }

    private fun normalizeType(type: String?): String {
        return type?.trim()?.lowercase().orEmpty()
    }

    private fun buildChatRequest(messages: List<Map<String, Any>>, stream: Boolean): RequestBody {
        val body = mapOf(
            "model" to config.modelId,
            "messages" to messages,
            "stream" to stream
        )
        return gson.toJson(body).toRequestBody(jsonMediaType)
    }

    private fun buildRequest(endpoint: String, body: RequestBody): Request {
        val url = normalizeBaseUrl(config.baseUrl) + endpoint
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
    }

    private fun normalizeBaseUrl(url: String): String {
        var normalized = url.trim()
        if (normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }
        // Remove trailing /v1 if present (we'll add it)
        if (normalized.endsWith("/v1")) {
            // Keep it as is
        } else if (!normalized.contains("/v1")) {
            // Add /v1 if not present
            normalized = "$normalized/v1"
        }
        return normalized
    }

    companion object {
        fun parseNonStreamingResponse(response: Response): String {
            val body = response.body?.string() ?: throw IOException("Empty response")
            val json = JsonParser.parseString(body).asJsonObject
            val choices = json.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val message = choices[0].asJsonObject.getAsJsonObject("message")
                return message?.get("content")?.asString ?: ""
            }
            throw IOException("Invalid response format: $body")
        }
    }
}
