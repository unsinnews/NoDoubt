package com.nodoubt.core.config

import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.nodoubt.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ParameterResolverTest {
    @Test fun inheritOverrideAndOmitProduceOnlyEnabledFields() {
        val channel = mapOf(
            "temperature" to ParameterValue(ParameterMode.VALUE, JsonPrimitive(0.3)),
            "top_p" to ParameterValue(ParameterMode.VALUE, JsonPrimitive(0.9)),
            "max_tokens" to ParameterValue(ParameterMode.INHERIT),
            "text.verbosity" to ParameterValue(ParameterMode.VALUE, JsonPrimitive("medium"))
        )
        val model = mapOf(
            "temperature" to ParameterValue(ParameterMode.INHERIT),
            "top_p" to ParameterValue(ParameterMode.OMIT),
            "text.verbosity" to ParameterValue(ParameterMode.VALUE, JsonPrimitive("high"))
        )
        val result = ParameterResolver.parameters(channel, model)
        assertEquals(2, result.size())
        assertEquals(0.3, result["temperature"].asDouble, 0.001)
        assertEquals("high", result.getAsJsonObject("text")["verbosity"].asString)
        assertFalse(result.has("top_p"))
        assertFalse(result.has("max_tokens"))
    }

    @Test fun headersOverrideCaseInsensitivelyAndCompatMergesRecursively() {
        val model = ModelConfig("m", headers = mapOf("authorization" to "model"),
            compat = JsonParser.parseString("""{"routing":{"b":2}}""").asJsonObject)
        val provider = ChannelConfig("p", "P", headers = mapOf("Authorization" to "provider", "X-App" to "yes"),
            compat = JsonParser.parseString("""{"routing":{"a":1}}""").asJsonObject, models = listOf(model))
        val result = ParameterResolver.resolve(AppConfig(providers = listOf(provider)), ModelRef("p", "m"))
        assertEquals(2, result.headers.size)
        assertEquals("model", result.headers["authorization"])
        assertEquals(2, result.compat.getAsJsonObject("routing").size())
    }

    @Test fun secretProtectionAlsoProtectsVariablesAndSensitiveHeaders() {
        val config = AppConfig(variables = mapOf("KEY" to "test-key"), providers = listOf(
            ChannelConfig("a", "A", headers = mapOf("Authorization" to "Bearer test-key", "X-App" to "public"))))
        val (safe, secrets) = ConfigSecrets.protect(config)
        val encoded = ConfigCodec.encode(safe)
        assertFalse(encoded.contains("test-key"))
        assertTrue(encoded.contains("public"))
        assertEquals(2, secrets.size)
        assertEquals("test-key", ConfigSecrets.resolve("${'$'}KEY", safe, read = { secrets[it].orEmpty() }))
        assertTrue(runCatching { ConfigSecrets.resolve("${'$'}MISSING", safe, read = { "" }) }.isFailure)
    }

    @Test fun variableCyclesAreReported() {
        val config = AppConfig(variables = mapOf("A" to "${'$'}B", "B" to "${'$'}A"))
        assertTrue(runCatching { ConfigSecrets.resolve("${'$'}A", config, read = { "" }) }.exceptionOrNull()!!.message!!.contains("循环"))
    }

    @Test fun piTemplatesSupportInterpolationAndLiteralEscapes() {
        val config = AppConfig(variables = mapOf("TOKEN" to "secret:variable:TOKEN", "HOST" to "api.example.com"))
        assertEquals("Bearer token", ConfigSecrets.resolve("Bearer ${'$'}{TOKEN}", config, read = { "token" }))
        assertEquals("https://api.example.com/v1", ConfigSecrets.resolve("https://${'$'}HOST/v1", config, read = { "" }))
        assertEquals("${'$'}literal !literal", ConfigSecrets.resolve("${'$'}${'$'}literal ${'$'}!literal", config, read = { "" }))
    }

    @Test fun importedRemoteBaselinesCannotPersistLiteralCredentials() {
        val baseline = """{"providers":{"a":{"apiKey":"baseline-secret","headers":{"X-Custom-Token":"header-secret"}}}}"""
        val config = AppConfig(source = RemoteSource("https://config.example", baseline = baseline))
        val (safe, _) = ConfigSecrets.protect(config)
        val encoded = ConfigCodec.encode(safe)
        assertFalse(encoded.contains("baseline-secret"))
        assertFalse(encoded.contains("header-secret"))
    }

    @Test fun dottedOmitRemovesInheritedObjectChildWithoutDiscardingItsSibling() {
        val channel = mapOf("generationConfig" to ParameterValue(ParameterMode.VALUE,
            JsonParser.parseString("""{"temperature":0.2,"topP":0.8}""")))
        val model = mapOf("generationConfig.temperature" to ParameterValue(ParameterMode.OMIT))
        val expected = JsonParser.parseString("""{"generationConfig":{"topP":0.8}}""")
        assertEquals(expected, ParameterResolver.parameters(channel, model))
    }

    @Test fun dottedInheritRestoresChannelValueOverTheModelsParentOverride() {
        val channel = mapOf("generationConfig" to ParameterValue(ParameterMode.VALUE,
            JsonParser.parseString("""{"temperature":0.2,"topP":0.8}""")))
        val model = linkedMapOf("generationConfig.temperature" to ParameterValue(ParameterMode.INHERIT),
            "generationConfig" to ParameterValue(ParameterMode.VALUE, JsonParser.parseString("""{"temperature":0.9}""")))
        assertEquals(JsonParser.parseString("""{"generationConfig":{"temperature":0.2,"topP":0.8}}"""),
            ParameterResolver.parameters(channel, model))
    }

    @Test fun inheritWithoutChannelValueRemovesValueIntroducedByParentAndEmptyParentsArePruned() {
        val model = linkedMapOf("generationConfig.temperature" to ParameterValue(ParameterMode.INHERIT),
            "generationConfig" to ParameterValue(ParameterMode.VALUE, JsonParser.parseString("""{"temperature":0.5}""")))
        assertEquals(JsonParser.parseString("{}"), ParameterResolver.parameters(emptyMap(), model))
    }
}
