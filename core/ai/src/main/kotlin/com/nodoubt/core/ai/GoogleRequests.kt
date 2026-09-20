package com.nodoubt.core.ai

import com.google.gson.JsonObject
import com.nodoubt.core.model.AiRequest
import com.nodoubt.core.model.ContentPart

internal object GoogleRequests {
    fun encode(request: AiRequest): JsonObject = jsonObject {
        val systemParts = buildList {
            if (request.system.isNotEmpty()) add(jsonObject { addProperty("text", request.system) })
            request.messages.filter { it.role in setOf("system", "developer") }.forEach { message ->
                require(message.content.all { it is ContentPart.Text }) { "Gemini 系统消息只能包含文本" }
                message.content.filterIsInstance<ContentPart.Text>().forEach { add(jsonObject { addProperty("text", it.text) }) }
            }
        }
        if (systemParts.isNotEmpty()) add("systemInstruction", jsonObject { add("parts", jsonArray(systemParts)) })
        add("contents", jsonArray(request.messages.filter { it.role !in setOf("system", "developer") }.map { message ->
            require(message.role in setOf("user", "assistant", "model")) { "不支持的 Gemini 消息角色：${message.role}" }
            jsonObject {
                addProperty("role", if (message.role == "assistant") "model" else message.role)
                add("parts", jsonArray(message.content.map { part -> when (part) {
                    is ContentPart.Text -> jsonObject { addProperty("text", part.text) }
                    is ContentPart.Image -> jsonObject { add("inlineData", jsonObject {
                        addProperty("mimeType", part.mimeType); addProperty("data", part.base64)
                    }) }
                } }))
            }
        }))
    }
}
