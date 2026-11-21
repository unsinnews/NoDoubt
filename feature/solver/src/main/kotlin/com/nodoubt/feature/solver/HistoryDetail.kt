package com.nodoubt.feature.solver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.Usage
import java.util.Calendar

@Composable
internal fun HistoryDetail(entry: HistoryEntry, fontSize: Int, onBack: () -> Unit, onDelete: () -> Unit) {
    val tokens = LocalNoDoubtTokens.current
    Page("解题记录", subtitle = "${dayLabel(entry.createdAt)} ${timeLabel(entry.createdAt)}", onBack = onBack,
        actions = { ToolbarIcon(UiSymbol.TRASH, "删除记录", onDelete, danger = true) }) {
        Surface(Modifier.testTag("history-detail"), shape = RoundedCornerShape(16.dp), color = tokens.raised,
            border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))) {
            Column(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().background(tokens.inset).padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HistoryHeader("题目") { CopyChip("题目", "复制题目", entry.question, "history-copy-question", tokens.raised) }
                    RichText(entry.question, Modifier.padding(end = 6.dp), fontSize = fontSize, compact = true)
                }
                entry.answers.forEach { answer ->
                    GroupDivider()
                    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HistoryHeader(answer.usage.modeLabel, answer.model) {
                            CopyChip("答案", "复制${answer.usage.modeLabel}答案", answer.text,
                                "history-copy-${answer.usage.name.lowercase()}")
                        }
                        RichText(answer.text, Modifier.padding(end = 6.dp), fontSize = fontSize, compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(title: String, detail: String? = null, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action()
    }
}

private val Usage.modeLabel get() = if (this == Usage.DEEP) "深度思考" else "极速解题"
internal val Usage.shortLabel get() = if (this == Usage.DEEP) "深度" else "极速"

/** One readable line for the list: drop Markdown/LaTeX markers and collapse whitespace. */
internal fun plainPreview(text: String): String = text
    .replace(Regex("""\\frac\{([^{}]*)\}\{([^{}]*)\}"""), "$1/$2")
    .replace(Regex("""\\[()\[\]]|\$+|\*\*|__|`|^#+\s*""", RegexOption.MULTILINE), "")
    .replace(Regex("""\\[a-zA-Z]+\s?"""), "")
    .replace(Regex("""[{}]"""), "")
    .replace(Regex("""\s+"""), " ").trim().ifBlank { "（空题目）" }

internal fun dayLabel(millis: Long, now: Long = System.currentTimeMillis()): String {
    val day = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val month = day.get(Calendar.MONTH) + 1
    val date = day.get(Calendar.DAY_OF_MONTH)
    return when {
        sameDay(day, today) -> "今天"
        sameDay(day, yesterday) -> "昨天"
        day.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> "${month}月${date}日"
        else -> "${day.get(Calendar.YEAR)}年${month}月${date}日"
    }
}

internal fun timeLabel(millis: Long): String {
    val time = Calendar.getInstance().apply { timeInMillis = millis }
    return "%02d:%02d".format(time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE))
}
