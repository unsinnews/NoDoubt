package com.nodoubt.core.ai

import com.nodoubt.core.model.AiMessage
import com.nodoubt.core.model.AiRequest
import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.Protocol
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CancellationTest {
    @Test fun `cancelling a stalled response closes its OkHttp call promptly`() = runBlocking {
        val server = MockWebServer()
        server.start()
        val cancelled = CountDownLatch(1)
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun canceled(call: Call) { cancelled.countDown() }
        }).build()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val endpoint = ModelEndpoint(Protocol.OPENAI_COMPLETIONS, server.url("/v1").toString(), "model")
            val failure = runCatching {
                withTimeout(300) { AiClient(client).stream(AiRequest(endpoint, listOf(AiMessage("user", "hello")))).toList() }
            }.exceptionOrNull()
            assertTrue(failure is TimeoutCancellationException)
            assertTrue("The HTTP call must be cancelled with the collecting coroutine", cancelled.await(2, TimeUnit.SECONDS))
        } finally {
            server.shutdown()
        }
    }
}
