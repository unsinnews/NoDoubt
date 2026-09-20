package com.nodoubt.feature.solver

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.*

@Composable
internal fun AnswerContent(answer: AnswerState, fontSize: Int, thinkingExpanded: Boolean, onThinkingExpandedChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (answer.thinking.isNotBlank()) ThinkingDisclosure(answer, fontSize, thinkingExpanded, onThinkingExpandedChange)
        if (answer.text.isNotBlank()) RichText(answer.text, fontSize = fontSize, compact = true)
        when (answer.status) {
            RunStatus.RUNNING, RunStatus.IDLE, RunStatus.STOPPED -> Unit
            RunStatus.ERROR -> InlineStatus(answer.error, error = true)
            RunStatus.COMPLETE -> if (answer.text.isBlank()) Caption("未返回正文")
        }
    }
}
