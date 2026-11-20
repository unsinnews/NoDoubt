package com.nodoubt.core.ai

import com.google.gson.JsonParser
import com.nodoubt.core.model.AiMessage
import com.nodoubt.core.model.AiRequest
import com.nodoubt.core.model.ContentPart
import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol
import org.junit.Assert.*
import org.junit.Test

class ProtocolRequestsTest {
    private val messages = listOf(AiMessage("user", listOf(ContentPart.Text("看图"), ContentPart.Image("aGVsbG8=", "image/png"))))

    @Test fun `new configurations send only pi defaults and never invent sampling or thinking`() {
        Protocol.entries.forEach { protocol ->
            val body = ProtocolRequests.encode(AiRequest(endpoint(protocol), messages, "系统指令"))
            listOf("temperature", "top_p", "top_k", "reasoning_effort", "reasoning", "thinking", "prompt_cache_key").forEach { key ->
                assertFalse("$protocol added $key", body.has(key))
            }
        }
        val completions = encode(Protocol.OPENAI_COMPLETIONS)
        assertEquals(false, completions.flag("store"))
        assertEquals(true, completions.obj("stream_options")!!.flag("include_usage"))
        assertEquals("system", completions.array("messages")[0].asJsonObject.text("role"))
        assertFalse(completions.has("max_completion_tokens"))
        assertEquals(false, encode(Protocol.OPENAI_RESPONSES).flag("store"))
        val anthropic = encode(Protocol.ANTHROPIC_MESSAGES)
        assertEquals(16384, anthropic.number("max_tokens"))
        val marker = json("""{"type":"ephemeral"}""")
        assertEquals(marker, anthropic.array("system")[0].asJsonObject.obj("cache_control"))
        assertEquals(marker, anthropic.array("messages")[0].asJsonObject.array("content")[1].asJsonObject.obj("cache_control"))
        assertNull(encode(Protocol.GOOGLE_GENERATIVE_AI).obj("generationConfig"))
    }

    @Test fun `chat completions detects defaults from the service URL like pi`() {
        val deepseek = encode(Protocol.OPENAI_COMPLETIONS, "https://api.deepseek.com/v1", """{"reasoning_effort":"high"}""",
            maxTokens = 8192)
        assertFalse(deepseek.has("store"))
        assertEquals(8192, deepseek.number("max_tokens"))
        assertEquals("enabled", deepseek.obj("thinking")!!.text("type"))
        assertEquals("high", deepseek.text("reasoning_effort"))
        val zai = encode(Protocol.OPENAI_COMPLETIONS, "https://open.bigmodel.cn/api/paas/v4", """{"reasoning_effort":"low"}""")
        assertEquals(false, zai.obj("thinking")!!.flag("clear_thinking"))
        assertFalse(zai.has("reasoning_effort"))
        val openRouter = encode(Protocol.OPENAI_COMPLETIONS, "https://openrouter.ai/api/v1", """{"reasoning_effort":"medium"}""")
        assertEquals("medium", openRouter.obj("reasoning")!!.text("effort"))
        assertFalse(openRouter.has("reasoning_effort"))
        assertEquals(4096, encode(Protocol.OPENAI_COMPLETIONS, maxTokens = 4096).number("max_completion_tokens"))
    }

    @Test fun `cache retention and session affinity follow pi per protocol`() {
        val openai = encode(Protocol.OPENAI_COMPLETIONS, parameters = """{"cacheRetention":"long","sessionId":"session-1"}""")
        assertEquals("session-1", openai.text("prompt_cache_key"))
        assertEquals("24h", openai.text("prompt_cache_retention"))
        assertFalse(encode(Protocol.OPENAI_COMPLETIONS, "https://proxy.example/v1", """{"sessionId":"s"}""").has("prompt_cache_key"))
        val responses = encode(Protocol.OPENAI_RESPONSES, parameters = """{"sessionId":"s"}""")
        assertEquals("s", responses.text("prompt_cache_key"))
        assertFalse(responses.has("prompt_cache_retention"))
        val anthropic = encode(Protocol.ANTHROPIC_MESSAGES, parameters = """{"cacheRetention":"long"}""")
        assertEquals("1h", anthropic.array("system")[0].asJsonObject.obj("cache_control")!!.text("ttl"))
        assertEquals("系统指令", encode(Protocol.ANTHROPIC_MESSAGES, parameters = """{"cacheRetention":"none"}""").text("system"))
        val markers = encode(Protocol.OPENAI_COMPLETIONS, compat = """{"cacheControlFormat":"anthropic"}""")
        assertTrue(markers.array("messages")[0].asJsonObject.array("content")[0].asJsonObject.has("cache_control"))
        assertTrue(markers.array("messages")[1].asJsonObject.array("content")[0].asJsonObject.has("cache_control"))
    }

    @Test fun `thinking defaults follow pi when thinking is configured`() {
        val responses = encode(Protocol.OPENAI_RESPONSES, parameters = """{"reasoning.effort":"high"}""")
        assertEquals("auto", responses.obj("reasoning")!!.text("summary"))
        val anthropic = encode(Protocol.ANTHROPIC_MESSAGES, parameters = """{"thinking.type":"enabled"}""")
        assertEquals("summarized", anthropic.obj("thinking")!!.text("display"))
        val google = encode(Protocol.GOOGLE_GENERATIVE_AI, parameters = """{"generationConfig.thinkingConfig.thinkingLevel":"HIGH"}""")
        assertEquals(true, google.obj("generationConfig")!!.obj("thinkingConfig")!!.flag("includeThoughts"))
        val off = encode(Protocol.GOOGLE_GENERATIVE_AI, parameters = """{"generationConfig.thinkingConfig.thinkingBudget":0}""")
        assertFalse(off.obj("generationConfig")!!.obj("thinkingConfig")!!.has("includeThoughts"))
        val vllm = encode(Protocol.OPENAI_COMPLETIONS, parameters = """{"reasoning_effort":"medium"}""",
            compat = """{"thinkingTokenBudgetField":"thinking_token_budget"}""")
        assertEquals(8192, vllm.number("thinking_token_budget"))
    }

    @Test fun `chat completions uses image URL parts and preserves zero and false`() {
        val endpoint = endpoint(Protocol.OPENAI_COMPLETIONS).copy(
            parameters = json("""{"temperature":0,"store":false,"max_completion_tokens":2048,"reasoning_effort":"high"}"""),
            compat = json("""{"maxTokensField":"max_tokens","thinkingFormat":"openrouter","supportsDeveloperRole":true}""")
        )
        val body = ProtocolRequests.encode(AiRequest(endpoint, messages, "system"))
        assertEquals("developer", body.array("messages")[0].asJsonObject.text("role"))
        val image = body.array("messages")[1].asJsonObject.array("content")[1].asJsonObject
        assertEquals("data:image/png;base64,aGVsbG8=", image.obj("image_url")?.text("url"))
        assertEquals(0, body.number("temperature"))
        assertEquals(false, body.flag("store"))
        assertEquals(2048, body.number("max_tokens"))
        assertEquals("high", body.obj("reasoning")?.text("effort"))
        assertFalse(body.has("reasoning_effort"))
    }

    @Test fun `responses has its own input encoding and nested parameter expansion`() {
        val endpoint = endpoint(Protocol.OPENAI_RESPONSES).copy(parameters = json("""{"reasoning.effort":"high","text.verbosity":"low"}"""))
        val body = ProtocolRequests.encode(AiRequest(endpoint, messages, "system"))
        assertFalse(body.has("messages"))
        assertEquals("system", body.text("instructions"))
        assertEquals("input_image", body.array("input")[0].asJsonObject.array("content")[1].asJsonObject.text("type"))
        assertEquals("high", body.obj("reasoning")?.text("effort"))
        assertEquals("low", body.obj("text")?.text("verbosity"))
    }

    @Test fun `anthropic uses model limit only for mandatory max tokens and explicit adaptive format`() {
        val endpoint = endpoint(Protocol.ANTHROPIC_MESSAGES).copy(maxOutputTokens = 4096,
            parameters = json("""{"thinking.type":"enabled","thinking.budget_tokens":1024}"""),
            compat = json("""{"forceAdaptiveThinking":true}"""))
        val body = ProtocolRequests.encode(AiRequest(endpoint, messages, "system"))
        assertEquals(4096, body.number("max_tokens"))
        assertEquals("adaptive", body.obj("thinking")?.text("type"))
        assertFalse(body.obj("thinking")!!.has("budget_tokens"))
        val source = body.array("messages")[0].asJsonObject.array("content")[1].asJsonObject.obj("source")!!
        assertEquals("base64", source.text("type"))
        assertEquals("image/png", source.text("media_type"))
    }

    @Test fun `google uses inlineData and generationConfig without OpenAI fields`() {
        val endpoint = endpoint(Protocol.GOOGLE_GENERATIVE_AI).copy(parameters = json("""{"generationConfig.temperature":0,"generationConfig.thinkingConfig.thinkingBudget":0}"""))
        val body = ProtocolRequests.encode(AiRequest(endpoint, messages, "system"))
        assertFalse(body.has("model"))
        assertFalse(body.has("stream"))
        assertEquals("image/png", body.array("contents")[0].asJsonObject.array("parts")[1].asJsonObject.obj("inlineData")?.text("mimeType"))
        assertEquals(0, body.obj("generationConfig")?.obj("thinkingConfig")?.number("thinkingBudget"))
        assertTrue(body.has("systemInstruction"))
    }

    @Test fun `extra body merges nested values last without turning streaming off`() {
        val endpoint = endpoint(Protocol.OPENAI_RESPONSES).copy(parameters = json("""{"reasoning.effort":"high","extraBody":{"reasoning":{"effort":"low","summary":"auto"},"stream":false}}"""))
        val body = ProtocolRequests.encode(AiRequest(endpoint, messages))
        assertEquals("low", body.obj("reasoning")?.text("effort"))
        assertEquals("auto", body.obj("reasoning")?.text("summary"))
        assertEquals(true, body.flag("stream"))
    }

    @Test fun `endpoint path normalization avoids duplicated actions and escapes model names`() {
        assertEquals("/custom/v1/responses", EndpointUrls.stream(endpoint(Protocol.OPENAI_RESPONSES).copy(baseUrl = "https://example.test/custom/v1/responses/")).encodedPath)
        val google = EndpointUrls.stream(endpoint(Protocol.GOOGLE_GENERATIVE_AI).copy(modelId = "models/odd model"))
        assertEquals("/v1beta/models/odd%20model:streamGenerateContent", google.encodedPath)
        assertEquals("sse", google.queryParameter("alt"))
    }

    @Test fun `budgets do not implicitly enable thinking or become unknown body fields`() {
        val endpoint = endpoint(Protocol.OPENAI_COMPLETIONS).copy(parameters = json("""{"thinkingBudgets":{"high":1024}}"""))
        val body = ProtocolRequests.encode(AiRequest(endpoint, messages))
        assertFalse(body.has("thinkingBudgets"))
        assertFalse(body.has("reasoning_effort"))
    }

    private fun endpoint(protocol: Protocol) = ModelEndpoint(protocol, protocol.defaultBaseUrl, "arbitrary-model")
    private fun encode(protocol: Protocol, baseUrl: String = protocol.defaultBaseUrl, parameters: String = "{}",
                       compat: String = "{}", maxTokens: Int? = null) = ProtocolRequests.encode(AiRequest(
        ModelEndpoint(protocol, baseUrl, "arbitrary-model", parameters = json(parameters), compat = json(compat),
            maxOutputTokens = maxTokens), messages, "系统指令"))
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject
}
