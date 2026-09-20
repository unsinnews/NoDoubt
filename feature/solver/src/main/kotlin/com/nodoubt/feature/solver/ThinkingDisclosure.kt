package com.nodoubt.feature.solver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.*

@Composable
internal fun ThinkingDisclosure(answer: AnswerState, fontSize: Int, expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    val thinking = answer.thinkingStartedAtMs > 0 && answer.thinkingFinishedAtMs == 0L && answer.status == RunStatus.RUNNING
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(thinking, answer.thinkingStartedAtMs) {
        while (thinking) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(500) }
    }
    val tokens = LocalNoDoubtTokens.current
    val tint = tokens.foregroundAccent.copy(alpha = .025f).compositeOver(tokens.raised)
    Column(Modifier.fillMaxWidth().background(tint, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics {
                contentDescription = if (expanded) "收起思考过程" else "展开思考过程"
                stateDescription = if (expanded) "已展开" else "已折叠"
            }
            .clickable(role = Role.Button) { onExpandedChange(!expanded) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (thinking) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp,
                color = tokens.foregroundAccent)
            Text(if (thinking) "思考中…" else "思考", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (answer.thinkingStartedAtMs > 0) {
                val end = answer.thinkingFinishedAtMs.takeIf { it > 0 } ?: now
                val seconds = (end - answer.thinkingStartedAtMs).coerceAtLeast(0) / 1000
                if (seconds > 0) Caption("${seconds}s")
            }
            Symbol(UiSymbol.CHEVRON, Modifier.size(14.dp).rotate(if (expanded) 90f else 0f))
        }
        if (expanded) Box(Modifier.fillMaxWidth().testTag("thinking-content").padding(bottom = 10.dp)) {
            RichText(answer.thinking, fontSize = (fontSize - 1).coerceAtLeast(12), compact = true)
        }
    }
}
