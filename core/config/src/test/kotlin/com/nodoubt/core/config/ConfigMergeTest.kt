package com.nodoubt.core.config

import com.google.gson.JsonPrimitive
import com.nodoubt.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ConfigMergeTest {
    private val original = AppConfig(providers = listOf(ChannelConfig("a", "A", models = listOf(ModelConfig("one"), ModelConfig("two")))))

    @Test fun remoteRefreshPreservesLocalModelOverrideAndAcceptsIndependentRemoteChanges() {
        val channel = original.providers.single()
        val localModel = channel.models[0].copy(parameters = mapOf("temperature" to ParameterValue(ParameterMode.VALUE, JsonPrimitive(0.4))))
        val local = original.copy(providers = listOf(channel.copy(name = "本地名称", models = listOf(localModel, channel.models[1]))))
        val remote = original.copy(providers = listOf(channel.copy(name = "远程名称", baseUrl = "https://changed.example",
            models = listOf(channel.models[0], channel.models[1].copy(maxTokens = 200), ModelConfig("three")))))
        val result = ConfigMerge.threeWay(original, local, remote).providers.single()
        assertEquals("本地名称", result.name)
        assertEquals("https://changed.example", result.baseUrl)
        assertEquals(3, result.models.size)
        assertEquals(0.4, result.models.first().parameters.getValue("temperature").value!!.asDouble, 0.001)
        assertEquals(200, result.models[1].maxTokens)
    }

    @Test fun remoteDeletionsApplyOnlyWhenItemWasNotChangedLocally() {
        val channel = original.providers.single()
        val local = original.copy(providers = listOf(channel.copy(models = listOf(channel.models[0].copy(name = "Keep"), channel.models[1]))))
        val remote = original.copy(providers = listOf(channel.copy(models = emptyList())))
        val result = ConfigMerge.threeWay(original, local, remote)
        assertEquals(listOf("one"), result.providers.single().models.map { it.id })
    }

    @Test fun localParameterChangeAlsoProtectsModelFromRemoteDeletion() {
        val channel = original.providers.single()
        val model = channel.models[0].copy(parameters = mapOf("temperature" to ParameterValue(ParameterMode.OMIT)))
        val local = original.copy(providers = listOf(channel.copy(models = listOf(model, channel.models[1]))))
        val remote = original.copy(providers = emptyList())
        val result = ConfigMerge.threeWay(original, local, remote)
        assertEquals(1, result.providers.size)
        assertEquals(ParameterMode.OMIT, result.providers[0].models[0].parameters.getValue("temperature").mode)
    }

    @Test fun normalMergeRetainsExistingModelsAndAddsImportedModels() {
        val incoming = AppConfig(providers = listOf(ChannelConfig("a", "New A", models = listOf(ModelConfig("three"))), ChannelConfig("b", "B")))
        val result = ConfigMerge.merge(original, incoming)
        assertEquals(2, result.providers.size)
        assertEquals(listOf("one", "two", "three"), result.providers[0].models.map { it.id })
        assertEquals("New A", result.providers[0].name)
    }
}
