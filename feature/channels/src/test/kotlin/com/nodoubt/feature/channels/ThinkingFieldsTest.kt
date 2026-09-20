package com.nodoubt.feature.channels

import com.nodoubt.core.ai.ParameterType
import com.nodoubt.core.model.ParameterMode
import com.nodoubt.core.model.ParameterValue
import org.junit.Assert.*
import org.junit.Test

class ThinkingFieldsTest {
    private fun json(raw: String) = ParameterValue(ParameterMode.VALUE, parseTyped(raw, ParameterType.OBJECT))

    @Test fun resettingThinkingClearsAllProtocolOverridesAndKeepsOtherSettings() {
        val original = mapOf(
            "reasoning_effort" to ParameterValue(ParameterMode.VALUE, parseTyped("high", ParameterType.STRING)),
            "thinking" to json("{\"type\":\"enabled\",\"budget_tokens\":2048}"),
            "generationConfig" to json("{\"temperature\":0.6,\"thinkingConfig\":{\"thinkingBudget\":4096}}"),
            "output_config" to json("{\"effort\":\"high\",\"format\":\"text\"}"),
            "extraBody" to json("{\"reasoning\":{\"effort\":\"high\"},\"store\":false}")
        )
        val cleared = clearThinkingParameters(original)
        assertFalse(cleared.containsKey("reasoning_effort"))
        assertFalse(cleared.containsKey("thinking"))
        assertEquals("{\"temperature\":0.6}", cleared["generationConfig"]?.value.toString())
        assertEquals("{\"format\":\"text\"}", cleared["output_config"]?.value.toString())
        assertEquals("{\"store\":false}", cleared["extraBody"]?.value.toString())
        assertTrue(original["generationConfig"]!!.value!!.asJsonObject.has("thinkingConfig"))
        assertFalse(hasThinkingParameters(cleared))
    }

    @Test fun clearingOneThinkingFieldKeepsOtherModelThinkingChoices() {
        val original = mapOf("reasoning" to json("{\"effort\":\"high\",\"summary\":\"auto\"}"))
        val cleared = clearThinkingField(original, "reasoning.effort")
        assertEquals("auto", parameterValueAt(cleared, "reasoning.summary")?.value?.asString)
        assertNull(parameterValueAt(cleared, "reasoning.effort"))
        assertTrue(hasThinkingParameters(cleared))
    }

    @Test fun legacyStateWithoutAValueDisplaysDefaultAndCanBeFullyRemoved() {
        val values = mapOf("reasoning.effort" to ParameterValue(ParameterMode.INHERIT),
            "thinking_budget" to ParameterValue(ParameterMode.OMIT))
        assertFalse(hasThinkingParameters(values))
        assertTrue(clearThinkingParameters(values).isEmpty())
    }

    @Test fun resettingThinkingPreservesResponseSchemaBusinessFields() {
        val schema = json("""{"json_schema":{"schema":{"type":"object","properties":{"thinking":{"type":"string"},"reasoning_effort":{"type":"number"},"generationConfig":{"properties":{"thinkingConfig":{"type":"string"}}}}}}}""")
        val original = mapOf(
            "response_format" to schema,
            "response_format.json_schema.schema.properties.thinking" to json("{\"type\":\"string\"}"),
            "extraBody" to json("""{"response_format":{"properties":{"thinking":{"type":"string"}}},"reasoning_effort":"high"}""")
        )
        val cleared = clearThinkingParameters(original)
        assertEquals(schema, cleared["response_format"])
        assertEquals(original["response_format.json_schema.schema.properties.thinking"], cleared["response_format.json_schema.schema.properties.thinking"])
        assertEquals("{\"response_format\":{\"properties\":{\"thinking\":{\"type\":\"string\"}}}}", cleared["extraBody"]?.value.toString())
        assertFalse(hasThinkingParameters(cleared))
    }

    @Test fun defaultClearsCaseAndUnderscoreVariantsAtRecognizedRequestRoots() {
        val original = mapOf(
            "REASONING_EFFORT" to ParameterValue(ParameterMode.VALUE, parseTyped("high", ParameterType.STRING)),
            "Extra_Body" to json("""{"Chat_Template_Kwargs":{"Reasoning_Effort":"high","temperature":0.4},"metadata":{"thinking":"business value"}}"""),
            "Generation_Config.Thinking_Config.Thinking_Budget" to ParameterValue(ParameterMode.VALUE, parseTyped("4096", ParameterType.INTEGER))
        )
        val cleared = clearThinkingParameters(original)
        assertEquals(setOf("Extra_Body"), cleared.keys)
        assertEquals("{\"Chat_Template_Kwargs\":{\"temperature\":0.4},\"metadata\":{\"thinking\":\"business value\"}}", cleared["Extra_Body"]?.value.toString())
    }

    @Test fun clearingOneFieldHandlesWrappedAliasesWithoutTouchingSchemaNames() {
        val original = mapOf("Extra_Body" to json("""{"REASONING":{"EF_FORT":"high","summary":"auto"},"response_format":{"properties":{"reasoning.effort":{"type":"string"}}}}"""))
        val cleared = clearThinkingField(original, "reasoning.effort")
        assertEquals("{\"REASONING\":{\"summary\":\"auto\"},\"response_format\":{\"properties\":{\"reasoning.effort\":{\"type\":\"string\"}}}}", cleared["Extra_Body"]?.value.toString())
    }
}
