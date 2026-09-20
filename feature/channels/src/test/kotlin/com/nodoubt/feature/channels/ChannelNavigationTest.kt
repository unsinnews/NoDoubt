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
