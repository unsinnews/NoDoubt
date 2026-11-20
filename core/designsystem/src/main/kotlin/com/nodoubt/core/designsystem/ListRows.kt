package com.nodoubt.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The shared list row for editable collections: optional drag handle, title/subtitle, extra trailing content
 * and a trash icon. Tapping the row opens it; the icon deletes.
 */
@Composable
fun EditableRow(
    title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, onDelete: (() -> Unit)? = null,
    handle: Boolean = false, badge: String? = null, subtitleError: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    Column {
        Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = if (subtitle.isNullOrBlank()) 52.dp else 62.dp).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (handle) DragHandle()
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (badge != null) Badge(badge)
                }
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = if (subtitleError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing?.invoke()
            if (onDelete != null) DeleteIcon("删除$title", onDelete)
            else if (onClick != null && trailing == null) Symbol(UiSymbol.CHEVRON, Modifier.size(16.dp))
        }
        GroupDivider()
    }
}

/** A quiet 48dp trailing icon for list rows, e.g. delete or rename. */
@Composable
fun RowIcon(symbol: UiSymbol, description: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(48.dp).semantics { contentDescription = description }) {
        Symbol(symbol, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .8f))
    }
}

@Composable
fun DeleteIcon(description: String, onClick: () -> Unit) = RowIcon(UiSymbol.TRASH, description, onClick)

/** Three short bars signalling that a row can be long-pressed and dragged. */
@Composable
fun DragHandle(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f)
    Canvas(modifier.size(18.dp)) {
        val stroke = 1.6.dp.toPx()
        listOf(.32f, .5f, .68f).forEach { y ->
            drawLine(color, Offset(size.width * .15f, size.height * y),
                Offset(size.width * .85f, size.height * y), stroke,
                StrokeCap.Round)
        }
    }
}

/** A small tinted label, e.g. 默认. */
@Composable
fun Badge(text: String) {
    val accent = LocalNoDoubtTokens.current.foregroundAccent
    Text(text, Modifier.background(accent.copy(alpha = .12f), RoundedCornerShape(6.dp))
        .padding(horizontal = 6.dp, vertical = 1.dp), color = accent, style = MaterialTheme.typography.labelMedium, maxLines = 1)
}

/** A one-line notice with an undo action, shown after an immediately-saved removal. */
@Composable
fun UndoBar(text: String, onUndo: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(LocalNoDoubtTokens.current.raised, RoundedCornerShape(12.dp))
        .padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        QuietButton("撤销", onUndo)
    }
}
