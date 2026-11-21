package com.nodoubt.feature.solver

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.*

private enum class HistoryConfirm { SELECTED, ALL }

/**
 * 我的 → 解题记录: a day-grouped list. A single delete is immediate with undo; long-press or 选择 enters
 * multi-select, whose delete asks first; 清空 asks first and cannot be undone. Handles its own Back.
 */
@Composable
fun HistoryScreen(history: SolveHistory, fontSize: Int, onBack: () -> Unit) {
    val entries by history.entries.collectAsState()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var picked by rememberSaveable(saver = idSetSaver) { mutableStateOf(emptySet<String>()) }
    var removed by remember { mutableStateOf<List<Pair<Int, HistoryEntry>>?>(null) }
    var confirm by remember { mutableStateOf<HistoryConfirm?>(null) }
    val open = entries.firstOrNull { it.id == openId }
    // Records can disappear under a selection (e.g. the size limit); only count the ones still listed.
    val live = entries.mapNotNullTo(mutableSetOf()) { it.id.takeIf { id -> id in picked } }
    fun exitSelection() { selecting = false; picked = emptySet() }
    fun delete(ids: Set<String>) { removed = history.delete(ids).takeIf { it.isNotEmpty() }; openId = null }
    LaunchedEffect(entries.isEmpty()) { if (entries.isEmpty()) exitSelection() }
    BackHandler { when { open != null -> openId = null; selecting -> exitSelection(); else -> onBack() } }
    if (open != null) {
        HistoryDetail(open, fontSize, onBack = { openId = null }, onDelete = { delete(setOf(open.id)) })
        return
    }
    val all = entries.isNotEmpty() && live.size == entries.size
    Column(Modifier.fillMaxSize()) {
        Page(if (selecting) "已选 ${live.size} 项" else "解题记录", Modifier.weight(1f),
            onBack = { if (selecting) exitSelection() else onBack() }, actions = {
                if (selecting) QuietButton(if (all) "全不选" else "全选", {
                    picked = if (all) emptySet() else entries.mapTo(mutableSetOf()) { it.id }
                }) else if (entries.isNotEmpty()) {
                    QuietButton("选择", { selecting = true; removed = null })
                    ToolbarIcon(UiSymbol.TRASH, "清空记录", { confirm = HistoryConfirm.ALL }, danger = true)
                }
            }) {
            removed?.let { list -> UndoBar("已删除 ${list.size} 条记录") { history.restore(list); removed = null } }
            if (entries.isEmpty()) {
                Caption("暂无记录。解答完成后会自动保存题目和答案。", Modifier.padding(horizontal = 16.dp, vertical = 24.dp))
            }
            entries.groupBy { dayLabel(it.createdAt) }.forEach { (day, group) ->
                Section(day) {
                    group.forEach { entry ->
                        HistoryRow(entry, selecting, entry.id in live,
                            onOpen = { openId = entry.id; removed = null },
                            onToggle = { picked = if (entry.id in picked) picked - entry.id else picked + entry.id },
                            onSelectStart = { selecting = true; removed = null; picked = setOf(entry.id) },
                            onDelete = { delete(setOf(entry.id)) })
                    }
                }
            }
            if (entries.isNotEmpty()) Caption(if (selecting) "轻点选择记录。" else
                "长按可多选。不保存思考过程，最多保留 ${SolveHistory.MAX_ENTRIES} 条。", Modifier.padding(horizontal = 16.dp))
        }
        if (selecting) DeleteBar(live.size) { confirm = HistoryConfirm.SELECTED }
    }
    when (confirm) {
        HistoryConfirm.SELECTED -> ConfirmDialog("删除 ${live.size} 条记录？", "删除后可在本页撤销。", confirm = "删除",
            danger = true, onConfirm = { delete(live); exitSelection(); confirm = null }, onDismiss = { confirm = null })
        HistoryConfirm.ALL -> ConfirmDialog("清空解题记录？", "将删除全部 ${entries.size} 条记录，无法恢复。", confirm = "清空",
            danger = true, onConfirm = { history.clear(); removed = null; exitSelection(); confirm = null },
            onDismiss = { confirm = null })
        null -> Unit
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    entry: HistoryEntry, selecting: Boolean, checked: Boolean,
    onOpen: () -> Unit, onToggle: () -> Unit, onSelectStart: () -> Unit, onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val title = plainPreview(entry.question)
    val interaction = if (selecting) Modifier.toggleable(checked, role = Role.Checkbox) { onToggle() }
        else Modifier.combinedClickable(onLongClickLabel = "多选", onLongClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onSelectStart()
        }, onClick = onOpen)
    Column {
        Row(Modifier.fillMaxWidth().then(interaction).heightIn(min = 62.dp).testTag("history-row"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selecting) SelectMark(checked)
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${timeLabel(entry.createdAt)} · ${entry.answers.joinToString("、") { it.usage.shortLabel }}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!selecting) DeleteIcon("删除$title", onDelete)
        }
        GroupDivider()
    }
}

@Composable
private fun SelectMark(checked: Boolean) {
    val tokens = LocalNoDoubtTokens.current
    Box(Modifier.size(22.dp).then(if (checked) Modifier.background(tokens.foregroundAccent, CircleShape)
        else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)), contentAlignment = Alignment.Center) {
        if (checked) Symbol(UiSymbol.CHECK, Modifier.size(14.dp), tokens.onAction)
    }
}

@Composable
private fun DeleteBar(count: Int, onDelete: () -> Unit) {
    Surface(color = LocalNoDoubtTokens.current.raised) {
        Column {
            GroupDivider()
            QuietButton(if (count == 0) "删除" else "删除（$count）", onDelete,
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("history-delete-selected"),
                enabled = count > 0, danger = true)
        }
    }
}

private val idSetSaver = listSaver<MutableState<Set<String>>, String>(
    save = { it.value.toList() }, restore = { mutableStateOf(it.toSet()) })
