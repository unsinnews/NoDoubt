package com.nodoubt.core.ai

import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

class ConnectionTestTest {
    private val server = MockWebServer()
    @Before fun setup() = server.start()
    @After fun teardown() = server.shutdown()

    private fun endpoint(key: String = "secret") = ModelEndpoint(Protocol.OPENAI_COMPLETIONS, server.url("/v1").toString(), "m", key)
    private fun sse(vararg deltas: String) = MockResponse().setHeader("Content-Type", "text/event-stream")
        .setBody(deltas.joinToString("") { "data: {\"choices\":[{\"delta\":$it}]}\n\n" } + "data: [DONE]\n\n")

    @Test fun `reply text is ok and quoted with latency`() = runBlocking {
        server.enqueue(sse("""{"content":"OK"}"""))
        val result = AiClient().test(endpoint())
        assertEquals(ConnectionResult.Level.OK, result.level)
        assertEquals("连接正常", result.summary)
        assertTrue(result.detail!!.contains("OK"))
    }

    @Test fun `thinking only or empty replies are warnings not success`() = runBlocking {
        server.enqueue(sse("""{"reasoning_content":"想一想"}"""))
        assertEquals("已连接，只返回了思考内容", AiClient().test(endpoint()).summary)
        server.enqueue(sse("""{"content":""}"""))
        val empty = AiClient().test(endpoint())
        assertEquals(ConnectionResult.Level.WARNING, empty.level)
        assertTrue(empty.ok)
    }

    @Test fun `http failures are explained and never retried`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        val unauthorized = AiClient().test(endpoint())
        assertEquals("API Key 无效", unauthorized.summary)
        assertFalse(unauthorized.ok)
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        assertEquals("未填写 API Key", AiClient().test(endpoint("")).summary)
        server.enqueue(MockResponse().setResponseCode(503).setBody("busy"))
        assertEquals("服务端暂时不可用", AiClient().test(endpoint()).summary)
        assertEquals(3, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
        assertEquals("地址或模型 ID 不存在", AiClient().test(endpoint()).summary)
    }

    @Test fun `network causes map to plain reasons`() {
        val wrapped = AiException("网络请求失败", cause = UnknownHostException("x"))
        assertEquals("无法解析服务地址", ConnectionDiagnosis.failure(wrapped, false).summary)
        assertEquals("连接超时", ConnectionDiagnosis.timeout(30).summary)
    }
}
