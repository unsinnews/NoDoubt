package com.nodoubt.feature.solver

import com.nodoubt.core.model.Usage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Closing the sheet stops everything; the bubble's 恢复上次解答 only shows that state again. */
@OptIn(ExperimentalCoroutinesApi::class)
class SolverRestoreTest {
    @Test fun `closing keeps the stopped session in memory and nothing is requested again`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image())
        runCurrent()
        gateway.last("ocr1").text("题目")
        gateway.last("ocr1").finish()
        runCurrent()
        gateway.last("fast1").text("部分回答")
        gateway.last("fast1").finish()
        gateway.last("deep1").text("深度一半")
        runCurrent()

        solver.stopAll()
        runCurrent()
        val closed = solver.state.value
        val requests = gateway.calls.size
        advanceUntilIdle()

        assertEquals(closed, solver.state.value)
        assertEquals(requests, gateway.calls.size)
        assertEquals(RunStatus.COMPLETE, solver.answerState(Usage.FAST).status)
        assertEquals("部分回答", solver.answerState(Usage.FAST).text)
        assertEquals(RunStatus.STOPPED, solver.answerState(Usage.DEEP).status)
        assertEquals("深度一半", solver.answerState(Usage.DEEP).text)
        assertTrue(gateway.last("deep1").cancelled)
    }

    @Test fun `exiting drops the session so there is nothing to restore`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image())
        runCurrent()
        gateway.last("ocr1").text("题目")
        gateway.last("ocr1").finish()
        runCurrent()
        solver.clear()
        runCurrent()
        assertTrue(solver.state.value.questions.isEmpty())
        assertNull(solver.state.value.error)
        assertTrue(gateway.last("fast1").cancelled)
    }
}
