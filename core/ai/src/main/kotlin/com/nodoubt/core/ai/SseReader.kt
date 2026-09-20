package com.nodoubt.core.ai

import okio.BufferedSource

internal data class SseFrame(val event: String?, val data: String)

/** An SSE event is delimited by an empty line, not by a TCP read or a data line. */
internal class SseReader(private val source: BufferedSource) {
    suspend fun consume(onFrame: suspend (SseFrame) -> Boolean) {
        var event: String? = null
        val lines = mutableListOf<String>()
        var firstLine = true
        while (true) {
            val raw = source.readUtf8Line() ?: break
            val line = if (firstLine) raw.removePrefix("\uFEFF") else raw
            firstLine = false
            if (line.isEmpty()) {
                if (lines.isNotEmpty() && !onFrame(SseFrame(event, lines.joinToString("\n")))) return
                event = null
                lines.clear()
                continue
            }
            if (line.startsWith(':')) continue
            val key = line.substringBefore(':')
            val value = line.substringAfter(':', "").removePrefix(" ")
            when (key) {
                "data" -> lines.add(value)
                "event" -> event = value
            }
        }
        if (lines.isNotEmpty()) onFrame(SseFrame(event, lines.joinToString("\n")))
    }
}
