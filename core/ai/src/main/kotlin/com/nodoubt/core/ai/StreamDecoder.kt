package com.nodoubt.core.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nodoubt.core.model.AiEvent
import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol

internal class StreamDecoder(private val endpoint: ModelEndpoint, private val emit: suspend (AiEvent) -> Unit) {
    private var terminal = false
    private var sawContent = false
    private var inputTokens = 0
    private var outputTokens = 0

    suspend fun frame(frame: SseFrame): Boolean {
        if (frame.data.trim() == "[DONE]") { terminal = true; return false }
        val json = try { JsonParser.parseString(frame.data).asJsonObject }
        catch (error: Exception) { throw AiException("服务端返回了无效的流式 JSON", cause = error) }
        if (frame.event == "error") throw responseError(json.toString())
        checkProviderError(json)
        when (endpoint.protocol) {
            Protocol.OPENAI_COMPLETIONS -> completions(json)
            Protocol.OPENAI_RESPONSES -> responses(json, frame.event)
            Protocol.ANTHROPIC_MESSAGES -> anthropic(json, frame.event)
            Protocol.GOOGLE_GENERATIVE_AI -> google(json)
        }
        return !(terminal && endpoint.protocol in setOf(Protocol.OPENAI_RESPONSES, Protocol.ANTHROPIC_MESSAGES))
    }

    suspend fun finish() {
        if (!terminal && !(sawContent && endpoint.compat.flag("supportsFinishReason") == false)) {
            throw AiException("响应流提前结束，未收到完成标记；可重试本次请求")
        }
        emit(AiEvent.Complete)
    }

    suspend fun document(json: JsonObject) {
        checkProviderError(json)
        when (endpoint.protocol) {
            Protocol.OPENAI_COMPLETIONS -> completions(json)
            Protocol.OPENAI_RESPONSES -> {
                json.array("output").forEach { item ->
                    val output = item.objectOrNull() ?: return@forEach
                    if (output.text("type") == "reasoning") {
                        output.array("summary").forEach { text(contentText(it), true) }
                    } else output.array("content").forEach { text(contentText(it)) }
                }
                usage(json.obj("usage"), "input_tokens", "output_tokens")
                terminal = json.text("status") in setOf("completed", "incomplete")
            }
            Protocol.ANTHROPIC_MESSAGES -> {
                json.array("content").forEach { part -> part.objectOrNull()?.let {
                    if (it.text("type") == "thinking") text(it.text("thinking"), true) else text(it.text("text"))
                } }
                usage(json.obj("usage"), "input_tokens", "output_tokens")
                terminal = !json.get("stop_reason").let { it == null || it.isJsonNull }
            }
            Protocol.GOOGLE_GENERATIVE_AI -> google(json)
        }
        finish()
    }

    private suspend fun completions(json: JsonObject) {
        json.array("choices").forEach { choice ->
            val item = choice.objectOrNull() ?: return@forEach
            if ((item.number("index") ?: 0) != 0) return@forEach
            val delta = item.obj("delta") ?: item.obj("message")
            if (delta != null) {
                text(contentText(delta.get("content")))
                val reasoning = contentText(delta.get("reasoning_content") ?: delta.get("reasoning"))
                text(reasoning, true)
                if (reasoning.isEmpty()) delta.array("reasoning_details").forEach { detail -> detail.objectOrNull()?.let {
                    text(it.text("text") ?: it.text("summary"), true)
                } }
                text(delta.text("refusal"))
            }
            val reason = item.get("finish_reason")
            if (reason != null && !reason.isJsonNull) terminal = true
        }
        usage(json.obj("usage"), "prompt_tokens", "completion_tokens")
    }

    private suspend fun responses(json: JsonObject, event: String?) {
        when (json.text("type") ?: event) {
            "response.output_text.delta", "response.refusal.delta" -> text(json.text("delta"))
            "response.reasoning_summary_text.delta", "response.reasoning_text.delta" -> text(json.text("delta"), true)
            "response.completed", "response.incomplete" -> {
                usage(json.obj("response")?.obj("usage"), "input_tokens", "output_tokens")
                terminal = true
            }
            "response.failed", "response.cancelled", "error" -> {
                throw responseError((json.obj("response") ?: json).toString())
            }
        }
    }

    private suspend fun anthropic(json: JsonObject, event: String?) {
        when (json.text("type") ?: event) {
            "message_start" -> usage(json.obj("message")?.obj("usage"), "input_tokens", "output_tokens")
            "content_block_start" -> json.obj("content_block")?.let { block ->
                text(block.text("text")); text(block.text("thinking"), true)
            }
            "content_block_delta" -> json.obj("delta")?.let { delta ->
                text(delta.text("text")); text(delta.text("thinking"), true)
            }
            "message_delta" -> usage(json.obj("usage"), "input_tokens", "output_tokens")
            "message_stop" -> terminal = true
            "error" -> throw responseError(json.toString())
        }
    }

    private suspend fun google(json: JsonObject) {
        json.obj("promptFeedback")?.text("blockReason")?.let { throw AiException("Gemini 拒绝请求：$it", code = it) }
        json.array("candidates").forEach { candidate ->
            val item = candidate.objectOrNull() ?: return@forEach
            if ((item.number("index") ?: 0) != 0) return@forEach
            item.obj("content")?.array("parts")?.forEach { part -> part.objectOrNull()?.let {
                text(it.text("text"), it.flag("thought") == true)
            } }
            item.text("finishReason")?.let { reason ->
                if (reason !in setOf("STOP", "MAX_TOKENS")) throw AiException("Gemini 响应终止：$reason", code = reason)
                terminal = true
            }
        }
        usage(json.obj("usageMetadata"), "promptTokenCount", "candidatesTokenCount", "thoughtsTokenCount")
    }

    private suspend fun text(value: String?, thinking: Boolean = false) {
        if (value.isNullOrEmpty()) return
        sawContent = true
        emit(if (thinking) AiEvent.ThinkingDelta(value) else AiEvent.TextDelta(value))
    }

    private suspend fun usage(value: JsonObject?, input: String, output: String, thoughts: String? = null) {
        if (value == null) return
        inputTokens = value.number(input) ?: inputTokens
        outputTokens = value.number(output)?.let { it + (thoughts?.let(value::number) ?: 0) } ?: outputTokens
        emit(AiEvent.Tokens(inputTokens, outputTokens))
    }
}
