package com.nodoubt.feature.solver

import com.nodoubt.core.model.Usage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SolverStatusTest {
    @Test fun `initial recognition and queued answers never report stopped`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        assertEquals("等待截图", answerModeStatus(solver.state.value, Usage.FAST))
        solver.stopAll()
        assertTrue(solver.state.value.stoppedModes.isEmpty())
        solver.recognize(image())
        assertEquals("正在识别题目…", answerModeStatus(solver.state.value, Usage.FAST))
        runCurrent()
        gateway.last("ocr1").text("尚未识别完")
        runCurrent()
        assertEquals("正在识别题目…", answerModeStatus(solver.state.value, Usage.DEEP))
        val queued = SolverState(recognition = RunStatus.COMPLETE,
            questions = listOf(QuestionState(1, "题目", ready = true)))
        assertEquals("等待解答…", answerModeStatus(queued, Usage.FAST))
        assertTrue(answerModeIsRunning(queued, Usage.FAST))
        assertEquals("识别未完成", answerModeStatus(SolverState(recognition = RunStatus.ERROR, error = "网络失败"), Usage.FAST))
    }

    @Test fun `replacing screenshot retrying and clearing do not publish a stopped transition`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        val observed = mutableListOf<SolverState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { solver.state.collect { observed += it } }
        solver.recognize(image())
        runCurrent()
        gateway.last("ocr1").text("题目|||")
        runCurrent()
        gateway.last("fast1").text("生成中")
        runCurrent()
        observed.clear()
        solver.retry(1, Usage.FAST)
        runCurrent()
        assertFalse(observed.any { it.hasStoppedState() })
        assertEquals(2, gateway.calls("fast1").size)

        observed.clear()
        val session = solver.state.value.session
        solver.prepareCapture()
        assertEquals(session, solver.state.value.session)
        assertEquals("等待截图", answerModeStatus(solver.state.value, Usage.FAST))
        solver.recognize(image("next"))
        assertEquals("正在识别题目…", answerModeStatus(solver.state.value, Usage.FAST))
        solver.clear()
        assertFalse(observed.any { it.hasStoppedState() })
        assertEquals(RunStatus.IDLE, solver.state.value.recognition)
    }

    @Test fun `only an explicit stop during recognition marks both modes stopped and retry clears it`() = runTest {
        val solver = SolverController(FakeSolverGateway(), backgroundScope)
        solver.recognize(image())
        runCurrent()
        solver.stopMode(Usage.FAST)
        assertEquals("已停止", answerModeStatus(solver.state.value, Usage.FAST))
        assertEquals("已停止", answerModeStatus(solver.state.value, Usage.DEEP))
        assertFalse(answerModeIsRunning(solver.state.value, Usage.FAST))
        solver.retryRecognition()
        assertTrue(solver.state.value.stoppedModes.isEmpty())
        assertEquals("正在识别题目…", answerModeStatus(solver.state.value, Usage.FAST))
    }

    @Test fun `stopping as a question becomes ready cancels queued answers before they start`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        var stopped = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            solver.state.collect { state ->
                if (!stopped && state.questions.any { it.ready }) {
                    stopped = true
                    solver.stopMode(Usage.FAST)
                }
            }
        }
        solver.recognize(image())
        runCurrent()
        gateway.last("ocr1").text("题目|||")
        runCurrent()
        assertTrue(stopped)
        assertTrue(gateway.calls("fast1").isEmpty())
        assertTrue(gateway.calls("deep1").isEmpty())
        assertEquals("已停止", answerModeStatus(solver.state.value, Usage.FAST))
        assertFalse(answerModeIsRunning(solver.state.value, Usage.FAST))
    }

    @Test fun `stopping deep does not report fast stopped when it completes with unfinished OCR`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image())
        runCurrent()
        gateway.last("ocr1").text("题目|||下一题尚未完整")
        runCurrent()
        gateway.last("deep1").thinking("深度分析")
        runCurrent()
        solver.stopMode(Usage.DEEP)
        assertEquals("已停止", answerModeStatus(solver.state.value, Usage.DEEP))
        assertEquals("解答中…", answerModeStatus(solver.state.value, Usage.FAST))
        gateway.last("fast1").text("极速答案")
        gateway.last("fast1").finish()
        runCurrent()
        assertEquals("题目识别未完成", answerModeStatus(solver.state.value, Usage.FAST))
        assertFalse(Usage.FAST in solver.state.value.stoppedModes)
        assertEquals(RunStatus.COMPLETE, solver.state.value.questions.first().answers.getValue(Usage.FAST).status)
    }

    private fun SolverState.hasStoppedState(): Boolean = recognition == RunStatus.STOPPED || stoppedModes.isNotEmpty() ||
        questions.any { q -> q.answers.values.any { it.status == RunStatus.STOPPED } }
}
