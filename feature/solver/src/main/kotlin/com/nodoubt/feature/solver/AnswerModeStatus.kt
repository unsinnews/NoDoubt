package com.nodoubt.feature.solver

import com.nodoubt.core.model.Usage

internal fun answerModeIsRunning(state: SolverState, usage: Usage): Boolean =
    state.recognition == RunStatus.RUNNING || state.questions.any { q -> q.ready &&
        (q.answers.getValue(usage).status == RunStatus.RUNNING ||
            (q.answers.getValue(usage).status == RunStatus.IDLE && usage !in state.stoppedModes)) }

/** Recognition cancellation in another mode must not label this mode as user-stopped. */
internal fun answerModeStatus(state: SolverState, usage: Usage): String {
    val answers = state.questions.filter { it.ready }.map { it.answers.getValue(usage) }
    return when {
        answers.any { it.status == RunStatus.RUNNING } -> "解答中…"
        state.recognition == RunStatus.RUNNING -> "正在识别题目…"
        usage in state.stoppedModes || answers.any { it.status == RunStatus.STOPPED } -> "已停止"
        answers.any { it.status == RunStatus.ERROR } -> "部分解答未完成"
        answers.any { it.status == RunStatus.IDLE } -> "等待解答…"
        state.recognition == RunStatus.ERROR -> "识别未完成"
        state.questions.any { !it.ready } -> "题目识别未完成"
        answers.isNotEmpty() && answers.all { it.status == RunStatus.COMPLETE } -> "已完成解答"
        state.recognition == RunStatus.COMPLETE -> "未识别到题目"
        else -> "等待截图"
    }
}
