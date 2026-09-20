package com.nodoubt.core.ai

import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ModelDirectoryTest {
    private val server = MockWebServer()
    @Before fun setup() = server.start()
    @After fun teardown() = server.shutdown()

    @Test fun `google model directory paginates without capability filtering`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"models":[{"name":"models/plain-text-model","displayName":"Plain","inputTokenLimit":12000}],"nextPageToken":"p2"}"""))
        server.enqueue(MockResponse().setBody("""{"models":[{"name":"models/another","outputTokenLimit":4096}]}"""))
        val models = AiClient().listModels(endpoint(Protocol.GOOGLE_GENERATIVE_AI))
        assertEquals(listOf("plain-text-model", "another"), models.map { it.id })
        assertEquals(12000, models[0].contextWindow)
        assertEquals(4096, models[1].maxTokens)
        assertEquals("/v1/models", server.takeRequest().path)
        assertEquals("/v1/models?pageToken=p2", server.takeRequest().path)
    }

    @Test fun `anthropic directory uses cursor pagination and display names`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"a","display_name":"A"}],"has_more":true,"last_id":"a"}"""))
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"b"}],"has_more":false}"""))
        assertEquals(listOf("A", "b"), AiClient().listModels(endpoint(Protocol.ANTHROPIC_MESSAGES)).map { it.name })
        server.takeRequest()
        assertEquals("/v1/models?after_id=a", server.takeRequest().path)
    }

    @Test fun `custom catalog uses exact URL and does not leak credentials across origins`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":"public-model"}]"""))
        val endpoint = ModelEndpoint(Protocol.OPENAI_COMPLETIONS, "https://api.example.test/v1", "m", "private-key", mapOf("X-Secret" to "private-header"))
        val models = AiClient().listModels(endpoint, server.url("/custom/catalog.json?version=1").toString())
        assertEquals("public-model", models.single().id)
        val request = server.takeRequest()
        assertEquals("/custom/catalog.json?version=1", request.path)
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("X-Secret"))
    }

    private fun endpoint(protocol: Protocol) = ModelEndpoint(protocol, server.url("/v1").toString(), "custom", "key")
}
