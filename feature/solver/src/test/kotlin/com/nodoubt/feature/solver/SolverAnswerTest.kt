package com.nodoubt.feature.solver

import com.nodoubt.core.model.AiEvent
import com.nodoubt.core.model.Usage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SolverAnswerTest {
    @Test fun `modes cancel switch models and retry independently without duplicate requests`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image())
        runCurrent()
        gateway.last("ocr1").text("题目")
        gateway.last("ocr1").finish()
        runCurrent()
        val fast = gateway.last("fast1")
        val deep = gateway.last("deep1")
        fast.text("部分回答")
        deep.thinking("思考中")
        runCurrent()

        solver.stop(1, Usage.FAST)
        runCurrent()
        assertTrue(fast.cancelled)
        assertEquals(RunStatus.STOPPED, solver.answerState(Usage.FAST).status)
        assertEquals("部分回答", solver.answerState(Usage.FAST).text)
        assertFalse(deep.cancelled)
        assertEquals(RunStatus.RUNNING, solver.answerState(Usage.DEEP).status)

        solver.answer(1, Usage.FAST)
        solver.answer(1, Usage.FAST)
        solver.answer(1, Usage.FAST, ref("fast1"))
        runCurrent()
        assertEquals(2, gateway.calls("fast1").size)
        val retry = gateway.last("fast1")
        retry.afterCancellation = listOf(AiEvent.TextDelta("过期回答"))
        solver.answer(1, Usage.FAST, ref("fast2"))
        solver.answer(1, Usage.FAST, ref("fast2"))
        runCurrent()
        advanceTimeBy(2)
        runCurrent()
        assertTrue(retry.cancelled)
        assertEquals(1, gateway.calls("fast2").size)
        assertEquals("", solver.answerState(Usage.FAST).text)
        assertEquals(ref("fast2"), solver.answerState(Usage.FAST).model)
        assertEquals("思考中", solver.answerState(Usage.DEEP).thinking)
        assertEquals(1, gateway.calls("deep1").size)

        gateway.last("fast2").text("新答案")
        gateway.last("fast2").finish()
        deep.text("深度答案")
        deep.finish()
        runCurrent()
        assertEquals(RunStatus.COMPLETE, solver.answerState(Usage.FAST).status)
        assertEquals(RunStatus.COMPLETE, solver.answerState(Usage.DEEP).status)
        solver.stop(1, Usage.DEEP)
        assertEquals(RunStatus.COMPLETE, solver.answerState(Usage.DEEP).status)
        solver.answer(1, Usage.FAST, ref("fast2"))
        solver.answer(1, Usage.FAST, ref("fast2"))
        runCurrent()
        assertEquals(2, gateway.calls("fast2").size)
        assertEquals("深度答案", solver.answerState(Usage.DEEP).text)
    }

    @Test fun `answer errors preserve output and do not restart the other mode`() = runTest {
        val gateway = FakeSolverGateway().apply { choices[Usage.FAST] = listOf(ref("fast1"), ref("fast2")) }
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image())
        runCurrent()
        gateway.last("ocr1").text("题目")
        gateway.last("ocr1").finish()
        runCurrent()
        gateway.last("fast1").text("已输出")
        gateway.last("fast1").fail("答案中断")
        runCurrent()
        assertEquals(RunStatus.ERROR, solver.answerState(Usage.FAST).status)
        assertEquals("已输出", solver.answerState(Usage.FAST).text)
        assertEquals("答案中断", solver.answerState(Usage.FAST).error)
        assertTrue(gateway.calls("fast2").isEmpty())
        assertFalse(gateway.last("deep1").cancelled)
        assertEquals(1, gateway.calls("deep1").size)
    }

    @Test fun `stopping absent questions or OCR mode is a no op`() = runTest {
        val solver = SolverController(FakeSolverGateway(), backgroundScope)
        solver.stop(99, Usage.FAST)
        solver.stop(1, Usage.OCR)
        assertTrue(solver.state.value.questions.isEmpty())
    }
}
