package com.nodoubt.core.config

import com.nodoubt.core.model.RemoteSource
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class RemoteDocument(val json: String?, val source: RemoteSource)

internal class RemoteConfigs {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).followSslRedirects(false).build()

    suspend fun fetch(url: String, previous: RemoteSource? = null): RemoteDocument = suspendCancellableCoroutine { continuation ->
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null || parsed.username.isNotBlank() || parsed.password.isNotBlank()) {
            continuation.resumeWithException(IllegalArgumentException("远程配置必须是有效的 HTTP/HTTPS 地址，不可包含用户名和密码"))
            return@suspendCancellableCoroutine
        }
        val request = Request.Builder().url(parsed).header("Accept", "application/json").apply {
            previous?.etag?.let { header("If-None-Match", it) }
            previous?.lastModified?.let { header("If-Modified-Since", it) }
        }.build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("无法获取远程配置，请检查地址和网络连接", e))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    if (it.code == 304) return@use RemoteDocument(null, previous ?: RemoteSource(url))
                    require(it.isSuccessful) { "远程配置请求失败（HTTP ${it.code}）" }
                    val body = it.body ?: error("远程配置为空")
                    require(body.contentLength() <= MAX_BYTES) { "远程配置不能超过 2 MB" }
                    val bytes = ByteArrayOutputStream()
                    body.byteStream().use { stream ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            require(bytes.size() + count <= MAX_BYTES) { "远程配置不能超过 2 MB" }
                            bytes.write(buffer, 0, count)
                        }
                    }
                    RemoteDocument(bytes.toString("UTF-8"), RemoteSource(url, System.currentTimeMillis(),
                        it.header("ETag"), it.header("Last-Modified")))
                } }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }

    companion object { private const val MAX_BYTES = 2 * 1024 * 1024 }
}
