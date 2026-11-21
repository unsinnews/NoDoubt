package com.nodoubt.feature.solver

import androidx.compose.runtime.saveable.SaverScope
import com.nodoubt.core.model.Usage
import org.junit.Assert.*
import org.junit.Test

class SolverReadingStateTest {
    @Test fun thinkingStartsCollapsedAndOpeningOneAnswerDoesNotAffectOthers() {
        val state = SolverReadingState()
        assertFalse(state.isThinkingExpanded(Usage.FAST, questionId = 1, runId = 10))
        state.setThinkingExpanded(Usage.FAST, questionId = 1, runId = 10, expanded = true)
        assertTrue(state.isThinkingExpanded(Usage.FAST, questionId = 1, runId = 10))
        assertFalse(state.isThinkingExpanded(Usage.DEEP, questionId = 1, runId = 20))
        assertFalse(state.isThinkingExpanded(Usage.FAST, questionId = 2, runId = 11))
        assertFalse("a retry starts collapsed again", state.isThinkingExpanded(Usage.FAST, questionId = 1, runId = 12))
        state.setThinkingExpanded(Usage.FAST, questionId = 1, runId = 10, expanded = false)
        assertFalse(state.isThinkingExpanded(Usage.FAST, questionId = 1, runId = 10))
    }

    @Test fun savedStateRestoresSeparateReadingPositionsAndOpenedAnswers() {
        val state = SolverReadingState(fastPosition = 120, deepPosition = 360)
        state.setThinkingExpanded(Usage.FAST, questionId = 1, runId = 10, expanded = true)
        state.setThinkingExpanded(Usage.DEEP, questionId = 2, runId = 21, expanded = true)
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(SolverReadingState.Saver) { scope.save(state) }
        val restored = requireNotNull(SolverReadingState.Saver.restore(requireNotNull(saved)))
        assertEquals(120, restored.scrollFor(Usage.FAST).value)
        assertEquals(360, restored.scrollFor(Usage.DEEP).value)
        assertTrue(restored.isThinkingExpanded(Usage.FAST, questionId = 1, runId = 10))
        assertTrue(restored.isThinkingExpanded(Usage.DEEP, questionId = 2, runId = 21))
        assertFalse(restored.isThinkingExpanded(Usage.DEEP, questionId = 1, runId = 20))
    }
}
