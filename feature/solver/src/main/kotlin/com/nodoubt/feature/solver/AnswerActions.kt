package com.nodoubt.feature.solver

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.Usage
import kotlinx.coroutines.delay

/** Actions under a finished answer: copy the answer, retry. The question copies from its own header. */
@Composable
internal fun AnswerActions(question: QuestionState, usage: Usage, onRetry: () -> Unit) {
    val answer = question.answers.getValue(usage)
    val answerText = (answer.error?.let { "错误：$it" } ?: answer.text).trim()
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        key(question.id, usage, answer.runId) { CopyChip("答案", "复制答案", answerText, "copy-answer") }
        Spacer(Modifier.weight(1f))
        ActionChip(UiSymbol.REFRESH, "重试", "重试", false, "retry-answer", onClick = onRetry)
    }
}

/** ⧉ + what is copied; confirms in place with a check and 已复制 for a moment. */
@Composable
internal fun CopyChip(
    label: String, description: String, text: String, tag: String,
    container: Color = LocalNoDoubtTokens.current.inset
) {
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1600); copied = false } }
    ActionChip(UiSymbol.COPY, label, description, copied, tag, container, enabled = text.isNotBlank()) {
        clipboard.setText(AnnotatedString(text))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        copied = true
    }
}

@Composable
private fun ActionChip(
    symbol: UiSymbol, label: String, description: String, done: Boolean, tag: String,
    container: Color = LocalNoDoubtTokens.current.inset, enabled: Boolean = true, onClick: () -> Unit
) {
    val tokens = LocalNoDoubtTokens.current
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f)
        done -> tokens.success
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val source = remember { MutableInteractionSource() }
    val pill = RoundedCornerShape(50)
    Box(Modifier.heightIn(min = 44.dp)
        .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = if (done) "已复制" else description }.testTag(tag),
        contentAlignment = Alignment.Center) {
        // The ripple follows the pill, not the larger touch target.
        Row(Modifier.clip(pill).background(if (done) tokens.success.copy(alpha = .12f) else container)
            .indication(source, LocalIndication.current)
            .padding(start = 9.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AnimatedContent(done, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "copy") { isDone ->
                Symbol(if (isDone) UiSymbol.CHECK else symbol, Modifier.size(15.dp), color)
            }
            Text(if (done) "已复制" else label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
        }
    }
}
