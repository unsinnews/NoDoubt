package com.nodoubt.feature.solver

import com.nodoubt.core.model.Protocol
import com.nodoubt.core.model.ThinkingOptions
import com.nodoubt.core.model.ThinkingOverride
import org.junit.Assert.*
import org.junit.Test

class ThinkingLevelDraftTest {
    private val anthropic = ThinkingOptions(Protocol.ANTHROPIC_MESSAGES, listOf("low", "high"), 1024)
    private val gemini = ThinkingOptions(Protocol.GOOGLE_GENERATIVE_AI, listOf("LOW", "HIGH"), -1)

    @Test fun modelSettingsAndServiceDefaultRemainDifferentChoices() {
        assertNull(thinkingDraft("model", "", anthropic).getOrThrow())
        assertEquals(ThinkingOverride.Default, thinkingDraft("default", "", anthropic).getOrThrow())
    }

    @Test fun manualBudgetNeverInventsAValueForMissingOrInvalidInput() {
        listOf("", " ", "1.5", "2147483648", "1023").forEach {
            assertTrue(thinkingDraft("budget", it, anthropic).isFailure)
        }
        assertEquals(ThinkingOverride.Budget(1024), thinkingDraft("budget", " 1024 ", anthropic).getOrThrow())
    }

    @Test fun geminiPreservesDynamicDisabledAndExplicitBudgets() {
        listOf(-1, 0, 1, 5000).forEach {
            assertEquals(ThinkingOverride.Budget(it), thinkingDraft("budget", "$it", gemini).getOrThrow())
        }
        assertTrue(thinkingDraft("budget", "-2", gemini).isFailure)
    }

    @Test fun effortAndBudgetDoNotLeakIntoEachOther() {
        assertEquals(ThinkingOverride.Effort("HIGH"), thinkingDraft("effort:HIGH", "bad", gemini).getOrThrow())
        assertTrue(thinkingDraft("effort:high", "", gemini).isFailure)
        assertEquals("budget", thinkingChoice(ThinkingOverride.Budget(3000)))
        assertEquals("effort:high", thinkingChoice(ThinkingOverride.Effort("high")))
    }
}
