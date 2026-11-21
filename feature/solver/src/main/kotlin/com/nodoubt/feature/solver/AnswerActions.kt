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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.Usage
import kotlinx.coroutines.delay

/**
 * Icon actions under a finished answer: copy the answer, copy question and answer together, retry.
 * Each copy confirms in place for a moment instead of opening a menu.
 */
@Composable
internal fun AnswerActions(question: QuestionState, usage: Usage, onRetry: () -> Unit) {
    val answer = question.answers.getValue(usage)
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var copied by remember(question.id, usage, answer.runId) { mutableStateOf<String?>(null) }
    LaunchedEffect(copied) { if (copied != null) { delay(1600); copied = null } }
    val answerText = (answer.error?.let { "错误：$it" } ?: answer.text).trim()
    val questionText = question.text.trim()
    val both = listOf("题目：\n$questionText".takeIf { questionText.isNotBlank() },
        "解答：\n$answerText".takeIf { answerText.isNotBlank() }).filterNotNull().joinToString("\n\n")
    fun copy(key: String, text: String) {
        clipboard.setText(AnnotatedString(text))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        copied = key
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ActionChip(UiSymbol.COPY, "复制", copied == "answer", "copy-answer", enabled = answerText.isNotBlank()) {
            copy("answer", answerText)
        }
        ActionChip(UiSymbol.COPY, "题与解", copied == "both", "copy-both", enabled = both.isNotBlank()) {
            copy("both", both)
        }
        Spacer(Modifier.weight(1f))
        ActionChip(UiSymbol.REFRESH, "重试", false, "retry-answer", onClick = onRetry)
    }
}

/** A quiet icon + label pill; after copying it briefly shows a check and 已复制. */
@Composable
private fun ActionChip(symbol: UiSymbol, label: String, done: Boolean, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
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
        .semantics { contentDescription = if (done) "已复制" else label }.testTag(tag),
        contentAlignment = Alignment.Center) {
        // The ripple follows the pill, not the larger touch target.
        Row(Modifier.clip(pill).background(if (done) tokens.success.copy(alpha = .1f) else tokens.inset)
            .indication(source, LocalIndication.current)
            .padding(start = 9.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AnimatedContent(done, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "copy") { isDone ->
                Symbol(if (isDone) UiSymbol.CHECK else symbol, Modifier.size(15.dp), color)
            }
            // Both labels share one slot so 已复制 does not push neighbouring chips.
            Box(contentAlignment = Alignment.Center) {
                Text(label, Modifier.hiddenWhen(done), style = MaterialTheme.typography.labelLarge,
                    color = color, maxLines = 1)
                if (symbol == UiSymbol.COPY) Text("已复制", Modifier.hiddenWhen(!done),
                    style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
            }
        }
    }
}

/** Keeps the space but removes the text visually and from accessibility. */
private fun Modifier.hiddenWhen(hidden: Boolean) = if (hidden) alpha(0f).clearAndSetSemantics {} else this
