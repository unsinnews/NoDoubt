package com.nodoubt.feature.solver

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.Usage

@Composable
internal fun AnswerActions(
    question: QuestionState, usage: Usage, onRetry: () -> Unit, onCopied: () -> Unit
) {
    val answer = question.answers.getValue(usage)
    var showCopy by remember(question.id, usage, answer.runId) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    GroupDivider()
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End) {
        Box {
            AnswerActionButton("复制") { showCopy = true }
            DropdownMenu(showCopy, { showCopy = false }, properties = PopupProperties(focusable = false)) {
                val answerText = (answer.error?.let { "错误: $it" } ?: answer.text).trim()
                val questionText = question.text.trim()
                val combined = buildList {
                    if (questionText.isNotBlank()) add("题目：\n$questionText")
                    if (answerText.isNotBlank()) add("解答：\n$answerText")
                }.joinToString("\n\n")
                listOf(
                    "仅题目" to questionText,
                    "仅解答" to answerText,
                    "题与解" to combined
                ).forEach { (label, text) ->
                    DropdownMenuItem(text = { Text(label) }, enabled = text.isNotBlank(), onClick = {
                        clipboard.setText(AnnotatedString(text))
                        showCopy = false
                        onCopied()
                    })
                }
            }
        }
        AnswerActionButton("重试", onRetry)
    }
}

@Composable
private fun AnswerActionButton(label: String, onClick: () -> Unit) {
    TextButton(onClick, Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = LocalNoDoubtTokens.current.foregroundAccent)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}
