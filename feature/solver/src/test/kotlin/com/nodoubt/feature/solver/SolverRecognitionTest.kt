package com.nodoubt.feature.solver

import com.nodoubt.core.model.ContentPart
import com.nodoubt.core.model.Usage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SolverRecognitionTest {
    @Test fun `each delimited question immediately starts both answer modes while OCR continues`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image())
        runCurrent()
        val ocr = gateway.last("ocr1")
        ocr.text("第一题|||第二")
        runCurrent()
        assertEquals(RunStatus.RUNNING, solver.state.value.recognition)
        assertEquals(1, gateway.calls("fast1").size)
        assertEquals(1, gateway.calls("deep1").size)
        assertTrue(solver.state.value.questions[0].ready)
        assertFalse(solver.state.value.questions[1].ready)

        ocr.text("题|||第三题")
        runCurrent()
        assertEquals(2, gateway.calls("fast1").size)
        assertEquals(2, gateway.calls("deep1").size)
        assertTrue(gateway.calls("fast1").none { it.finished })
        assertTrue(gateway.calls("deep1").none { it.finished })
        ocr.finish()
        runCurrent()
        assertEquals(RunStatus.COMPLETE, solver.state.value.recognition)
        assertEquals(listOf("第一题", "第二题", "第三题"), solver.state.value.questions.map { it.text })
        assertEquals(3, gateway.calls("fast1").size)
        assertEquals(3, gateway.calls("deep1").size)
        val second = gateway.calls("fast1")[1].request.messages.single().content.single() as ContentPart.Text
        assertTrue(second.text.endsWith("第二题"))
    }

    @Test fun `OCR fallback is allowed after an error before any output`() = runTest {
        val gateway = FakeSolverGateway().apply { choices[Usage.OCR] = listOf(ref("ocr1"), ref("ocr2")) }
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image())
        runCurrent()
        gateway.last("ocr1").fail()
        runCurrent()
        assertEquals(1, gateway.calls("ocr2").size)
        gateway.last("ocr2").text("成功识别")
        gateway.last("ocr2").finish()
        runCurrent()
        assertEquals(RunStatus.COMPLETE, solver.state.value.recognition)
        assertEquals("成功识别", solver.state.value.questions.single().text)
    }

    @Test fun `OCR failure after output preserves complete and partial questions without replay`() = runTest {
        val gateway = FakeSolverGateway().apply { choices[Usage.OCR] = listOf(ref("ocr1"), ref("ocr2")) }
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image())
        runCurrent()
        gateway.last("ocr1").text("完整题目|||未完成题目")
        runCurrent()
        gateway.last("fast1").text("已生成的回答")
        gateway.last("ocr1").fail("识别中断")
        runCurrent()
        assertEquals(RunStatus.ERROR, solver.state.value.recognition)
        assertEquals("识别中断", solver.state.value.error)
        assertTrue(gateway.calls("ocr2").isEmpty())
        assertEquals(listOf("完整题目", "未完成题目"), solver.state.value.questions.map { it.text })
        assertEquals(listOf(true, false), solver.state.value.questions.map { it.ready })
        assertEquals("已生成的回答", solver.state.value.questions[0].answers.getValue(Usage.FAST).text)
        assertEquals(1, gateway.calls("fast1").size)
        assertFalse(gateway.last("deep1").cancelled)
    }

    @Test fun `thinking output also prevents automatic OCR replay`() = runTest {
        val gateway = FakeSolverGateway().apply { choices[Usage.OCR] = listOf(ref("ocr1"), ref("ocr2")) }
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image())
        runCurrent()
        gateway.last("ocr1").thinking("已开始分析")
        gateway.last("ocr1").fail()
        runCurrent()
        assertEquals(RunStatus.ERROR, solver.state.value.recognition)
        assertTrue(gateway.calls("ocr2").isEmpty())
    }

    @Test fun `invalid first configuration falls back without issuing its network request`() = runTest {
        val gateway = FakeSolverGateway().apply {
            choices[Usage.OCR] = listOf(ref("ocr1"), ref("ocr2"))
            resolutionFailures.add("ocr1")
        }
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image())
        runCurrent()
        assertTrue(gateway.calls("ocr1").isEmpty())
        assertEquals(1, gateway.calls("ocr2").size)
    }
}
