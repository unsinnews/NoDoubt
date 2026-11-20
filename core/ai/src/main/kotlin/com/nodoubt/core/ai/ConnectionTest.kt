package com.nodoubt.core.ai

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Outcome of a connection test, phrased for people rather than for logs. */
data class ConnectionResult(val level: Level, val summary: String, val detail: String? = null, val millis: Long = 0) {
    enum class Level { OK, WARNING, ERROR }
    val ok get() = level != Level.ERROR
}

internal object ConnectionDiagnosis {
    fun success(text: String, thinking: Boolean, millis: Long): ConnectionResult {
        val reply = text.trim().replace(Regex("\\s+"), " ")
        return when {
            reply.isNotEmpty() -> ConnectionResult(ConnectionResult.Level.OK, "连接正常",
                "回复「${reply.take(24)}${if (reply.length > 24) "…" else ""}」" + if (thinking) "，含思考内容" else "", millis)
            thinking -> ConnectionResult(ConnectionResult.Level.WARNING, "已连接，只返回了思考内容",
                "可能是输出上限过小", millis)
            else -> ConnectionResult(ConnectionResult.Level.WARNING, "已连接，但回复为空", "请检查模型 ID 与参数", millis)
        }
    }

    fun timeout(seconds: Long) = ConnectionResult(ConnectionResult.Level.ERROR, "连接超时", "${seconds} 秒内没有完成响应")

    fun failure(error: Throwable, apiKeyBlank: Boolean): ConnectionResult {
        val status = (error as? AiException)?.status
        val network = if (status == null) networkSummary(error) else null
        // Socket messages are long, English and add nothing to the plain reason.
        val detail = if (network != null) null else error.message?.takeIf { it.isNotBlank() }?.take(160)
        val summary = when {
            status == 401 && apiKeyBlank -> "未填写 API Key"
            status == 401 -> "API Key 无效"
            status == 403 -> "没有访问权限"
            status == 404 -> "地址或模型 ID 不存在"
            status == 400 || status == 422 -> "请求被拒绝，请检查参数"
            status == 402 || status == 429 -> "额度不足或请求过多"
            status != null && status >= 500 -> "服务端暂时不可用"
            status != null -> "请求失败（HTTP $status）"
            else -> network ?: when (error) {
                is IllegalArgumentException, is IllegalStateException -> "配置有误"
                else -> "响应异常"
            }
        }
        val hint = when (network) { null -> null; "无法解析服务地址", "无法连接服务器" -> "请检查服务地址和网络"; else -> "请检查网络或稍后重试" }
        return ConnectionResult(ConnectionResult.Level.ERROR, summary, detail?.takeIf { it != summary } ?: hint)
    }

    private fun networkSummary(error: Throwable): String? = generateSequence(error) { it.cause }.take(6).firstNotNullOfOrNull {
        when (it) {
            is UnknownHostException -> "无法解析服务地址"
            is ConnectException -> "无法连接服务器"
            is SocketTimeoutException -> "连接超时"
            is SSLException -> "安全连接失败"
            is InterruptedIOException -> "连接超时"
            else -> null
        }
    }
}

/** Human-readable reason for a failed request such as a model-list fetch. */
fun describeFailure(error: Throwable, apiKeyBlank: Boolean): String =
    ConnectionDiagnosis.failure(error, apiKeyBlank).let { result -> listOfNotNull(result.summary, result.detail).joinToString("：") }
