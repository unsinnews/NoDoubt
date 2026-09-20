package com.nodoubt.core.ai

import com.google.gson.JsonParser
import com.nodoubt.core.model.AiEvent
import com.nodoubt.core.model.AiMessage
import com.nodoubt.core.model.AiRequest
import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AiClientStreamTest {
    private val server = MockWebServer()
    @Before fun setup() = server.start()
    @After fun teardown() = server.shutdown()

    @Test fun `all four protocols parse split UTF8 SSE and use native auth`() = runBlocking {
        val cases = listOf(
            Protocol.OPENAI_COMPLETIONS to "completions.sse",
            Protocol.OPENAI_RESPONSES to "responses.sse",
            Protocol.ANTHROPIC_MESSAGES to "anthropic.sse",
            Protocol.GOOGLE_GENERATIVE_AI to "google.sse"
        )
        for ((protocol, fixture) in cases) {
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(fixture(fixture))
                .throttleBody(7, 1, TimeUnit.MILLISECONDS))
            val events = AiClient().stream(request(protocol)).toList()
            assertEquals(listOf("答案"), events.filterIsInstance<AiEvent.TextDelta>().map { it.text })
            assertEquals(listOf("分析"), events.filterIsInstance<AiEvent.ThinkingDelta>().map { it.text })
            assertEquals(AiEvent.Tokens(12, 5), events.filterIsInstance<AiEvent.Tokens>().last())
            assertEquals(1, events.count { it == AiEvent.Complete })
            val recorded = server.takeRequest()
            when (protocol) {
                Protocol.ANTHROPIC_MESSAGES -> {
                    assertEquals("secret", recorded.getHeader("x-api-key"))
                    assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))
                }
                Protocol.GOOGLE_GENERATIVE_AI -> assertEquals("secret", recorded.getHeader("x-goog-api-key"))
                else -> assertEquals("Bearer secret", recorded.getHeader("Authorization"))
            }
        }
    }

    @Test fun `HTTP and in stream errors do not masquerade as completion`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad credentials","code":"invalid_key"}}"""))
        val http = runCatching { AiClient().stream(request(Protocol.OPENAI_COMPLETIONS)).toList() }.exceptionOrNull()
        assertTrue(http is AiException)
        assertEquals(401, (http as AiException).status)
        assertEquals("invalid_key", http.code)
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody("event: error\ndata: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"busy\"}}\n\n"))
        val failure = runCatching { AiClient().stream(request(Protocol.ANTHROPIC_MESSAGES)).toList() }.exceptionOrNull()
        assertEquals("overloaded_error", (failure as AiException).code)
    }

    @Test fun `unfinished stream fails unless compatibility explicitly permits missing finish reason`() = runBlocking {
        val body = "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n"
        server.enqueue(MockResponse().setBody(body))
        val emitted = mutableListOf<AiEvent>()
        assertTrue(runCatching { AiClient().stream(request(Protocol.OPENAI_COMPLETIONS)).toList(emitted) }.isFailure)
        assertEquals(listOf(AiEvent.TextDelta("partial")), emitted)
        server.enqueue(MockResponse().setBody(body))
        val original = request(Protocol.OPENAI_COMPLETIONS)
        val compatible = original.copy(endpoint = original.endpoint.copy(compat = JsonParser.parseString("""{"supportsFinishReason":false}""").asJsonObject))
        assertEquals(AiEvent.Complete, AiClient().stream(compatible).toList().last())
    }

    @Test fun `retry occurs only when explicitly configured and before emitted content`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
        server.enqueue(MockResponse().setBody(fixture("completions.sse")))
        val original = request(Protocol.OPENAI_COMPLETIONS)
        val configured = original.copy(endpoint = original.endpoint.copy(parameters = JsonParser.parseString("""{"maxRetries":1,"maxRetryDelayMs":1}""").asJsonObject))
        assertEquals(AiEvent.Complete, AiClient().stream(configured).toList().last())
        assertEquals(2, server.requestCount)
    }

    private fun request(protocol: Protocol) = AiRequest(
        ModelEndpoint(protocol, server.url(if (protocol == Protocol.GOOGLE_GENERATIVE_AI) "/v1beta" else "/v1").toString(), "custom", "secret"),
        listOf(AiMessage("user", "hello"))
    )
    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()
}
