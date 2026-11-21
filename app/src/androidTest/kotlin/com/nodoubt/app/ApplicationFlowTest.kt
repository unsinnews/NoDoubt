package com.nodoubt.app

import androidx.compose.ui.test.*
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.graphics.toPixelMap
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.nodoubt.core.config.ConfigRepository
import com.nodoubt.core.model.*
import com.nodoubt.feature.solver.RunStatus
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun richTextPaintsAfterLeavingAndReturningToSettings() {
        compose.runOnUiThread {
            val repository = compose.activity.appGraph.repository
            val config = repository.state.value
            repository.save(config.copy(preferences = config.preferences.copy(appearance = Appearance.LIGHT)))
        }
        compose.onNode(hasText("我的") and hasClickAction()).performClick()
        repeat(2) {
            compose.onNodeWithText("外观与阅读").performClick()
            val pixels = compose.onNodeWithTag("settings-rich-text").captureToImage().toPixelMap()
            var ink = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.red < 0.5f && color.green < 0.5f && color.blue < 0.5f && color.alpha > 0.5f) ink++
            }
            assertTrue("富文本应实际绘制，不能只有空白布局", ink > 100)
            compose.onNodeWithContentDescription("返回").performClick()
        }
    }

    @Test fun channelDraftSurvivesRecreationAndWarnsBeforeExit() {
        compose.onNode(hasText("我的") and hasClickAction()).performClick()
        compose.onNodeWithText("模型服务").performClick()
        compose.onNodeWithContentDescription("添加服务").performClick()
        compose.onNodeWithContentDescription("名称").performTextReplacement("未保存渠道")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("未保存渠道").assertExists()
        compose.onNodeWithText("思考").assertDoesNotExist()
        assertFalse(compose.activity.appGraph.repository.state.value.providers.any { it.name == "未保存渠道" })
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("保存修改？").assertExists()
        compose.onNodeWithText("不保存").performClick()
    }

    @Test fun thinkingIsModelOnlyAndDefaultClearsOnlyThinking() {
        val repository = compose.activity.appGraph.repository
        compose.runOnUiThread {
            repository.save(AppConfig(id = repository.state.value.id,
                providers = listOf(ChannelConfig("thinking", "思考设置验证", models = listOf(
                    ModelConfig("test-thinking", parameters = mapOf(
                        "temperature" to ParameterValue(ParameterMode.VALUE, JsonPrimitive(0.25)))))))))
        }
        compose.onNode(hasText("我的") and hasClickAction()).performClick()
        compose.onNodeWithText("模型服务").performClick()
        compose.onNodeWithText("思考设置验证").performClick()
        compose.onNodeWithText("思考设置").assertDoesNotExist()
        compose.onNodeWithText("test-thinking").performClick()
        compose.onNodeWithText("思考设置").performClick()
        compose.onNodeWithText("修改").performClick()
        compose.onNodeWithText("思考强度").performClick()
        compose.onNodeWithText("高", substring = false).performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("保存", substring = false).performClick()
        fun model() = repository.state.value.providers.single().models.single()
        assertEquals("high", model().parameters["reasoning_effort"]?.value?.asString)
        compose.onNodeWithText("思考设置验证").performClick()
        compose.onNodeWithText("test-thinking").performClick()
        compose.onNodeWithText("思考设置").performClick()
        compose.onNodeWithText("默认", substring = false).performClick()
        compose.onNodeWithText("思考强度").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("保存", substring = false).performClick()
        assertEquals(setOf("temperature"), model().parameters.keys)
        assertEquals(0.25, model().parameters.getValue("temperature").value!!.asDouble, 0.0)
    }

    @Test fun encryptedConfigurationReloadAndTwoQuestionStreamingReachTheUi() {
        val server = MockWebServer()
        server.start()
        val baseUrl = server.url("/v1").toString()
        try {
            server.enqueue(stream("(计算题) 1+1=?|||", "(计算题) 2+2=?"))
            repeat(4) { server.enqueue(stream("【答案】", "完成。", thinking = "先确认题意，再计算。")) }
            val graph = compose.activity.appGraph
            val ref = ModelRef("fixture", "fixture-model")
            compose.runOnUiThread {
                val config = AppConfig(id = graph.repository.state.value.id,
                    providers = listOf(ChannelConfig("fixture", "本地验证", baseUrl = baseUrl,
                        models = listOf(ModelConfig("fixture-model")))),
                    bindings = Usage.entries.associateWith { UsageBinding(listOf(ref), ref) })
                graph.repository.saveChannel(config, "fixture", "test-only-key")
                graph.solver.prepareCapture()
                compose.activity.setContent { AnswerOverlay(graph, {}, {}, {}) }
            }
            compose.onAllNodesWithText("已停止", substring = true).assertCountEquals(0)
            compose.onNodeWithText("截图后，题目和解答会出现在这里").assertExists()
            compose.runOnUiThread { graph.solver.recognize(ContentPart.Image("iVBORw0KGgo=", "image/png")) }
            compose.waitUntil(25_000) {
                val state = graph.solver.state.value
                state.recognition == RunStatus.COMPLETE && state.questions.size == 2 &&
                    state.questions.all { q -> q.answers.values.all { it.status == RunStatus.COMPLETE } }
            }
            assertEquals(5, server.requestCount)
            val reloaded = ConfigRepository(compose.activity)
            assertNull(reloaded.initializationError)
            assertEquals("test-only-key", reloaded.resolve(ref).apiKey)
            assertFalse(reloaded.exportConfig().contains("test-only-key"))
            assertTrue(graph.solver.state.value.questions.all { question ->
                question.answers.values.all { it.thinking == "先确认题意，再计算。" }
            })
            // Thinking starts as a two-line preview; only the answer the user opens shows in full.
            compose.onAllNodesWithTag("thinking-content").assertCountEquals(0)
            compose.onAllNodesWithTag("thinking-preview").assertCountEquals(2)
            compose.onAllNodesWithContentDescription("展开思考过程")[0].performClick()
            compose.onAllNodesWithTag("thinking-content").assertCountEquals(1)
            compose.onNodeWithText("深度思考").performClick()
            compose.onAllNodesWithTag("thinking-content").assertCountEquals(0)
            compose.onNodeWithText("极速解题").performClick()
            compose.onAllNodesWithTag("thinking-content").assertCountEquals(1)
            compose.onNodeWithContentDescription("收起思考过程").performClick()
            compose.onAllNodesWithTag("thinking-content").assertCountEquals(0)
            compose.onNodeWithText("题目 1").assertExists()
            // The question copies from its header, the answer from its footer; each confirms in place.
            val clipboard = compose.activity.getSystemService(android.content.ClipboardManager::class.java)
            fun clip() = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            compose.onAllNodesWithContentDescription("复制题目")[0].performClick()
            compose.waitForIdle()
            assertEquals("(计算题) 1+1=?", clip())
            compose.onAllNodesWithContentDescription("已复制").assertCountEquals(1)
            compose.onAllNodesWithContentDescription("复制答案")[0].performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals("【答案】完成。", clip())
            compose.onNodeWithText("题目 2").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("重截").assertIsDisplayed()
        } finally { server.shutdown() }
    }

    @Test fun usageCandidatesReorderByLongPressDragAndDeleteWithIcon() {
        val repository = compose.activity.appGraph.repository
        val first = ModelRef("order", "first-model")
        val second = ModelRef("order", "second-model")
        compose.runOnUiThread {
            repository.save(AppConfig(id = repository.state.value.id,
                providers = listOf(ChannelConfig("order", "排序验证", models = listOf(ModelConfig("first-model"), ModelConfig("second-model")))),
                bindings = Usage.entries.associateWith { if (it == Usage.OCR) UsageBinding(listOf(first, second), first) else UsageBinding() }))
        }
        compose.onNode(hasText("我的") and hasClickAction()).performClick()
        compose.onNodeWithText("模型服务").performClick()
        compose.onNodeWithText("解题模型").performClick()
        compose.onNodeWithText("first-model").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 1f))
            repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(0f, 30f)) }
            up()
        }
        compose.waitForIdle()
        val binding = repository.state.value.bindings.getValue(Usage.OCR)
        assertEquals(listOf(second, first), binding.entries)
        assertEquals(second, binding.selected)
        compose.onNodeWithContentDescription("删除second-model").performClick()
        assertEquals(listOf(first), repository.state.value.bindings.getValue(Usage.OCR).entries)
        compose.onNodeWithText("撤销").performClick()
        assertEquals(listOf(first, second), repository.state.value.bindings.getValue(Usage.OCR).entries)
    }

    private fun stream(vararg chunks: String, thinking: String? = null): MockResponse {
        val deltas = buildList {
            thinking?.let { add(JsonObject().apply { addProperty("reasoning_content", it) }) }
            chunks.forEach { text -> add(JsonObject().apply { addProperty("content", text) }) }
        }
        val frames = deltas.joinToString("") { delta ->
            "data: {\"choices\":[{\"delta\":$delta}]}\n\n"
        }
        return MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody(frames + "data: [DONE]\n\n")
    }
}
