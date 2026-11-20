package com.nodoubt.core.ai

import com.google.gson.JsonParser
import com.nodoubt.core.model.AiEvent
import com.nodoubt.core.model.AiMessage
import com.nodoubt.core.model.AiRequest
import com.nodoubt.core.model.ModelConfig
import com.nodoubt.core.model.ModelEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AiClient(private val httpClient: OkHttpClient = OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).build()) {
    fun stream(request: AiRequest): Flow<AiEvent> = channelFlow {
        val body = ProtocolRequests.encode(request)
        val builder = Request.Builder().url(EndpointUrls.stream(request.endpoint))
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        EndpointUrls.headers(builder, request.endpoint)
        RequestHeaders.apply(builder, request.endpoint, body)
        request.endpoint.headers.forEach { (name, value) -> builder.header(name, value) }
        val client = clientFor(request.endpoint)
        val activeCall = AtomicReference<okhttp3.Call?>()
        val worker = launch(Dispatchers.IO) {
            try {
                var attempt = 0
                var emitted = false
                val retries = (request.endpoint.parameters.number("maxRetries") ?: DEFAULT_RETRIES).coerceIn(0, 10)
                while (true) {
                    val call = client.newCall(builder.build())
                    activeCall.set(call)
                    currentCoroutineContext().ensureActive()
                    try {
                        call.execute().use { response ->
                            val responseBody = response.body ?: throw AiException("服务端未返回响应体", response.code)
                            if (!response.isSuccessful) throw responseError(responseBody.string(), response.code)
                            val decoder = StreamDecoder(request.endpoint) { emitted = true; send(it) }
                            val mediaType = responseBody.contentType()?.subtype.orEmpty()
                            if (mediaType.contains("json")) {
                                val json = runCatching { JsonParser.parseString(responseBody.string()).asJsonObject }
                                    .getOrElse { throw AiException("服务端返回了无效 JSON", cause = it) }
                                decoder.document(json)
                            } else {
                                SseReader(responseBody.source()).consume(decoder::frame)
                                decoder.finish()
                            }
                        }
                        break
                    } catch (failure: AiException) {
                        val retryable = failure.status in setOf(408, 429, 500, 502, 503, 504)
                        if (emitted || !retryable || attempt >= retries) throw failure
                        val maximum = request.endpoint.parameters.number("maxRetryDelayMs") ?: 60000
                        val pause = (500L * (1L shl attempt)).coerceAtMost(if (maximum > 0) maximum.toLong() else 60000L)
                        attempt++
                        delay(pause)
                    }
                }
                close()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                close(if (error is AiException) error else AiException(error.message ?: "网络请求失败", cause = error))
            }
        }
        awaitClose { activeCall.get()?.cancel(); worker.cancel() }
    }

    suspend fun listModels(endpoint: ModelEndpoint, catalogUrl: String? = null): List<ModelConfig> =
        ModelDirectory(clientFor(endpoint)).list(endpoint, catalogUrl)

    /** Sends one short message without retries and reports latency and a human-readable diagnosis. */
    suspend fun test(endpoint: ModelEndpoint): ConnectionResult {
        val result = StringBuilder()
        var thinking = false
        val seconds = (endpoint.parameters.number("timeoutMs")?.div(1000)?.toLong() ?: 30L).coerceIn(5L, 120L)
        val probe = endpoint.copy(parameters = endpoint.parameters.deepCopy().apply { addProperty("maxRetries", 0) })
        val started = System.nanoTime()
        return try {
            withTimeout(seconds * 1000) {
                stream(AiRequest(probe, listOf(AiMessage("user", "Reply with OK.")))).collect { event ->
                    if (event is AiEvent.TextDelta) result.append(event.text)
                    if (event is AiEvent.ThinkingDelta && event.text.isNotBlank()) thinking = true
                }
            }
            ConnectionDiagnosis.success(result.toString(), thinking, (System.nanoTime() - started) / 1_000_000)
        } catch (timeout: TimeoutCancellationException) {
            ConnectionDiagnosis.timeout(seconds)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            ConnectionDiagnosis.failure(error, endpoint.apiKey.isBlank())
        }
    }

    private companion object { const val DEFAULT_RETRIES = 2 }

    private fun clientFor(endpoint: ModelEndpoint): OkHttpClient {
        val timeout = endpoint.parameters.number("timeoutMs") ?: return httpClient
        require(timeout > 0) { "超时时间必须大于 0 毫秒" }
        return httpClient.newBuilder().callTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeout.toLong(), TimeUnit.MILLISECONDS).build()
    }
}
