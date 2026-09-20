package com.nodoubt.core.ai

import com.nodoubt.core.model.AiEvent
import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StreamDecoderTest {
    @Test fun `SSE error event reports service message even without nested error object`() = runBlocking {
        val decoder = StreamDecoder(endpoint()) {}
        val failure = runCatching { decoder.frame(SseFrame("error", """{"message":"upstream unavailable","code":"busy"}""")) }.exceptionOrNull()
        assertEquals("upstream unavailable", (failure as AiException).message)
        assertEquals("busy", failure.code)
    }

    @Test fun `OpenRouter reasoning details parse independently of model names`() = runBlocking {
        val events = mutableListOf<AiEvent>()
        val decoder = StreamDecoder(endpoint()) { events.add(it) }
        decoder.frame(SseFrame(null, """{"choices":[{"delta":{"reasoning_details":[{"type":"reasoning.text","text":"details"}]},"finish_reason":"stop"}]}"""))
        decoder.finish()
        assertEquals(listOf(AiEvent.ThinkingDelta("details"), AiEvent.Complete), events)
    }

    private fun endpoint() = ModelEndpoint(Protocol.OPENAI_COMPLETIONS, "https://example.test/v1", "unclassified")
}
