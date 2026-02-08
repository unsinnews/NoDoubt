package com.yy.perfectfloatwindow.data

data class Answer(
    val questionId: Int,
    var text: String = "",
    var thinkingText: String = "",
    var isThinking: Boolean = false,
    var thinkingExpanded: Boolean = true,
    var thinkingStartAtMs: Long = 0L,
    var thinkingDurationMs: Long = 0L,
    var isComplete: Boolean = false,
    var isStopped: Boolean = false,
    var error: String? = null
)
