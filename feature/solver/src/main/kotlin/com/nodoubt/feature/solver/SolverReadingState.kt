package com.nodoubt.feature.solver

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.mapSaver
import com.nodoubt.core.model.Usage

/** Owned above the selected mode so replacing its content cannot discard reading state. */
@Stable
internal class SolverReadingState(fastPosition: Int = 0, deepPosition: Int = 0) {
    private val fastScroll = ScrollState(fastPosition)
    private val deepScroll = ScrollState(deepPosition)
    /** Thinking starts as a short scrolling preview; only answers the user opened are listed here. */
    private val expanded = mutableStateMapOf<String, Boolean>()

    fun scrollFor(usage: Usage): ScrollState = if (usage == Usage.FAST) fastScroll else deepScroll

    fun isThinkingExpanded(usage: Usage, questionId: Int, runId: Int): Boolean =
        expanded[answerKey(usage, questionId, runId)] == true

    fun setThinkingExpanded(usage: Usage, questionId: Int, runId: Int, expanded: Boolean) {
        val prefix = "${usage.name}:$questionId:"
        this.expanded.keys.filter { it.startsWith(prefix) }.forEach(this.expanded::remove)
        if (expanded) this.expanded[answerKey(usage, questionId, runId)] = true
    }

    private fun answerKey(usage: Usage, questionId: Int, runId: Int) = "${usage.name}:$questionId:$runId"

    companion object {
        val Saver = mapSaver(
            save = { state: SolverReadingState ->
                buildMap<String, Any> {
                    put("fast", state.fastScroll.value)
                    put("deep", state.deepScroll.value)
                    state.expanded.keys.forEach { put("answer:$it", true) }
                }
            },
            restore = { saved ->
                SolverReadingState(saved["fast"] as Int, saved["deep"] as Int).apply {
                    saved.keys.filter { it.startsWith("answer:") }.forEach {
                        expanded[it.removePrefix("answer:")] = true
                    }
                }
            }
        )
    }
}
