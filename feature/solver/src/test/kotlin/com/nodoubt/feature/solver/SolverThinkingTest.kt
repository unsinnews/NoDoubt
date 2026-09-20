package com.nodoubt.feature.solver

import com.nodoubt.core.model.Usage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SolverThinkingTest {
    @Test fun `default models keep returned thinking in both modes without adding thinking parameters`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image())
        runCurrent()
        gateway.last("ocr1").text("题目")
        gateway.last("ocr1").finish()
        runCurrent()
        val fast = gateway.last("fast1")
        val deep = gateway.last("deep1")
        assertTrue(fast.request.endpoint.parameters.entrySet().isEmpty())
        assertTrue(deep.request.endpoint.parameters.entrySet().isEmpty())
        fast.thinking("极速返回的思考")
        deep.thinking("深度返回的思考")
        runCurrent()
        assertEquals("极速返回的思考", solver.answerState(Usage.FAST).thinking)
        assertEquals("深度返回的思考", solver.answerState(Usage.DEEP).thinking)
        listOf(Usage.FAST, Usage.DEEP).forEach { mode ->
            assertTrue(solver.answerState(mode).thinkingStartedAtMs > 0)
            assertEquals(0L, solver.answerState(mode).thinkingFinishedAtMs)
        }
        fast.text("极速正文")
        deep.text("深度正文")
        fast.finish()
        deep.finish()
        runCurrent()
        assertEquals("极速返回的思考", solver.answerState(Usage.FAST).thinking)
        assertEquals("深度返回的思考", solver.answerState(Usage.DEEP).thinking)
        listOf(Usage.FAST, Usage.DEEP).forEach { mode ->
            assertEquals(RunStatus.COMPLETE, solver.answerState(mode).status)
            assertTrue(solver.answerState(mode).thinkingFinishedAtMs >= solver.answerState(mode).thinkingStartedAtMs)
        }
    }
}
