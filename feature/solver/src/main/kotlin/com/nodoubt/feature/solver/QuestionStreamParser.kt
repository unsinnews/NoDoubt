package com.nodoubt.feature.solver

/** Buffers only a possible delimiter suffix, so chunk boundaries cannot split a question. */
class QuestionStreamParser(
    private val onProgress: (Int, String) -> Unit,
    private val onQuestion: (Int, String) -> Unit
) {
    private val pending = StringBuilder()
    private var index = 1
    var hasContent = false
        private set
    var noQuestion = false
        private set

    fun append(delta: String) {
        if (delta.isNotEmpty()) hasContent = true
        pending.append(delta)
        while (true) {
            val boundary = pending.indexOf("|||")
            if (boundary < 0) break
            val text = pending.substring(0, boundary).trim()
            pending.delete(0, boundary + 3)
            if (text.isNotBlank()) {
                onProgress(index, text)
                onQuestion(index++, text)
            }
        }
        val held = when {
            pending.endsWith("||") -> 2
            pending.endsWith("|") -> 1
            else -> 0
        }
        val text = pending.substring(0, pending.length - held).trim()
        if (text.isNotBlank()) onProgress(index, text)
    }

    fun finish() {
        val text = pending.toString().trim()
        noQuestion = index == 1 && text.contains("未识别到有效题目")
        if (!noQuestion && text.isNotBlank()) {
            onProgress(index, text)
            onQuestion(index++, text)
        }
        pending.clear()
    }
}
