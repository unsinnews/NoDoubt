package com.nodoubt.feature.channels

import com.nodoubt.core.ai.ParameterType
import com.nodoubt.core.model.ChannelConfig
import com.nodoubt.core.model.ModelConfig
import com.nodoubt.core.model.ParameterMode
import com.nodoubt.core.model.ParameterValue
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ConfigurationEditingTest {
    @Test fun typedRequestValuesKeepTheirJsonTypes() {
        assertTrue(parseTyped("false", ParameterType.BOOLEAN).asJsonPrimitive.isBoolean)
        assertFalse(parseTyped("false", ParameterType.BOOLEAN).asBoolean)
        assertTrue(parseTyped("0.75", ParameterType.NUMBER).asJsonPrimitive.isNumber)
        assertTrue(parseTyped("false", ParameterType.STRING).asJsonPrimitive.isString)
        assertEquals(3, parseTyped("[1,2,3]", ParameterType.ARRAY).asJsonArray.size())
        assertEquals("custom", parseTyped("{\"vendor\":\"custom\"}", ParameterType.OBJECT).asJsonObject["vendor"].asString)
    }

    @Test fun wrongJsonTypesAreRejectedBeforeSaving() {
        assertThrows(IllegalArgumentException::class.java) { parseTyped("[]", ParameterType.OBJECT) }
        assertThrows(IllegalArgumentException::class.java) { parseTyped("1.5", ParameterType.INTEGER) }
        assertThrows(IllegalArgumentException::class.java) { parseTyped("\"true\"", ParameterType.BOOLEAN) }
    }

    @Test fun configurationFilesAcceptUtf8BomAndRejectOversizedInput() {
        assertEquals("{}", readConfiguration(ByteArrayInputStream("\uFEFF{}".toByteArray())))
        assertThrows(IllegalArgumentException::class.java) {
            readConfiguration(ByteArrayInputStream(ByteArray(2 * 1024 * 1024 + 1)))
        }
    }

    @Test fun sequentialModelRenamesKeepOriginalBindingMapping() {
        val editor = ChannelEditorState()
        editor.open(ChannelConfig("channel", "Example", models = listOf(ModelConfig("model-original"))))
        editor.modelIndex = 0
        editor.updateModel(ModelConfig("model-o"))
        editor.updateModel(ModelConfig("model-new"))
        assertEquals("model-new", editor.renamedModels["model-original"])
        assertEquals("model-new", editor.channel?.models?.single()?.id)
        assertTrue(editor.dirty)
    }

    @Test fun structuredImportedParametersAreVisibleInTheirGuiFields() {
        val reasoning = ParameterValue(ParameterMode.VALUE, parseTyped("{\"effort\":\"low\"}", ParameterType.OBJECT))
        val values = mapOf("reasoning" to reasoning)
        assertEquals("low", parameterValueAt(values, "reasoning.effort")?.value?.asString)
        assertEquals(ParameterMode.OMIT,
            parameterValueAt(values + ("reasoning.effort" to ParameterValue(ParameterMode.OMIT)), "reasoning.effort")?.mode)
    }

    @Test fun reusingAnotherModelsOldIdDoesNotStealItsBindingIdentity() {
        val editor = ChannelEditorState()
        editor.open(ChannelConfig("c", "Example", models = listOf(ModelConfig("a"), ModelConfig("b"))))
        editor.modelIndex = 0
        editor.updateModel(ModelConfig("renamed-a"))
        editor.modelIndex = 1
        editor.updateModel(ModelConfig("a"))
        editor.updateModel(ModelConfig("renamed-b"))
        assertEquals("renamed-a", editor.renamedModels["a"])
        assertEquals("renamed-b", editor.renamedModels["b"])
    }

    @Test fun newModelAndRemovalKeepRemainingModelOriginsAligned() {
        val editor = ChannelEditorState()
        editor.open(ChannelConfig("c", "Example", models = listOf(ModelConfig("a"), ModelConfig("b"))))
        editor.addModels(listOf(ModelConfig("new")))
        editor.removeModel("a")
        editor.modelIndex = 0
        editor.updateModel(ModelConfig("renamed-b"))
        editor.modelIndex = 1
        editor.updateModel(ModelConfig("renamed-new"))
        assertEquals("renamed-b", editor.renamedModels["b"])
        assertFalse(editor.renamedModels.containsKey("new"))
    }

    @Test fun removingAnEditedModelWithDuplicateIdKeepsTheOtherModelAndItsBinding() {
        val editor = ChannelEditorState()
        editor.open(ChannelConfig("c", "Example", models = listOf(ModelConfig("a"), ModelConfig("b"))))
        editor.modelIndex = 1
        editor.updateModel(ModelConfig("a"))
        editor.removeModelAt(editor.modelIndex)
        assertEquals(listOf("a"), editor.channel?.models?.map { it.id })
        assertEquals("a", editor.currentModelId("a"))
        assertNull(editor.currentModelId("b"))
    }

    @Test fun readdingARemovedIdDoesNotRestoreTheRemovedModelsBindings() {
        val editor = ChannelEditorState()
        editor.open(ChannelConfig("c", "Example", models = listOf(ModelConfig("a"))))
        editor.removeModelAt(0)
        editor.addModels(listOf(ModelConfig("a")))
        assertNull(editor.currentModelId("a"))
    }
}
