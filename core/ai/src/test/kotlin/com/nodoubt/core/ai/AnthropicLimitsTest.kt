package com.nodoubt.core.ai

import com.google.gson.JsonParser
import com.nodoubt.core.model.AiMessage
import com.nodoubt.core.model.AiRequest
import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol
import org.junit.Assert.*
import org.junit.Test

class AnthropicLimitsTest {
    @Test fun `enabled thinking requires an integer budget of at least 1024`() {
        listOf(
            """{"thinking.type":"enabled"}""",
            """{"thinking":{"type":"enabled","budget_tokens":1023}}""",
            """{"thinking":{"type":"enabled","budget_tokens":1024.5}}"""
        ).forEach { parameters ->
            val error = assertThrows(IllegalArgumentException::class.java) { encode(parameters) }
            assertTrue(error.message.orEmpty().contains("请填写思考预算（至少1024）"))
        }
    }

    @Test fun `mandatory fallback reserves output beyond thinking while preserving configured caps`() {
        val parameters = """{"thinking.type":"enabled","thinking.budget_tokens":2048}"""
        assertEquals(3072, encode(parameters).number("max_tokens"))
        assertEquals(8192, encode(parameters, modelLimit = 8192).number("max_tokens"))
        assertEquals(4096, encode("""{"thinking":{"type":"enabled","budget_tokens":2048},"max_tokens":4096}""",
            modelLimit = 8192).number("max_tokens"))
        assertEquals(1024, encode("""{"thinking.type":"adaptive"}""").number("max_tokens"))
    }

    @Test fun `final extra body and model caps cannot undercut the enabled thinking budget`() {
        val invalid = listOf(
            """{"thinking.type":"enabled","thinking.budget_tokens":1024,"max_tokens":1024}""" to null,
            """{"thinking.type":"enabled","thinking.budget_tokens":1024}""" to 1024,
            """{"thinking.type":"enabled","thinking.budget_tokens":1024,"max_tokens":3072,"extraBody":{"thinking":{"budget_tokens":4096}}}""" to null,
            """{"thinking.type":"enabled","thinking.budget_tokens":1024,"max_tokens":4096,"extraBody":{"max_tokens":512}}""" to null
        )
        invalid.forEach { (parameters, limit) ->
            val error = assertThrows(IllegalArgumentException::class.java) { encode(parameters, limit) }
            assertTrue(error.message.orEmpty().contains("必须大于思考预算"))
        }
        val adaptive = encode("""{"thinking.type":"enabled","extraBody":{"thinking":{"type":"adaptive"},"max_tokens":512}}""")
        assertEquals("adaptive", adaptive.obj("thinking")?.text("type"))
        assertEquals(512, adaptive.number("max_tokens"))
    }

    private fun encode(parameters: String, modelLimit: Int? = null) = ProtocolRequests.encode(AiRequest(
        ModelEndpoint(Protocol.ANTHROPIC_MESSAGES, Protocol.ANTHROPIC_MESSAGES.defaultBaseUrl, "manual-model",
            parameters = JsonParser.parseString(parameters).asJsonObject, maxOutputTokens = modelLimit),
        listOf(AiMessage("user", "题目"))
    ))
}
