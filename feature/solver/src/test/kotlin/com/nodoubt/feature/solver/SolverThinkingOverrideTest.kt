package com.nodoubt.feature.solver

import com.google.gson.JsonParser
import com.nodoubt.core.model.AiEvent
import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol
import com.nodoubt.core.model.ThinkingOverride
import com.nodoubt.core.model.Usage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SolverThinkingOverrideTest {
    @Test fun `temporary selection is isolated to one question and mode and ordinary retries retain it`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image()); runCurrent()
        gateway.last("ocr1").text("第一题|||第二题|||"); runCurrent()
        val before = gateway.calls.toList()
        val questionId = solver.state.value.questions.first().id
        assertTrue(solver.retryWithThinking(questionId, Usage.FAST, ThinkingOverride.Effort("high")).isSuccess)
        runCurrent()
        val first = solver.state.value.questions.first()
        val second = solver.state.value.questions.last()
        assertEquals("高", first.answers.getValue(Usage.FAST).thinkingSnapshot!!.label)
        assertEquals("服务端默认", first.answers.getValue(Usage.DEEP).thinkingSnapshot!!.label)
        assertEquals("服务端默认", second.answers.getValue(Usage.FAST).thinkingSnapshot!!.label)
        assertEquals(before.size + 1, gateway.calls.size)
        assertEquals(1, before.count { it.cancelled })
        solver.retry(questionId, Usage.FAST)
        solver.retry(questionId, Usage.FAST)
        runCurrent()
        assertEquals(ThinkingOverride.Effort("high"), solver.state.value.questions.first().answers.getValue(Usage.FAST).thinkingSnapshot!!.override)
        assertEquals("high", gateway.last("fast1").request.endpoint.parameters.get("reasoning_effort").asString)
    }

    @Test fun `default clears this request while model settings restores configuration and model change resets selection`() = runTest {
        val gateway = FakeSolverGateway().apply {
            endpoints["fast1"] = endpoint("fast1", """{"reasoning_effort":"low"}""")
            endpoints["fast2"] = endpoint("fast2", """{"reasoning_effort":"medium"}""")
        }
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image()); runCurrent()
        gateway.last("ocr1").text("题目|||"); runCurrent()
        val id = solver.state.value.questions.single().id
        assertEquals("低", solver.answerState(Usage.FAST).thinkingSnapshot!!.label)
        assertTrue(solver.retryWithThinking(id, Usage.FAST, ThinkingOverride.Default).isSuccess); runCurrent()
        assertEquals("服务端默认", solver.answerState(Usage.FAST).thinkingSnapshot!!.label)
        assertFalse(gateway.last("fast1").request.endpoint.parameters.has("reasoning_effort"))
        assertTrue(solver.retryWithThinking(id, Usage.FAST, null).isSuccess); runCurrent()
        assertEquals("低", solver.answerState(Usage.FAST).thinkingSnapshot!!.label)
        solver.retryWithThinking(id, Usage.FAST, ThinkingOverride.Effort("high")); runCurrent()
        solver.answer(id, Usage.FAST, ref("fast2")); runCurrent()
        assertEquals("中", solver.answerState(Usage.FAST).thinkingSnapshot!!.label)
        assertNull(solver.answerState(Usage.FAST).thinkingOverride)
        assertEquals("low", gateway.endpoints.getValue("fast1").parameters.get("reasoning_effort").asString)
    }

    @Test fun `new screenshot resets temporary settings and refuses output from replaced streams`() = runTest {
        val gateway = FakeSolverGateway()
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image()); runCurrent()
        gateway.last("ocr1").text("旧题|||"); runCurrent()
        val old = gateway.last("fast1")
        old.afterCancellation = listOf(AiEvent.ThinkingDelta("迟到思考"), AiEvent.TextDelta("迟到回答"))
        val id = solver.state.value.questions.single().id
        solver.retryWithThinking(id, Usage.FAST, ThinkingOverride.Effort("high"))
        runCurrent(); advanceTimeBy(2); runCurrent()
        assertEquals("", solver.answerState(Usage.FAST).text)
        assertEquals("", solver.answerState(Usage.FAST).thinking)
        assertEquals("高", solver.answerState(Usage.FAST).thinkingSnapshot!!.label)
        solver.prepareCapture()
        solver.recognize(image("new")); runCurrent()
        gateway.last("ocr1").text("新题|||"); runCurrent()
        assertEquals("服务端默认", solver.answerState(Usage.FAST).thinkingSnapshot!!.label)
        assertNull(solver.answerState(Usage.FAST).thinkingOverride)
    }

    @Test fun `invalid budget is rejected before canceling a running answer or changing its snapshot`() = runTest {
        val gateway = FakeSolverGateway().apply {
            endpoints["fast1"] = endpoint("fast1", """{"max_tokens":2048}""", Protocol.ANTHROPIC_MESSAGES)
        }
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image()); runCurrent()
        gateway.last("ocr1").text("题目|||"); runCurrent()
        val old = gateway.last("fast1")
        old.text("已有内容"); runCurrent()
        val before = solver.answerState(Usage.FAST)
        val id = solver.state.value.questions.single().id
        assertTrue(solver.retryWithThinking(id, Usage.FAST, ThinkingOverride.Budget(2048)).isFailure)
        runCurrent()
        assertEquals(before, solver.answerState(Usage.FAST))
        assertFalse(old.cancelled)
        assertEquals(1, gateway.calls("fast1").size)
        old.text("继续输出"); runCurrent()
        assertEquals("已有内容继续输出", solver.answerState(Usage.FAST).text)
        assertEquals(1024, solver.thinkingOptions(id, Usage.FAST)!!.minimumBudget)
    }

    @Test fun `request snapshot stays frozen when the gateway configuration changes`() = runTest {
        val gateway = FakeSolverGateway().apply { endpoints["fast1"] = endpoint("fast1", """{"reasoning_effort":"low"}""") }
        val solver = SolverController(gateway, backgroundScope)
        solver.recognize(image()); runCurrent()
        gateway.last("ocr1").text("题目|||"); runCurrent()
        val snapshot = solver.answerState(Usage.FAST).thinkingSnapshot!!
        gateway.endpoints.getValue("fast1").parameters.addProperty("reasoning_effort", "max")
        assertEquals("低", snapshot.label)
        assertEquals("low", snapshot.controls.get("reasoning_effort").asString)
        assertEquals("low", gateway.last("fast1").request.endpoint.parameters.get("reasoning_effort").asString)
    }

    private fun endpoint(id: String, parameters: String, protocol: Protocol = Protocol.OPENAI_COMPLETIONS) =
        ModelEndpoint(protocol, protocol.defaultBaseUrl, id, parameters = JsonParser.parseString(parameters).asJsonObject)
}
