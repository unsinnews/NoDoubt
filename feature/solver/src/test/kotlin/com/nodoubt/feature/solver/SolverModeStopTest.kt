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
class SolverModeStopTest {
    @Test fun `mode stop cancels all its questions and pending OCR while other mode continues`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image())
        runCurrent()
        val ocr = gateway.last("ocr1")
        ocr.text("第一题|||第二题|||未识别完")
        runCurrent()
        gateway.calls("fast1").first().text("保留答案")
        runCurrent()
        ocr.afterCancellation = listOf(AiEvent.TextDelta("过期补全|||"))
        gateway.calls("fast1").forEach { it.afterCancellation = listOf(AiEvent.TextDelta("过期答案")) }

        solver.stopMode(Usage.FAST)
        runCurrent()
        advanceTimeBy(2)
        runCurrent()
        assertTrue(ocr.cancelled)
        assertEquals(RunStatus.STOPPED, solver.state.value.recognition)
        assertTrue(gateway.calls("fast1").all { it.cancelled })
        assertTrue(gateway.calls("deep1").none { it.cancelled })
        assertEquals(2, gateway.calls("fast1").size)
        assertEquals("未识别完", solver.state.value.questions.last().text)
        assertFalse(solver.state.value.questions.last().ready)
        val recognized = solver.state.value.questions.filter { it.ready }
        assertEquals("保留答案", recognized.first().answers.getValue(Usage.FAST).text)
        assertTrue(recognized.all { it.answers.getValue(Usage.FAST).status == RunStatus.STOPPED })
        assertTrue(recognized.all { it.answers.getValue(Usage.DEEP).status == RunStatus.RUNNING })

        gateway.calls("deep1").forEach { it.text("继续完成"); it.finish() }
        runCurrent()
        assertTrue(solver.state.value.questions.filter { it.ready }.all {
            it.answers.getValue(Usage.DEEP).status == RunStatus.COMPLETE
        })
    }

    @Test fun `mode stop before current mode output cancels both modes and recognition`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image())
        runCurrent()
        gateway.last("ocr1").text("题目|||")
        runCurrent()
        gateway.last("deep1").thinking("另一模式已有输出")
        runCurrent()
        solver.stopMode(Usage.FAST)
        runCurrent()
        assertTrue(gateway.last("ocr1").cancelled)
        assertTrue(gateway.last("fast1").cancelled)
        assertTrue(gateway.last("deep1").cancelled)
        assertEquals("另一模式已有输出", solver.answerState(Usage.DEEP).thinking)
        assertEquals(RunStatus.STOPPED, solver.answerState(Usage.DEEP).status)
    }

    @Test fun `explicit retry restarts a streaming answer without restarting the other mode`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image())
        runCurrent()
        gateway.last("ocr1").text("题目")
        gateway.last("ocr1").finish()
        runCurrent()
        val oldAnswer = gateway.last("fast1")
        oldAnswer.text("旧输出")
        oldAnswer.afterCancellation = listOf(AiEvent.TextDelta("迟到输出"))
        runCurrent()
        val oldRun = solver.answerState(Usage.FAST).runId
        solver.retry(1, Usage.FAST)
        runCurrent()
        advanceTimeBy(2)
        runCurrent()
        assertTrue(oldAnswer.cancelled)
        assertEquals(2, gateway.calls("fast1").size)
        assertEquals(1, gateway.calls("deep1").size)
        assertFalse(gateway.last("deep1").cancelled)
        assertEquals("", solver.answerState(Usage.FAST).text)
        assertTrue(solver.answerState(Usage.FAST).runId > oldRun)
        assertEquals(ref("fast1"), solver.answerState(Usage.FAST).model)
    }
}
