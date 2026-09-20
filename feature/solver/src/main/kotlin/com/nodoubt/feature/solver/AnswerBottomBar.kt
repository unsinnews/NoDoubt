package com.nodoubt.feature.solver

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.GroupDivider
import com.nodoubt.core.designsystem.LocalNoDoubtTokens

/** Fixed action positions and one status line leave the rest of the sheet for answers. */
@Composable
internal fun AnswerBottomBar(
    status: String, running: Boolean, onCapture: () -> Unit, onStop: () -> Unit, onDismiss: () -> Unit
) {
    val tokens = LocalNoDoubtTokens.current
    val colors = ButtonDefaults.textButtonColors(contentColor = tokens.foregroundAccent)
    Surface(Modifier.fillMaxWidth().testTag("answer-bottom-bar"), color = tokens.raised) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            GroupDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onCapture, Modifier.widthIn(min = 72.dp).heightIn(min = 48.dp).testTag("capture-again"),
                    colors = colors, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("重截", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Text(status, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(if (running) onStop else onDismiss,
                    Modifier.widthIn(min = 72.dp).heightIn(min = 48.dp).testTag(if (running) "stop-answer" else "dismiss-answer")
                        .semantics { contentDescription = if (running) "停止当前模式" else "关闭解答" },
                    colors = colors, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    val color = LocalContentColor.current
                    Canvas(Modifier.size(14.dp)) {
                        if (running) drawRoundRect(color, Offset(size.width * .15f, size.height * .15f),
                            Size(size.width * .7f, size.height * .7f), CornerRadius(1.5.dp.toPx()))
                        else {
                            drawLine(color, Offset(size.width * .15f, size.height * .35f),
                                Offset(size.width * .5f, size.height * .7f), 1.7.dp.toPx(), StrokeCap.Round)
                            drawLine(color, Offset(size.width * .5f, size.height * .7f),
                                Offset(size.width * .85f, size.height * .35f), 1.7.dp.toPx(), StrokeCap.Round)
                        }
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(if (running) "停止" else "收起", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}
