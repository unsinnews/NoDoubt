package com.nodoubt.core.config

import com.google.gson.JsonParser
import com.nodoubt.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ConfigCodecTest {
    @Test fun piOverridesAreAppliedAfterModelAndPreserveMetadata() {
        val preview = ConfigCodec.decode("""{"providers":{"custom":{
          "api":"openai-completions","baseUrl":"https://provider.example/v1","apiKey":"${'$'}KEY",
          "headers":{"X-Provider":"yes"},"compat":{"supportsStore":false},
          "models":[{"id":"model","api":"openai-responses","baseUrl":"https://model.example/v1",
            "reasoning":false,"input":["text"],"cost":{"input":0.5},"maxTokens":100}],
          "modelOverrides":{"model":{"maxTokens":300,"compat":{"supportsStore":true}}}
        }}}""")
        val channel = preview.config.providers.single()
        val model = channel.models.single()
        val endpoint = ParameterResolver.resolve(preview.config, ModelRef("custom", "model")) { "resolved-$it" }
        assertEquals(Protocol.OPENAI_RESPONSES, endpoint.protocol)
        assertEquals("resolved-https://model.example/v1", endpoint.baseUrl)
        assertEquals(300, endpoint.maxOutputTokens)
        assertTrue(endpoint.compat["supportsStore"].asBoolean)
        assertFalse(model.metadata["reasoning"].asBoolean)
        assertEquals("text", model.metadata["input"].asJsonArray[0].asString)
        assertEquals(0.5, model.metadata["cost"].asJsonObject["input"].asDouble, 0.001)
        assertTrue(preview.warnings.any { it.contains("KEY") })
    }

    @Test fun roundTripPreservesGuiExtensionsAndProtocolSpecificOverrides() {
        val document = """{"schemaVersion":1,"providers":{"a":{"api":"google-generative-ai",
          "baseUrl":"https://example.com","models":[{"id":"m","name":"模型","reasoning":true}]}},
          "nodoubt":{"id":"profile","name":"配置","channels":{"a":{"name":"谷歌",
          "parameters":{"temperature":{"mode":"VALUE","value":0.2}},
          "models":{"m":{"parameters":{"temperature":{"mode":"OMIT"}}}}}},
          "bindings":{"FAST":{"entries":[{"channelId":"a","modelId":"m"}],
          "selected":{"channelId":"a","modelId":"m"}}}}}"""
        val first = ConfigCodec.decode(document).config
        val second = ConfigCodec.decode(ConfigCodec.encode(first)).config
        assertEquals(first, second)
        assertEquals(ParameterMode.OMIT, second.providers.single().models.single().parameters.getValue("temperature").mode)
        val pi = JsonParser.parseString(ConfigCodec.encode(first, purePi = true)).asJsonObject
        assertFalse(pi.has("nodoubt"))
        assertFalse(pi.has("schemaVersion"))
        assertEquals("google-generative-ai", pi["providers"].asJsonObject["a"].asJsonObject["api"].asString)
    }

    @Test fun literalKeysMoveToCredentialsAndCommandsAreNeverExecuted() {
        val literal = ConfigCodec.decode("""{"providers":{"a":{"apiKey":"sk-secret","models":[]}}}""")
        assertEquals("sk-secret", literal.credentials["secret:channel:a"])
        assertFalse(ConfigCodec.encode(literal.config).contains("sk-secret"))
        val command = ConfigCodec.decode("""{"providers":{"a":{"apiKey":"!touch should-not-exist",
          "headers":{"Authorization":"!print token"},"models":[]}}}""")
        assertEquals(2, command.warnings.count { it.contains("拒绝执行") })
        assertTrue(command.credentials.isEmpty())
        assertFalse(ConfigCodec.encode(command.config).contains("touch"))
    }

    @Test fun purePiSecretReferencesBecomeEnvironmentVariableNames() {
        val config = AppConfig(providers = listOf(ChannelConfig("custom", "A", credentialRef = "secret:channel:custom")))
        val pi = ConfigCodec.encode(config, purePi = true)
        assertTrue(pi.contains("${'$'}NODOUBT_CHANNEL_CUSTOM"))
        assertFalse(pi.contains("secret:"))
    }

    @Test fun explicitPiSamplingParametersAreImportedAndNoDoubtOverrideWins() {
        val parsed = ConfigCodec.decode("""{"providers":{"a":{"models":[{"id":"m",
          "samplingParams":{"temperature":0.2,"top_p":0.8}}]}},"nodoubt":{"channels":{"a":{
          "models":{"m":{"parameters":{"top_p":{"mode":"OMIT"}}}}}}}}""").config
        val endpoint = ParameterResolver.resolve(parsed, ModelRef("a", "m"))
        assertEquals(0.2, endpoint.parameters["temperature"].asDouble, 0.001)
        assertFalse(endpoint.parameters.has("top_p"))
        val pi = ConfigCodec.decode(ConfigCodec.encode(parsed, purePi = true)).config
        assertFalse(pi.providers[0].models[0].parameters.containsKey("top_p"))
    }

    @Test fun invalidInputsFailBeforeTheyCanReplaceStoredConfiguration() {
        listOf("{broken", "{}", "{\"providers\":[]} ", "{\"schemaVersion\":5,\"providers\":{}}",
            "{\"providers\":{\"a\":{\"api\":\"unsupported\"}}}", "{\"providers\":{}} trailing")
            .forEach { json -> assertTrue(json, runCatching { ConfigCodec.decode(json) }.isFailure) }
    }

    @Test fun purePiWarnsAboutParametersItCannotRepresent() {
        val options = mapOf("temperature" to ParameterValue(ParameterMode.VALUE, com.google.gson.JsonPrimitive(0.2)),
            "timeoutMs" to ParameterValue(ParameterMode.VALUE, com.google.gson.JsonPrimitive(1000)))
        val config = AppConfig(providers = listOf(ChannelConfig("a", "A", protocol = Protocol.ANTHROPIC_MESSAGES,
            parameters = options, models = listOf(ModelConfig("m")))))
        val json = ConfigCodec.encode(config, purePi = true)
        assertFalse(json.contains("samplingParams"))
        assertTrue(ConfigEncoder.warnings(config, true).any { it.contains("temperature") && it.contains("timeoutMs") })
        val openAi = config.copy(providers = listOf(config.providers[0].copy(protocol = Protocol.OPENAI_COMPLETIONS)))
        val openJson = ConfigCodec.encode(openAi, purePi = true)
        assertTrue(openJson.contains("temperature"))
        assertFalse(openJson.contains("timeoutMs"))
    }
}
