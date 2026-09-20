package com.nodoubt.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val tokens = LocalNoDoubtTokens.current
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = tokens.action, contentColor = tokens.onAction)) { Text(text) }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    TextButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.textButtonColors(contentColor = if (danger) MaterialTheme.colorScheme.error else LocalNoDoubtTokens.current.foregroundAccent)) { Text(text) }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.textButtonColors(contentColor = LocalNoDoubtTokens.current.foregroundAccent)) { Text(text) }
}

@Composable
fun Field(
    label: String, value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier, hint: String = "", secret: Boolean = false,
    multiline: Boolean = false, error: String? = null, enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    var revealed by remember { mutableStateOf(false) }
    val input: @Composable (Modifier) -> Unit = { inputModifier ->
        BasicTextField(value, onValueChange, inputModifier.semantics { contentDescription = label }, enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = !multiline, minLines = if (multiline) 3 else 1, maxLines = if (multiline) 8 else 1,
            visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType),
            cursorBrush = SolidColor(LocalNoDoubtTokens.current.foregroundAccent),
            decorationBox = { inner -> Box {
                if (value.isEmpty()) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f),
                    style = MaterialTheme.typography.bodyLarge, maxLines = if (multiline) 3 else 1)
                inner()
            } })
    }
    Column(modifier.fillMaxWidth()) {
        if (multiline) Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Caption(label)
            input(Modifier.fillMaxWidth())
        } else Row(Modifier.fillMaxWidth().heightIn(min = 55.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.widthIn(min = 78.dp, max = 112.dp), style = MaterialTheme.typography.bodyLarge)
            input(Modifier.weight(1f).padding(vertical = 12.dp))
            if (secret && value.isNotEmpty()) QuietButton(if (revealed) "隐藏" else "显示", { revealed = !revealed })
        }
        if (error != null) Text(error, Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium)
        GroupDivider()
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, description: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Caption(description)
        }
        Switch(checked, onCheckedChange, colors = SwitchDefaults.colors(
            checkedTrackColor = LocalNoDoubtTokens.current.action, checkedThumbColor = Color.White,
            uncheckedTrackColor = MaterialTheme.colorScheme.outline, uncheckedThumbColor = Color.White,
            uncheckedBorderColor = Color.Transparent))
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String = "确认", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (!LocalScreenActive.current) return
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        shape = MaterialTheme.shapes.large,
        confirmButton = { QuietButton(confirm, onConfirm) }, dismissButton = { QuietButton("取消", onDismiss) })
}
