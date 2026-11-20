package com.nodoubt.core.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nodoubt.core.model.AiMessage
import com.nodoubt.core.model.AiRequest
import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol
import com.nodoubt.core.model.ThinkingOverride
import org.junit.Assert.*
import org.junit.Test

class ThinkingRequestsTest {
    @Test fun `four protocols map explicit effort to their own wire fields`() {
        val completions = prepare(Protocol.OPENAI_COMPLETIONS, ThinkingOverride.Effort("high"))
        val responses = prepare(Protocol.OPENAI_RESPONSES, ThinkingOverride.Effort("high"))
        val anthropic = prepare(Protocol.ANTHROPIC_MESSAGES, ThinkingOverride.Effort("high"))
        val google = prepare(Protocol.GOOGLE_GENERATIVE_AI, ThinkingOverride.Effort("HIGH"))
        assertEquals("high", wire(completions).text("reasoning_effort"))
        assertEquals("high", wire(responses).obj("reasoning")?.text("effort"))
        assertEquals("adaptive", wire(anthropic).obj("thinking")?.text("type"))
        assertEquals("high", wire(anthropic).obj("output_config")?.text("effort"))
        assertEquals("HIGH", wire(google).obj("generationConfig")?.obj("thinkingConfig")?.text("thinkingLevel"))
        listOf(completions, responses, anthropic, google).forEach {
            assertEquals("高", it.snapshot.label)
            assertEquals("manual-model", it.snapshot.modelId)
        }
    }

    @Test fun `default removes only root request controls across nested dotted and extra body forms`() {
        val parameters = """{
            "reasoning_effort":"high","thinking":{"type":"enabled","budget_tokens":4096},
            "generationConfig.thinkingConfig.thinkingLevel":"HIGH",
            "generationConfig":{"temperature":0,"thinkingConfig":{"thinkingBudget":2048}},
            "output_config":{"effort":"high","format":{"properties":{"thinking":{"type":"string"}}}},
            "response_format":{"type":"json_schema","json_schema":{"schema":{"properties":{"reasoning":{"type":"string"}}}}},
            "extraBody":{"reasoning.effort":"low","enable_thinking":true,
                "chat_template_kwargs":{"enable_thinking":true,"preserve_thinking":true,"ordinary":false},
                "temperature":0.2,"business_schema":{"properties":{"thinking":{"type":"boolean"}}}},
            "extraBody.thinking.type":"enabled","max_tokens":8192
        }"""
        val prepared = prepare(Protocol.OPENAI_COMPLETIONS, ThinkingOverride.Default, parameters)
        val body = wire(prepared)
        assertFalse(body.has("reasoning_effort"))
        assertFalse(body.has("thinking"))
        assertFalse(body.has("reasoning.effort"))
        assertNull(body.obj("generationConfig")?.obj("thinkingConfig"))
        assertFalse(body.obj("output_config")!!.has("effort"))
        assertEquals(0, body.obj("generationConfig")!!.number("temperature"))
        assertEquals(0.2, body.get("temperature").asDouble, 0.0)
        assertEquals(false, body.obj("chat_template_kwargs")!!.flag("ordinary"))
        assertTrue(body.obj("output_config")!!.obj("format")!!.obj("properties")!!.has("thinking"))
        assertTrue(body.obj("response_format")!!.obj("json_schema")!!.obj("schema")!!.obj("properties")!!.has("reasoning"))
        assertTrue(body.obj("business_schema")!!.obj("properties")!!.has("thinking"))
        assertEquals("服务端默认", prepared.snapshot.label)
        assertEquals(0, prepared.snapshot.controls.size())
    }

    @Test fun `extra body cannot override selected effort and preparation does not mutate model configuration`() {
        val endpoint = ModelEndpoint(Protocol.OPENAI_RESPONSES, "https://example.invalid/v1", "manual-model",
            apiKey = "fixture-key", headers = mapOf("x-fixture" to "preserved"),
            parameters = json("""{"reasoning.effort":"low","extraBody":{"reasoning":{"effort":"minimal","summary":"detailed"}},"temperature":0.4}"""))
        val original = endpoint.parameters.deepCopy()
        val prepared = ThinkingRequests.prepare(AiRequest(endpoint, listOf(AiMessage("user", "题目"))), ThinkingOverride.Effort("high"))
        assertEquals("high", wire(prepared).obj("reasoning")!!.text("effort"))
        assertEquals("detailed", wire(prepared).obj("reasoning")!!.text("summary"))
        assertEquals(original, endpoint.parameters)
        assertEquals(endpoint.apiKey, prepared.request.endpoint.apiKey)
        assertEquals(endpoint.headers, prepared.request.endpoint.headers)
        assertEquals(0.4, prepared.request.endpoint.parameters.get("temperature").asDouble, 0.0)
        endpoint.parameters.addProperty("reasoning.effort", "max")
        assertEquals("高", prepared.snapshot.label)
        assertEquals("high", prepared.snapshot.controls.obj("reasoning")!!.text("effort"))
    }

    @Test fun `model settings differs from explicit default without changing original controls`() {
        val parameters = """{"reasoning_effort":"low"}"""
        val model = prepare(Protocol.OPENAI_COMPLETIONS, null, parameters)
        val default = prepare(Protocol.OPENAI_COMPLETIONS, ThinkingOverride.Default, parameters)
        assertEquals("低", model.snapshot.label)
        assertNull(model.snapshot.override)
        assertEquals("服务端默认", default.snapshot.label)
        assertEquals(ThinkingOverride.Default, default.snapshot.override)
        assertEquals("服务端默认", prepare(Protocol.GOOGLE_GENERATIVE_AI, null).snapshot.label)
        assertEquals("服务端默认", prepare(Protocol.GOOGLE_GENERATIVE_AI, null,
            """{"generationConfig.thinkingConfig.includeThoughts":true}""").snapshot.label)
    }

    @Test fun `anthropic effort and budget are mutually exclusive and explicit budget is not forced adaptive`() {
        val original = """{"thinking":{"type":"enabled","budget_tokens":4096,"display":"summarized"},"output_config":{"effort":"max"},"max_tokens":8192,
            "extraBody":{"thinking":{"type":"adaptive","budget_tokens":7000},"output_config.effort":"low"}}"""
        val effort = prepare(Protocol.ANTHROPIC_MESSAGES, ThinkingOverride.Effort("high"), original)
        assertFalse(wire(effort).obj("thinking")!!.has("budget_tokens"))
        val budget = prepare(Protocol.ANTHROPIC_MESSAGES, ThinkingOverride.Budget(2048), original, """{"forceAdaptiveThinking":true}""")
        assertEquals("enabled", wire(budget).obj("thinking")!!.text("type"))
        assertEquals(2048, wire(budget).obj("thinking")!!.number("budget_tokens"))
        assertEquals("summarized", wire(budget).obj("thinking")!!.text("display"))
        assertNull(wire(budget).obj("output_config"))
        assertFalse(wire(budget).has("output_config.effort"))
        assertEquals(8192, wire(budget).number("max_tokens"))
        assertEquals("预算 2048", budget.snapshot.label)
    }

    @Test fun `anthropic budget validates minimum and final maximum before any network call`() {
        assertThrows(IllegalArgumentException::class.java) { prepare(Protocol.ANTHROPIC_MESSAGES, ThinkingOverride.Budget(1023)) }
        assertThrows(IllegalArgumentException::class.java) {
            prepare(Protocol.ANTHROPIC_MESSAGES, ThinkingOverride.Budget(2048), """{"max_tokens":2048}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            prepare(Protocol.ANTHROPIC_MESSAGES, ThinkingOverride.Budget(2048), """{"max_tokens":8192,"extraBody":{"max_tokens":1024}}""")
        }
        val fallback = prepare(Protocol.ANTHROPIC_MESSAGES, ThinkingOverride.Budget(2048))
        assertEquals(16384, wire(fallback).number("max_tokens"))
    }

    @Test fun `gemini level and budget cannot leak from the previous model setting`() {
        val original = """{"generationConfig":{"temperature":0.5,"thinkingConfig":{"thinkingLevel":"LOW","thinkingBudget":4096,"includeThoughts":true}},
            "extraBody":{"generationConfig.thinkingConfig.thinkingLevel":"HIGH"}}"""
        val level = prepare(Protocol.GOOGLE_GENERATIVE_AI, ThinkingOverride.Effort("MEDIUM"), original)
        assertEquals(json("""{"thinkingLevel":"MEDIUM","includeThoughts":true}"""), wire(level).obj("generationConfig")!!.obj("thinkingConfig"))
        val budget = prepare(Protocol.GOOGLE_GENERATIVE_AI, ThinkingOverride.Budget(-1), original)
        assertEquals(json("""{"thinkingBudget":-1,"includeThoughts":true}"""), wire(budget).obj("generationConfig")!!.obj("thinkingConfig"))
        assertFalse(wire(budget).has("generationConfig.thinkingConfig.thinkingLevel"))
        assertEquals("动态预算（服务端）", budget.snapshot.label)
        assertThrows(IllegalArgumentException::class.java) { prepare(Protocol.GOOGLE_GENERATIVE_AI, ThinkingOverride.Budget(-2)) }
    }

    @Test fun `snapshot reflects boolean compatibility and actual clipped budgets instead of selected labels`() {
        val boolean = prepare(Protocol.OPENAI_COMPLETIONS, ThinkingOverride.Effort("low"),
            compat = """{"thinkingFormat":"qwen","supportsReasoningEffort":false}""")
        assertEquals("已开启", boolean.snapshot.label)
        assertFalse(boolean.snapshot.controls.has("reasoning_effort"))
        assertEquals(true, boolean.snapshot.controls.flag("enable_thinking"))
        val clipped = prepare(Protocol.OPENAI_COMPLETIONS, null,
            """{"reasoning_effort":"high","thinkingBudgets":{"high":8192},"max_completion_tokens":3072}""",
            """{"supportsThinkingTokenBudget":true,"thinkingTokenBudgetField":"budget"}""")
        assertEquals(2048, clipped.snapshot.controls.number("budget"))
        assertEquals("高 · 预算 2048", clipped.snapshot.label)
        val default = prepare(Protocol.OPENAI_COMPLETIONS, ThinkingOverride.Default,
            """{"budget":8192,"extraBody":{"budget":4096}}""", """{"thinkingTokenBudgetField":"budget"}""")
        assertFalse(wire(default).has("budget"))
    }

    @Test fun `selected effort preserves extra body auxiliary fields and effort budget mapping`() {
        val selected = prepare(Protocol.OPENAI_COMPLETIONS, ThinkingOverride.Effort("high"),
            """{"reasoning_effort":"low","max_completion_tokens":3072,
                "thinkingBudgets":{"high":1024,"low":512},"extraBody":{"thinkingBudgets":{"high":8192},"reasoning.summary":"detailed"}}""",
            """{"supportsThinkingTokenBudget":true,"thinkingTokenBudgetField":"budget"}""")
        assertEquals(2048, wire(selected).number("budget"))
        assertEquals("detailed", wire(selected).obj("reasoning")!!.text("summary"))
        assertFalse(wire(selected).has("thinkingBudgets"))
        assertEquals(512, selected.request.endpoint.parameters.obj("thinkingBudgets")!!.number("low"))
        assertEquals("高 · 预算 2048", selected.snapshot.label)
        val google = prepare(Protocol.GOOGLE_GENERATIVE_AI, ThinkingOverride.Effort("HIGH"),
            """{"generationConfig.thinkingConfig.includeThoughts":true,
                "extraBody":{"generationConfig":{"thinkingConfig":{"includeThoughts":false,"thinkingBudget":128}}}}""")
        assertEquals(false, wire(google).obj("generationConfig")!!.obj("thinkingConfig")!!.flag("includeThoughts"))
        assertFalse(wire(google).obj("generationConfig")!!.obj("thinkingConfig")!!.has("thinkingBudget"))
    }

    @Test fun `effort choices use the same catalog as model configuration`() {
        assertEquals(ParameterCatalog.parameters(Protocol.ANTHROPIC_MESSAGES).first { it.path == "output_config.effort" }.choices,
            ThinkingRequests.options(Protocol.ANTHROPIC_MESSAGES).efforts)
        assertEquals("很高", prepare(Protocol.ANTHROPIC_MESSAGES, ThinkingOverride.Effort("xhigh")).snapshot.label)
    }

    @Test fun `qwen keeps final extra body display preference while dotted budget helpers compile before compatibility`() {
        val selected = prepare(Protocol.OPENAI_COMPLETIONS, ThinkingOverride.Effort("high"),
            """{"reasoning_effort":"low","max_completion_tokens":3072,"thinkingBudgets":{"low":512},
                "extraBody":{"chat_template_kwargs.preserve_thinking":false,"thinkingBudgets.high":8192,"reasoning_effort":"low"}}""",
            """{"thinkingFormat":"qwen-chat-template","supportsThinkingTokenBudget":true}""")
        val body = wire(selected)
        assertEquals(true, body.obj("chat_template_kwargs")!!.flag("enable_thinking"))
        assertEquals(false, body.obj("chat_template_kwargs")!!.flag("preserve_thinking"))
        assertEquals(2048, body.number("thinking_token_budget"))
        assertFalse(body.has("thinkingBudgets"))
        assertFalse(body.has("thinkingBudgets.high"))
        assertFalse(body.has("reasoning_effort"))
        assertEquals(512, selected.request.endpoint.parameters.obj("thinkingBudgets")!!.number("low"))
        assertEquals(8192, selected.request.endpoint.parameters.obj("thinkingBudgets")!!.number("high"))
    }

    @Test fun `zai keeps extra body clear thinking priority without restoring its old strength`() {
        val parameters = """{"thinking":{"type":"disabled","clear_thinking":false},
            "extraBody":{"thinking":{"type":"disabled","clear_thinking":true}}}"""
        val compat = """{"thinkingFormat":"zai","supportsReasoningEffort":false}"""
        val selected = prepare(Protocol.OPENAI_COMPLETIONS, ThinkingOverride.Effort("high"), parameters, compat)
        assertEquals("enabled", wire(selected).obj("thinking")!!.text("type"))
        assertEquals(true, wire(selected).obj("thinking")!!.flag("clear_thinking"))
        assertEquals("已开启", selected.snapshot.label)
        val default = prepare(Protocol.OPENAI_COMPLETIONS, ThinkingOverride.Default, parameters, compat)
        assertFalse(wire(default).has("thinking"))
        assertEquals("服务端默认", default.snapshot.label)
    }

    private fun prepare(protocol: Protocol, override: ThinkingOverride?, parameters: String = "{}", compat: String = "{}") =
        ThinkingRequests.prepare(AiRequest(ModelEndpoint(protocol, protocol.defaultBaseUrl, "manual-model",
            parameters = json(parameters), compat = json(compat)), listOf(AiMessage("user", "题目"))), override)
    private fun wire(prepared: ThinkingRequest) = ProtocolRequests.encode(prepared.request)
    private fun json(value: String): JsonObject = JsonParser.parseString(value).asJsonObject
}
