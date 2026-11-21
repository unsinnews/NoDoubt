package com.nodoubt.feature.solver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.AppConfig
import com.nodoubt.core.model.ModelRef
import com.nodoubt.core.model.ThinkingOptions
import com.nodoubt.core.model.ThinkingOverride
import com.nodoubt.core.model.Usage

@Composable
internal fun QuestionAnswerCard(
    question: QuestionState, usage: Usage, recognition: RunStatus, config: AppConfig,
    thinkingExpanded: Boolean, onThinkingExpandedChange: (Boolean) -> Unit,
    onRetry: () -> Unit, onModel: (ModelRef) -> Unit,
    thinkingOptions: ThinkingOptions?, onRetryWithThinking: (ThinkingOverride?) -> Result<Unit>,
    onInputActiveChange: (Boolean) -> Unit, editorSession: Int, retryError: String?
) {
    val answer = question.answers.getValue(usage)
    val finished = answer.status in setOf(RunStatus.COMPLETE, RunStatus.ERROR, RunStatus.STOPPED)
    val canRetry = question.ready && (finished || answer.text.isNotBlank() || answer.thinking.isNotBlank())
    val tokens = LocalNoDoubtTokens.current
    Surface(modifier = Modifier.testTag("answer-${usage.name.lowercase()}-${question.id}"),
        shape = RoundedCornerShape(16.dp), color = tokens.raised,
        border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))) {
        Column(Modifier.fillMaxWidth()) {
            val copyable = question.ready && question.text.isNotBlank()
            Column(Modifier.fillMaxWidth().background(tokens.inset)
                .padding(start = 14.dp, end = 8.dp, top = if (copyable) 4.dp else 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("题目 ${question.id}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (copyable) key(question.id) { CopyChip("题目", "复制题目", question.text.trim(), "copy-question", tokens.raised) }
                }
                RichText(question.text, Modifier.padding(end = 6.dp), fontSize = config.preferences.answerFontSize,
                    compact = true)
                if (!question.ready) {
                    InlineStatus(if (recognition == RunStatus.RUNNING) "正在识别题目…" else "识别未完成",
                        loading = recognition == RunStatus.RUNNING)
                }
            }
            if (question.ready) {
                GroupDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 4.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    key(editorSession) {
                        AnswerThinkingControl(question.id, usage, answer, thinkingOptions, onRetryWithThinking, onInputActiveChange,
                            modelSelector = { modifier ->
                                AnswerModelPicker(answer, question.id, usage, config, onModel, modifier) {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("解答", style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        AnswerStatusLabel(answer.status)
                                    }
                                }
                            }, trailingAction = { if (canRetry && !finished) QuietButton("重试", onRetry) })
                    }
                    if (!finished) AnswerRetryError(retryError)
                    key(answer.runId) {
                        AnswerContent(answer, config.preferences.answerFontSize, thinkingExpanded, onThinkingExpandedChange)
                    }
                    if (finished) AnswerRetryError(retryError)
                    if (finished) AnswerActions(question, usage, onRetry)
                }
            }
        }
    }
}

@Composable
private fun AnswerRetryError(message: String?) {
    if (message != null) Box(Modifier.testTag("answer-retry-error")) { InlineStatus(message, error = true) }
}

@Composable
private fun AnswerStatusLabel(status: RunStatus) {
    if (status == RunStatus.COMPLETE) return
    val label = when (status) {
        RunStatus.IDLE -> "排队中"
        RunStatus.RUNNING -> "生成中"
        RunStatus.COMPLETE -> ""
        RunStatus.STOPPED -> "已停止"
        RunStatus.ERROR -> "请求错误"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (status == RunStatus.RUNNING) CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.2.dp,
            color = LocalNoDoubtTokens.current.foregroundAccent)
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (status == RunStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
