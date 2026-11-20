package com.nodoubt.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

val LocalScreenActive = staticCompositionLocalOf { true }

/**
 * The single in-app bottom sheet: 取消 | centered title | optional confirm action.
 * Short choices apply on tap and omit [confirm]; forms and multi-selection confirm in the header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(
    title: String, onDismiss: () -> Unit,
    confirm: String? = null, onConfirm: () -> Unit = {}, confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!LocalScreenActive.current) return
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), dragHandle = {
            Box(Modifier.padding(top = 8.dp, bottom = 2.dp).size(36.dp, 5.dp)
                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(50)))
        }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { SheetButton("取消", onDismiss) }
            Text(title, Modifier.weight(1.6f), style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (confirm != null) SheetButton(confirm, onConfirm, confirmEnabled, primary = true)
            }
        }
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
private fun SheetButton(text: String, onClick: () -> Unit, enabled: Boolean = true, primary: Boolean = false) {
    TextButton(onClick, Modifier.heightIn(min = 48.dp), enabled = enabled, shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = LocalNoDoubtTokens.current.foregroundAccent)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal)
    }
}

data class DialogAction(val label: String, val onClick: () -> Unit, val danger: Boolean = false, val primary: Boolean = false)

/**
 * iOS-style alert: centered title and message, full-width actions split by hairlines.
 * Two actions sit side by side; three or more stack vertically in the given order.
 */
@Composable
fun MessageDialog(title: String, message: String?, actions: List<DialogAction>, onDismiss: () -> Unit) {
    if (!LocalScreenActive.current) return
    Dialog(onDismiss) {
        Surface(Modifier.width(290.dp), shape = RoundedCornerShape(18.dp), color = LocalNoDoubtTokens.current.raised) {
            Column {
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    if (!message.isNullOrBlank()) Text(message, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                val line = MaterialTheme.colorScheme.outlineVariant
                HorizontalDivider(thickness = .5.dp, color = line)
                if (actions.size == 2) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    DialogButton(actions[0], Modifier.weight(1f))
                    VerticalDivider(thickness = .5.dp, color = line)
                    DialogButton(actions[1], Modifier.weight(1f))
                } else actions.forEachIndexed { index, action ->
                    if (index > 0) HorizontalDivider(thickness = .5.dp, color = line)
                    DialogButton(action, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DialogButton(action: DialogAction, modifier: Modifier) {
    val color = if (action.danger) MaterialTheme.colorScheme.error else LocalNoDoubtTokens.current.foregroundAccent
    Box(modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = action.onClick),
        contentAlignment = Alignment.Center) {
        Text(action.label, Modifier.padding(horizontal = 12.dp), color = color, style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (action.primary) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
fun ConfirmDialog(
    title: String, message: String, confirm: String = "确认", onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false
) = MessageDialog(title, message, listOf(DialogAction("取消", onDismiss), DialogAction(confirm, onConfirm, danger, primary = !danger)), onDismiss)
