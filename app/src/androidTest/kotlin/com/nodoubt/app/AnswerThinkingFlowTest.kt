package com.nodoubt.app

import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.nodoubt.core.model.*
import com.nodoubt.feature.solver.RunStatus
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AnswerThinkingFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun thinkingDraftsRetryOnlyTheCurrentQuestionAndModeAfterConfirmation() = withFixture(
        parameters = mapOf(
            "reasoning_effort" to value("low"),
            "extraBody" to ParameterValue(ParameterMode.VALUE,
                JsonParser.parseString("""{"reasoning_effort":"low"}""")),
            "temperature" to ParameterValue(ParameterMode.VALUE, JsonPrimitive(0.25))
        )
    ) { graph, server, dismissals ->
        val originalFast = graph.solver.state.value.questions[0].answers.getValue(Usage.FAST)
        val originalDeep = graph.solver.state.value.questions[0].answers.getValue(Usage.DEEP)
        val originalSecond = graph.solver.state.value.questions[1]
        val originalConfig = graph.repository.exportConfig()
        val efforts = requireNotNull(graph.solver.thinkingOptions(1, Usage.FAST)).efforts
        val highIndex = efforts.indexOf("high").also { assertTrue(it >= 0) }
        fun unchanged() {
            assertEquals(originalDeep, graph.solver.state.value.questions[0].answers.getValue(Usage.DEEP))
            assertEquals(originalSecond, graph.solver.state.value.questions[1])
            assertEquals(originalConfig, graph.repository.exportConfig())
        }

        fastTag("answer-request-model", useUnmergedTree = true).assertTextEquals(FAST_MODEL)
        fastTag("thinking-level-control").assertContentDescriptionEquals("本次思考层级：低")
        clickTag("thinking-level-control")
        thinkingMenu().assertExists()
        menuTag("thinking-draft-label").assertTextEquals("低")
        fastTag("thinking-level-control").assertContentDescriptionEquals("本次思考层级：低")
        assertNoRequest(server)
        assertEquals(5, server.requestCount)
        assertEquals(originalFast, graph.solver.state.value.questions[0].answers.getValue(Usage.FAST))
        unchanged()

        // Exercise the real drag recognizer before checking an exact discrete choice.
        menuTag("thinking-depth-slider").performTouchInput {
            swipe(start = center, end = Offset(width - 1f, centerY), durationMillis = 400)
        }
        assertEquals(efforts.lastIndex.toFloat(), menuTag("thinking-depth-slider")
            .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0.01f)
        fastTag("thinking-level-control").assertContentDescriptionEquals("本次思考层级：低")
        assertNoRequest(server)
        assertEquals(5, server.requestCount)
        assertEquals(originalFast, graph.solver.state.value.questions[0].answers.getValue(Usage.FAST))
        unchanged()

        thinkingMenu().performSemanticsAction(SemanticsActions.Dismiss) { it() }
        thinkingMenu().assertDoesNotExist()
        assertNoRequest(server)
        assertEquals(originalFast, graph.solver.state.value.questions[0].answers.getValue(Usage.FAST))
        unchanged()

        clickTag("thinking-level-control")
        menuTag("thinking-draft-label").assertTextEquals("低")
        menuTag("thinking-depth-slider").performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            assertTrue(setProgress(highIndex.toFloat()))
        }
        menuTag("thinking-draft-label").assertTextEquals("高")
        fastTag("thinking-level-control").assertContentDescriptionEquals("本次思考层级：低")
        assertNoRequest(server)
        assertEquals(5, server.requestCount)
        assertEquals(originalFast, graph.solver.state.value.questions[0].answers.getValue(Usage.FAST))
        unchanged()
        val high = retryRequest(graph, server) { clickMenuTag("confirm-thinking") }
        assertEquals("high", high.get("reasoning_effort").asString)
        assertEquals(0.25, high.get("temperature").asDouble, 0.0)
        thinkingMenu().assertDoesNotExist()
        fastTag("thinking-level-control").assertContentDescriptionEquals("本次思考层级：高")
        assertEquals(6, server.requestCount)
        assertNoRequest(server)
        unchanged()

        val retried = retryRequest(graph, server) {
            fastNode(hasText("重试") and hasClickAction()).performScrollTo().performClick()
        }
        assertEquals("high", retried.get("reasoning_effort").asString)
        assertEquals(7, server.requestCount)
        unchanged()

        clickTag("thinking-level-control")
        val beforeDefault = graph.solver.state.value.questions[0].answers.getValue(Usage.FAST)
        clickMenuTag("thinking-option-default")
        menuTag("thinking-draft-label").assertTextEquals("默认")
        fastTag("thinking-level-control").assertContentDescriptionEquals("本次思考层级：高")
        assertNoRequest(server)
        assertEquals(7, server.requestCount)
        assertEquals(beforeDefault, graph.solver.state.value.questions[0].answers.getValue(Usage.FAST))
        unchanged()
        val defaults = retryRequest(graph, server) { clickMenuTag("confirm-thinking") }
        assertEquals("low", defaults.get("reasoning_effort").asString)
        assertEquals(0.25, defaults.get("temperature").asDouble, 0.0)
        assertNull(graph.solver.state.value.questions[0].answers.getValue(Usage.FAST).thinkingOverride)
        thinkingMenu().assertDoesNotExist()
        fastTag("thinking-level-control").assertContentDescriptionEquals("本次思考层级：低")
        assertEquals(8, server.requestCount)
        assertNoRequest(server)
        unchanged()

        clickTag("thinking-level-control")
        menuTag("thinking-depth-slider").performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            assertTrue(setProgress(highIndex.toFloat()))
        }
        val beforeSwitch = retryRequest(graph, server) { clickMenuTag("confirm-thinking") }
        assertEquals("high", beforeSwitch.get("reasoning_effort").asString)
        val currentFast = graph.solver.state.value.questions[0].answers.getValue(Usage.FAST)
        clickTag("answer-model-control")
        compose.onNodeWithTag("answer-model-menu").assertExists()
        assertNoRequest(server)
        clickModel(FAST_MODEL)
        compose.onNodeWithTag("answer-model-menu").assertDoesNotExist()
        assertEquals(currentFast, graph.solver.state.value.questions[0].answers.getValue(Usage.FAST))
        assertEquals(9, server.requestCount)
        assertNoRequest(server)
        unchanged()

        clickTag("answer-model-control")
        val switched = retryRequest(graph, server, SECOND_FAST_MODEL) { clickModel(SECOND_FAST_MODEL) }
        listOf("reasoning_effort", "reasoning", "thinking").forEach { assertFalse(switched.has(it)) }
        assertNull(graph.solver.state.value.questions[0].answers.getValue(Usage.FAST).thinkingOverride)
        fastTag("answer-request-model", useUnmergedTree = true).assertTextEquals(SECOND_FAST_MODEL)
        fastTag("thinking-level-control").assertContentDescriptionEquals("本次思考层级：服务端默认")
        assertEquals(10, server.requestCount)
        assertNoRequest(server)
        unchanged()

        // Keep an actual request pending so the bottom action exercises cancellation, not a completed answer.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        }
        fastNode(hasText("重试") and hasClickAction()).performScrollTo().performClick()
        val pending = takeBody(server)
        assertEquals(SECOND_FAST_MODEL, pending.get("model").asString)
        compose.waitUntil(5_000) {
            graph.solver.state.value.questions[0].answers.getValue(Usage.FAST).status == RunStatus.RUNNING
        }
        compose.onNodeWithTag("dismiss-answer").assertDoesNotExist()
        compose.onNodeWithTag("stop-answer").performClick()
        compose.waitUntil(5_000) {
            graph.solver.state.value.questions[0].answers.getValue(Usage.FAST).status == RunStatus.STOPPED
        }
        compose.onNodeWithTag("stop-answer").assertDoesNotExist()
        unchanged()
        compose.onNodeWithTag("dismiss-answer").performClick()
        assertEquals(1, dismissals.get())
        assertEquals(11, server.requestCount)
        assertNoRequest(server)
    }

    @Test fun budgetCancelAndValidationKeepTheAnswerUntilAValidRetryIsConfirmed() = withFixture(
        protocol = Protocol.ANTHROPIC_MESSAGES,
        parameters = mapOf("max_tokens" to ParameterValue(ParameterMode.VALUE, JsonPrimitive(2048)))
    ) { graph, server, _ ->
        val original = graph.solver.state.value
        val originalConfig = graph.repository.exportConfig()
        clickTag("thinking-level-control")
        clickMenuTag("thinking-option-budget")
        thinkingMenu().assertDoesNotExist()
        fastTag("thinking-budget-editor").assertExists()
        assertNoRequest(server)
        fastNode(hasContentDescription("思考预算")).performScrollTo().performTextReplacement("1536")
        clickTag("thinking-budget-cancel")
        fastTag("thinking-budget-editor").assertDoesNotExist()
        assertNoRequest(server)
        assertEquals(5, server.requestCount)
        assertEquals(original, graph.solver.state.value)
        assertEquals(originalConfig, graph.repository.exportConfig())

        clickTag("thinking-level-control")
        clickMenuTag("thinking-option-budget")
        thinkingMenu().assertDoesNotExist()
        fastTag("thinking-budget-editor").assertExists()
        val budget = fastNode(hasContentDescription("思考预算"))
        budget.performScrollTo().performTextReplacement("2048")
        assertNoRequest(server)
        clickTag("retry-with-thinking")
        fastTag("thinking-option-error").assertTextContains("必须大于思考预算", substring = true)
        assertNoRequest(server)
        assertEquals(5, server.requestCount)
        assertEquals(original, graph.solver.state.value)
        assertEquals(originalConfig, graph.repository.exportConfig())

        budget.performScrollTo().performTextReplacement("1024")
        val body = retryRequest(graph, server) { clickTag("retry-with-thinking") }
        assertEquals("enabled", body.getAsJsonObject("thinking").get("type").asString)
        assertEquals(1024, body.getAsJsonObject("thinking").get("budget_tokens").asInt)
        assertEquals(2048, body.get("max_tokens").asInt)
        fastTag("thinking-budget-editor").assertDoesNotExist()
        fastTag("thinking-level-control").assertContentDescriptionEquals("本次思考层级：预算 1024")
        assertEquals(original.questions[0].answers.getValue(Usage.DEEP),
            graph.solver.state.value.questions[0].answers.getValue(Usage.DEEP))
        assertEquals(original.questions[1], graph.solver.state.value.questions[1])
        assertEquals(originalConfig, graph.repository.exportConfig())
        assertEquals(6, server.requestCount)
    }

    private fun fastNode(matcher: SemanticsMatcher, useUnmergedTree: Boolean = false): SemanticsNodeInteraction = compose.onNode(
        matcher and hasAnyAncestor(hasTestTag("answer-fast-1")), useUnmergedTree = useUnmergedTree)

    private fun fastTag(tag: String, useUnmergedTree: Boolean = false) = fastNode(hasTestTag(tag), useUnmergedTree)

    private fun clickTag(tag: String) { fastTag(tag).performScrollTo().performClick() }

    private fun thinkingMenu() = compose.onNodeWithTag("thinking-menu-fast-1")

    private fun menuTag(tag: String) = compose.onNode(
        hasTestTag(tag) and hasAnyAncestor(hasTestTag("thinking-menu-fast-1")))

    private fun clickMenuTag(tag: String) { menuTag(tag).performClick() }

    private fun clickModel(model: String) {
        compose.onNode(hasTestTag("answer-model-option-thinking-flow-$model") and
            hasAnyAncestor(hasTestTag("answer-model-menu"))).performClick()
    }

    private fun retryRequest(graph: AppGraph, server: MockWebServer, expectedModel: String = FAST_MODEL,
        action: () -> Unit): JsonObject {
        val previousRun = graph.solver.state.value.questions[0].answers.getValue(Usage.FAST).runId
        action()
        compose.waitUntil(15_000) {
            graph.solver.state.value.questions[0].answers.getValue(Usage.FAST).let {
                it.runId > previousRun && it.status == RunStatus.COMPLETE
            }
        }
        val body = takeBody(server)
        assertEquals(expectedModel, body.get("model").asString)
        val question = body.getAsJsonArray("messages").last().asJsonObject.getAsJsonArray("content")
            .joinToString { it.asJsonObject.get("text")?.asString.orEmpty() }
        assertTrue(question.contains("1+1=?"))
        assertFalse(question.contains("2+2=?"))
        return body
    }

    private fun assertNoRequest(server: MockWebServer) {
        compose.waitForIdle()
        assertNull("打开或关闭菜单、滑动或选择草稿、编辑预算和预检失败不应发起请求", server.takeRequest(500, TimeUnit.MILLISECONDS))
    }

    private fun takeBody(server: MockWebServer): JsonObject {
        val request = server.takeRequest(3, TimeUnit.SECONDS) ?: error("未收到预期请求")
        return JsonParser.parseString(request.body.readUtf8()).asJsonObject
    }

    private fun withFixture(
        protocol: Protocol = Protocol.OPENAI_COMPLETIONS,
        parameters: Map<String, ParameterValue>,
        test: (AppGraph, MockWebServer, AtomicInteger) -> Unit
    ) {
        val graph = compose.activity.appGraph
        val original = graph.repository.state.value
        val dismissals = AtomicInteger()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val model = JsonParser.parseString(request.body.clone().readUtf8()).asJsonObject.get("model").asString
                return when (model) {
                    OCR_MODEL -> chatStream("(计算题) 1+1=?|||(计算题) 2+2=?|||")
                    FAST_MODEL, SECOND_FAST_MODEL -> if (protocol == Protocol.ANTHROPIC_MESSAGES) anthropicStream() else chatStream("极速原解答")
                    DEEP_MODEL -> chatStream("深度原解答")
                    else -> MockResponse().setResponseCode(400).setBody("unexpected model")
                }
            }
        }
        try {
            server.start()
            val baseUrl = server.url("/v1").toString()
            compose.runOnUiThread {
                val refs = mapOf(Usage.OCR to ModelRef("thinking-flow", OCR_MODEL),
                    Usage.FAST to ModelRef("thinking-flow", FAST_MODEL), Usage.DEEP to ModelRef("thinking-flow", DEEP_MODEL))
                val secondFast = ModelRef("thinking-flow", SECOND_FAST_MODEL)
                graph.repository.save(AppConfig(id = original.id,
                    providers = listOf(ChannelConfig("thinking-flow", "思考交互测试", baseUrl = baseUrl,
                        models = listOf(ModelConfig(OCR_MODEL), ModelConfig(FAST_MODEL, protocol = protocol, parameters = parameters),
                            ModelConfig(SECOND_FAST_MODEL, protocol = protocol),
                            ModelConfig(DEEP_MODEL)))),
                    bindings = refs.mapValues { (usage, ref) ->
                        UsageBinding(if (usage == Usage.FAST) listOf(ref, secondFast) else listOf(ref), ref)
                    }))
                graph.solver.prepareCapture()
                compose.activity.setContent {
                    AnswerOverlay(graph, {}, {}, {}, onDismiss = { dismissals.incrementAndGet() })
                }
                graph.solver.recognize(ContentPart.Image("iVBORw0KGgo=", "image/png"))
            }
            compose.waitUntil(25_000) {
                val state = graph.solver.state.value
                state.recognition == RunStatus.COMPLETE && state.questions.size == 2 &&
                    state.questions.all { q -> q.answers.values.all { it.status == RunStatus.COMPLETE } }
            }
            assertEquals(5, server.requestCount)
            val initial = List(5) { takeBody(server) }
            assertEquals(2, initial.count { it.get("model").asString == FAST_MODEL })
            assertEquals(2, initial.count { it.get("model").asString == DEEP_MODEL })
            test(graph, server, dismissals)
        } finally {
            try {
                compose.runOnUiThread { graph.solver.clear(); graph.repository.save(original) }
            } finally { server.shutdown() }
        }
    }

    private fun chatStream(text: String): MockResponse {
        val delta = JsonObject().apply { addProperty("content", text) }
        return stream("data: {\"choices\":[{\"delta\":$delta}]}\n\ndata: [DONE]\n\n")
    }

    private fun anthropicStream() = stream(
        "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"极速原解答\"}}\n\n" +
            "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n")

    private fun stream(body: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)

    private fun value(text: String) = ParameterValue(ParameterMode.VALUE, JsonPrimitive(text))

    private companion object {
        const val OCR_MODEL = "ocr-thinking-fixture"
        const val FAST_MODEL = "fast-thinking-fixture"
        const val SECOND_FAST_MODEL = "fast-second-fixture"
        const val DEEP_MODEL = "deep-thinking-fixture"
    }
}
