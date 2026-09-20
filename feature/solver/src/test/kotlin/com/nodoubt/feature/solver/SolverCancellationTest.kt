package com.nodoubt.feature.solver

import com.nodoubt.core.model.AiEvent
import com.nodoubt.core.model.Usage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SolverCancellationTest {
    @Test fun `stop all invalidates answer revisions before synchronous cancellation handlers run`() = runTest {
        val gateway = FakeSolverGateway()
        val immediateScope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val solver = SolverController(gateway, immediateScope)
        solver.recognize(image())
        gateway.last("ocr1").text("题目")
        gateway.last("ocr1").finish()
        runCurrent()
        val fast = gateway.last("fast1")
        fast.text("停止前")
        runCurrent()
        fast.afterCancellation = listOf(AiEvent.TextDelta("不应追加"))
        fast.afterCancellationDelayMs = 0
        solver.stopAll()
        runCurrent()
        assertTrue(fast.cancelled)
        assertEquals("停止前", solver.answerState(Usage.FAST).text)
        assertEquals(RunStatus.STOPPED, solver.answerState(Usage.FAST).status)
    }

    @Test fun `new screenshot cancels all old streams and late callbacks cannot pollute new questions`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image("old"))
        runCurrent()
        val oldOcr = gateway.last("ocr1")
        oldOcr.text("旧题目|||旧的半题")
        runCurrent()
        val oldFast = gateway.last("fast1")
        val oldDeep = gateway.last("deep1")
        oldOcr.afterCancellation = listOf(AiEvent.TextDelta("迟到旧题|||"))
        oldFast.afterCancellation = listOf(AiEvent.TextDelta("迟到旧回答"))
        oldDeep.afterCancellation = listOf(AiEvent.ThinkingDelta("迟到旧思考"))

        solver.recognize(image("new"))
        runCurrent()
        gateway.last("ocr1").text("新题目|||")
        runCurrent()
        advanceTimeBy(2)
        runCurrent()
        assertTrue(oldOcr.cancelled && oldFast.cancelled && oldDeep.cancelled)
        assertTrue(oldOcr.finished && oldFast.finished && oldDeep.finished)
        assertEquals(2L, solver.state.value.session)
        assertEquals("新题目", solver.state.value.questions.single().text)
        assertEquals("", solver.answerState(Usage.FAST).text)
        assertEquals("", solver.answerState(Usage.DEEP).thinking)
        assertEquals(2, gateway.calls("fast1").size)
        assertEquals(2, gateway.calls("deep1").size)
    }

    @Test fun `stop all invalidates OCR callbacks even when the screenshot session does not change`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image())
        runCurrent()
        val ocr = gateway.last("ocr1")
        ocr.text("未完成")
        runCurrent()
        ocr.afterCancellation = listOf(AiEvent.TextDelta("迟到的补全|||"))
        val session = solver.state.value.session
        solver.stopAll()
        runCurrent()
        advanceTimeBy(2)
        runCurrent()
        assertEquals(session, solver.state.value.session)
        assertEquals(RunStatus.STOPPED, solver.state.value.recognition)
        assertEquals("未完成", solver.state.value.questions.single().text)
        assertFalse(solver.state.value.questions.single().ready)
        assertTrue(gateway.calls("fast1").isEmpty())
        assertTrue(gateway.calls("deep1").isEmpty())
    }

    @Test fun `clear discards image retry state and keeps later cancelled output out`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image())
        runCurrent()
        gateway.last("ocr1").afterCancellation = listOf(AiEvent.TextDelta("迟到题目|||"))
        solver.clear()
        solver.retryRecognition()
        runCurrent()
        advanceTimeBy(2)
        runCurrent()
        assertEquals(RunStatus.IDLE, solver.state.value.recognition)
        assertTrue(solver.state.value.questions.isEmpty())
        assertEquals(1, gateway.calls.size)
    }
}
