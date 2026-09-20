package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManagementSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                QuietButton("取消", onDismiss)
            }
            content()
        }
    }
}

internal data class ManagementAction(val title: String, val onClick: () -> Unit, val enabled: Boolean = true, val danger: Boolean = false)

@Composable
internal fun ManagementMoreMenu(description: String, active: Boolean, enabled: Boolean = true, actions: List<ManagementAction>) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) expanded = false }
    Box {
        ToolbarIcon(UiSymbol.MORE, description, { expanded = true }, enabled)
        DropdownMenu(expanded && active, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(text = { Text(action.title,
                    color = if (action.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
                    onClick = { expanded = false; action.onClick() }, enabled = action.enabled)
            }
        }
    }
}
