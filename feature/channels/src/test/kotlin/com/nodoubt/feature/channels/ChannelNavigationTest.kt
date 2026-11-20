package com.nodoubt.feature.channels

import com.nodoubt.core.model.ChannelConfig
import com.nodoubt.core.model.ModelConfig
import org.junit.Assert.*
import org.junit.Test

class ChannelNavigationTest {
    @Test fun leavingAnUnchangedChannelReturnsWithoutConfirmation() {
        val editor = ChannelEditorState()
        editor.open(ChannelConfig("c", "Name"))
        editor.back()
        assertEquals(ChannelPage.LIST, editor.page)
        assertFalse(editor.confirmExit)
    }

    @Test fun leavingDirtyChannelKeepsDraftUntilExplicitDecision() {
        val editor = ChannelEditorState()
        editor.open(ChannelConfig("c", "Name"))
        editor.channel = editor.channel!!.copy(name = "Draft")
        editor.keyDraft = "unsaved-test-key"
        editor.keyChanged = true
        editor.back()
        assertEquals(ChannelPage.CHANNEL, editor.page)
        assertTrue(editor.confirmExit)
        assertEquals("Draft", editor.channel?.name)
        editor.discard()
        assertEquals(ChannelPage.LIST, editor.page)
        assertEquals("Name", editor.channel?.name)
        assertTrue(editor.keyDraft.isEmpty())
        assertFalse(editor.dirty)
    }

    @Test fun untouchedNewChannelLeavesWithoutConfirmation() {
        val editor = ChannelEditorState()
        editor.open(null)
        assertFalse(editor.dirty)
        editor.back()
        assertEquals(ChannelPage.LIST, editor.page)
        assertFalse(editor.confirmExit)
    }

    @Test fun movingModelsKeepsRenameOrigins() {
        val editor = ChannelEditorState()
        editor.open(ChannelConfig("c", "Name", models = listOf(ModelConfig("a"), ModelConfig("b"), ModelConfig("c"))))
        editor.moveModel(0, 2)
        assertEquals(listOf("b", "c", "a"), editor.channel?.models?.map { it.id })
        editor.modelIndex = 2
        editor.updateModel(ModelConfig("a2"))
        assertEquals("a2", editor.renamedModels["a"])
        assertEquals("a2", editor.currentModelId("a"))
        assertTrue(editor.dirty)
    }

    @Test fun nestedParameterBackReturnsToModelAndKeepsEditedData() {
        val editor = ChannelEditorState()
        editor.open(ChannelConfig("c", "Name", models = listOf(ModelConfig("old"))))
        editor.modelIndex = 0
        editor.updateModel(ModelConfig("edited"))
        editor.modelScope = true
        editor.page = ChannelPage.PARAMETERS
        editor.back()
        assertEquals(ChannelPage.MODEL, editor.page)
        editor.back()
        assertEquals(ChannelPage.CHANNEL, editor.page)
        assertEquals("edited", editor.channel?.models?.single()?.id)
        assertTrue(editor.dirty)
    }
}
