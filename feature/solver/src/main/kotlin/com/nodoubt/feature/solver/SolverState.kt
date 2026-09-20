package com.nodoubt.feature.solver

import com.nodoubt.core.model.ModelRef
import com.nodoubt.core.model.Usage
import com.nodoubt.core.model.ThinkingSnapshot
import com.nodoubt.core.model.ThinkingOverride

enum class RunStatus { IDLE, RUNNING, COMPLETE, STOPPED, ERROR }

data class AnswerState(
    val text: String = "",
    val thinking: String = "",
    val status: RunStatus = RunStatus.IDLE,
    val error: String? = null,
    val model: ModelRef? = null,
    val runId: Int = 0,
    val thinkingStartedAtMs: Long = 0,
    val thinkingFinishedAtMs: Long = 0,
    val thinkingSnapshot: ThinkingSnapshot? = null,
    val thinkingOverride: ThinkingOverride? = null
)

data class QuestionState(
    val id: Int,
    val text: String,
    val ready: Boolean = false,
    val answers: Map<Usage, AnswerState> = mapOf(Usage.FAST to AnswerState(), Usage.DEEP to AnswerState())
)

data class SolverState(
    val session: Long = 0,
    val questions: List<QuestionState> = emptyList(),
    val recognition: RunStatus = RunStatus.IDLE,
    val error: String? = null,
    val stoppedModes: Set<Usage> = emptySet()
)

internal fun AnswerState.appendText(delta: String): AnswerState =
    if (delta.isEmpty()) this else finishThinking().copy(text = text + delta)

internal fun AnswerState.appendThinking(delta: String): AnswerState =
    if (delta.isEmpty()) this else copy(thinking = thinking + delta,
        thinkingStartedAtMs = thinkingStartedAtMs.takeIf { it > 0 } ?: System.currentTimeMillis())

internal fun AnswerState.finishThinking(): AnswerState =
    if (thinkingStartedAtMs > 0 && thinkingFinishedAtMs == 0L) copy(thinkingFinishedAtMs = System.currentTimeMillis()) else this
