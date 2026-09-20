package com.nodoubt.core.config

import com.google.gson.JsonParser
import com.nodoubt.core.model.ParameterMode
import com.nodoubt.core.model.ParameterValue
import org.junit.Assert.*
import org.junit.Test

class ModelThinkingTest {
    private fun value(json: String) = ParameterValue(ParameterMode.VALUE, JsonParser.parseString(json))

    @Test fun channelObjectsKeepOrdinaryFieldsButCannotSupplyThinking() {
        val channel = mapOf(
            "reasoning" to value("""{"effort":"high"}"""),
            "thinking" to value("""{"type":"enabled","budget_tokens":4096}"""),
            "output_config" to value("""{"effort":"high","format":{"type":"json"}}"""),
            "generationConfig" to value("""{"temperature":0.3,"thinkingConfig":{"thinkingBudget":4096}}""")
        )
        assertEquals(JsonParser.parseString("""{"output_config":{"format":{"type":"json"}},"generationConfig":{"temperature":0.3}}"""),
            ParameterResolver.parameters(channel, emptyMap()))
        assertTrue(channel.getValue("generationConfig").value!!.asJsonObject.has("thinkingConfig"))
    }

    @Test fun dottedChannelThinkingAndModelInheritProduceNoThinkingFields() {
        val channel = mapOf(
            "reasoning.effort" to value("\"high\""),
            "thinking.budget_tokens" to value("4096"),
            "output_config.effort" to value("\"medium\""),
            "generationConfig.thinkingConfig.includeThoughts" to value("true"),
            "generationConfig.topP" to value("0.8")
        )
        val inherited = channel.keys.associateWith { ParameterValue(ParameterMode.INHERIT) }
        val expected = JsonParser.parseString("""{"generationConfig":{"topP":0.8}}""")
        assertEquals(expected, ParameterResolver.parameters(channel, inherited))
        assertEquals(expected, ParameterResolver.parameters(channel, emptyMap()))
        listOf(ParameterMode.INHERIT, ParameterMode.OMIT).forEach { mode ->
            val thinking = channel.keys.filterNot { it == "generationConfig.topP" }.associateWith { ParameterValue(mode) }
            assertEquals(expected, ParameterResolver.parameters(channel, thinking))
        }
    }

    @Test fun channelExtraBodyCannotReintroduceThinking() {
        val channel = mapOf(
            "extraBody" to value("""{"reasoning":{"effort":"high"},"thinking":{"type":"enabled"},
                "generationConfig":{"thinkingConfig":{"thinkingLevel":"HIGH"},"temperature":0.2},
                "output_config":{"effort":"high","format":{"type":"json"}},"seed":42}"""),
            "extraBody.reasoning.summary" to value("\"auto\""),
            "extraBody.chat_template_kwargs.enable_thinking" to value("true")
        )
        assertEquals(JsonParser.parseString("""{"extraBody":{"generationConfig":{"temperature":0.2},
            "output_config":{"format":{"type":"json"}},"seed":42}}"""), ParameterResolver.parameters(channel, emptyMap()))
    }

    @Test fun explicitModelThinkingAndModelExtraBodyRemainEffective() {
        val channel = mapOf("reasoning.effort" to value("\"low\""), "extraBody" to value("""{"reasoning":{"effort":"low"},"seed":42}"""))
        val model = mapOf("reasoning.effort" to value("\"high\""), "extraBody" to value("""{"thinking":{"type":"enabled"},
            "generationConfig":{"thinkingConfig":{"thinkingBudget":2048}}}"""))
        assertEquals(JsonParser.parseString("""{"reasoning":{"effort":"high"},"extraBody":{"seed":42,
            "thinking":{"type":"enabled"},"generationConfig":{"thinkingConfig":{"thinkingBudget":2048}}}}"""),
            ParameterResolver.parameters(channel, model))
    }

    @Test fun compatibilityThinkingAliasesAreModelOnlyButResponseSchemasAreUntouched() {
        val aliases = listOf("reasoning_effort", "thinkingBudgets", "thinking_token_budget", "thinking_budget_tokens", "enable_thinking", "interleavedThinking")
        val channel = aliases.associateWith { value("true") } + mapOf("response_format" to value(
            """{"type":"json_schema","json_schema":{"schema":{"properties":{"reasoning":{"type":"string"}}}}}"""))
        val result = ParameterResolver.parameters(channel, emptyMap())
        assertEquals(1, result.size())
        assertEquals(channel.getValue("response_format").value, result["response_format"])
    }

    @Test fun importsWarnAndDiscardChannelThinkingWithoutCopyingItIntoModels() {
        val preview = ConfigCodec.decode("""{"providers":{"a":{"api":"openai-responses","models":[{"id":"m1"},{"id":"m2"}]}},
          "nodoubt":{"channels":{"a":{"parameters":{"reasoning":{"mode":"VALUE","value":{"effort":"high"}},
            "extraBody":{"mode":"VALUE","value":{"thinking":{"type":"enabled"},"temperature":0.2}}},
            "models":{"m1":{"parameters":{"reasoning.effort":{"mode":"VALUE","value":"low"}}}}}}}}""")
        val channel = preview.config.providers.single()
        assertTrue(preview.warnings.any { it.contains("渠道级思考配置已忽略") && it.contains("extraBody.thinking") })
        assertFalse(channel.parameters.containsKey("reasoning"))
        assertEquals(JsonParser.parseString("""{"temperature":0.2}"""), channel.parameters.getValue("extraBody").value)
        assertTrue(channel.models[1].parameters.isEmpty())
        assertEquals("low", ParameterResolver.parameters(channel.parameters, channel.models[0].parameters)
            .getAsJsonObject("reasoning")["effort"].asString)
    }
}
