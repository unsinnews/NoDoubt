package com.nodoubt.core.ai

import com.google.gson.JsonObject
import com.nodoubt.core.model.AiRequest
import com.nodoubt.core.model.ContentPart
import com.nodoubt.core.model.Protocol

internal object ProtocolRequests {
    fun encode(request: AiRequest): JsonObject {
        require(request.endpoint.modelId.isNotBlank()) { "请先选择模型" }
        val body = when (request.endpoint.protocol) {
            Protocol.OPENAI_COMPLETIONS -> completions(request)
            Protocol.OPENAI_RESPONSES -> responses(request)
            Protocol.ANTHROPIC_MESSAGES -> AnthropicRequests.encode(request)
            Protocol.GOOGLE_GENERATIVE_AI -> GoogleRequests.encode(request)
        }
        body.merge(expandedParameters(request.endpoint.parameters))
        ProtocolCompatibility.apply(body, request.endpoint)
        request.endpoint.parameters.obj("extraBody")?.let(body::merge)
        if (request.endpoint.protocol != Protocol.GOOGLE_GENERATIVE_AI) {
            body.addProperty("model", request.endpoint.modelId)
            body.addProperty("stream", true)
        }
        if (request.endpoint.protocol == Protocol.ANTHROPIC_MESSAGES) {
            AnthropicLimits.apply(body)
        }
        return body
    }

    private fun completions(request: AiRequest): JsonObject = jsonObject {
        val messages = request.messages.map { message ->
            jsonObject {
                addProperty("role", message.role)
                add("content", jsonArray(message.content.map { part ->
                    when (part) {
                        is ContentPart.Text -> jsonObject { addProperty("type", "text"); addProperty("text", part.text) }
                        is ContentPart.Image -> jsonObject {
                            addProperty("type", "image_url")
                            add("image_url", jsonObject { addProperty("url", "data:${part.mimeType};base64,${part.base64}") })
                        }
                    }
                }))
            }
        }.toMutableList()
        if (request.system.isNotEmpty()) messages.add(0, jsonObject {
            addProperty("role", "system"); addProperty("content", request.system)
        })
        add("messages", jsonArray(messages))
    }

    private fun responses(request: AiRequest): JsonObject = jsonObject {
        if (request.system.isNotEmpty()) addProperty("instructions", request.system)
        add("input", jsonArray(request.messages.map { message -> jsonObject {
            addProperty("role", message.role)
            add("content", jsonArray(message.content.map { part -> when (part) {
                is ContentPart.Text -> jsonObject {
                    addProperty("type", if (message.role == "assistant") "output_text" else "input_text")
                    addProperty("text", part.text)
                }
                is ContentPart.Image -> jsonObject {
                    require(message.role != "assistant") { "Responses 协议不支持助手历史中的输入图片" }
                    addProperty("type", "input_image")
                    addProperty("image_url", "data:${part.mimeType};base64,${part.base64}")
                }
            } }))
        } }))
    }
}
