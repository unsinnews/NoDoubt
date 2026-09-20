package com.nodoubt.core.ai

import com.google.gson.JsonObject
import com.nodoubt.core.model.AiRequest
import com.nodoubt.core.model.ContentPart

internal object AnthropicRequests {
    fun encode(request: AiRequest): JsonObject = jsonObject {
        val systemText = buildList {
            if (request.system.isNotEmpty()) add(request.system)
            request.messages.filter { it.role in setOf("system", "developer") }.forEach { message ->
                require(message.content.all { it is ContentPart.Text }) { "Anthropic 系统消息只能包含文本" }
                add(message.content.filterIsInstance<ContentPart.Text>().joinToString("\n") { it.text })
            }
        }.joinToString("\n\n")
        if (systemText.isNotEmpty()) addProperty("system", systemText)
        add("messages", jsonArray(request.messages.filter { it.role !in setOf("system", "developer") }.map { message ->
            require(message.role in setOf("user", "assistant")) { "不支持的 Anthropic 消息角色：${message.role}" }
            jsonObject {
                addProperty("role", message.role)
                add("content", jsonArray(message.content.map { part -> when (part) {
                    is ContentPart.Text -> jsonObject { addProperty("type", "text"); addProperty("text", part.text) }
                    is ContentPart.Image -> jsonObject {
                        addProperty("type", "image")
                        add("source", jsonObject {
                            addProperty("type", "base64"); addProperty("media_type", part.mimeType); addProperty("data", part.base64)
                        })
                    }
                } }))
            }
        }))
    }
}
