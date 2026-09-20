package com.nodoubt.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun Page(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) ToolbarIcon(UiSymbol.BACK, "返回", onBack)
                else Spacer(Modifier.width(8.dp))
                if (onBack == null) Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                else Spacer(Modifier.weight(1f))
                actions()
            }
            if (onBack != null) Column(Modifier.align(Alignment.Center).padding(horizontal = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                content = content
            )
        }
    }
}

@Composable
fun Section(title: String, description: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (title.isNotBlank()) Text(title, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.fillMaxWidth().background(LocalNoDoubtTokens.current.raised, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 3.dp), content = content)
        if (!description.isNullOrBlank()) {
            Text(description, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun ItemRow(title: String, description: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val interaction = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column {
        Row(
            Modifier.fillMaxWidth().then(interaction).heightIn(min = if (description.isNullOrBlank()) 50.dp else 66.dp).padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!description.isNullOrBlank()) Text(description, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) trailing() else if (onClick != null) Symbol(UiSymbol.CHEVRON, Modifier.size(16.dp))
        }
        GroupDivider()
    }
}

@Composable
fun SettingsRow(title: String, value: String? = null, symbol: UiSymbol? = null, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            if (symbol != null) LeadingSymbol(symbol)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (!value.isNullOrBlank()) Text(value, Modifier.widthIn(max = 150.dp), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
            Symbol(UiSymbol.CHEVRON, Modifier.size(15.dp))
        }
        GroupDivider()
    }
}

@Composable
fun ActionListRow(title: String, onClick: () -> Unit, symbol: UiSymbol = UiSymbol.ADD, enabled: Boolean = true, danger: Boolean = false) {
    val color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f)
        else if (danger) MaterialTheme.colorScheme.error else LocalNoDoubtTokens.current.foregroundAccent
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Symbol(symbol, color = color)
        Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
fun GroupDivider() = HorizontalDivider(thickness = .5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))

@Composable
fun InlineStatus(text: String?, error: Boolean = false, loading: Boolean = false) {
    if (text == null && !loading) return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(text ?: "处理中…", style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

@Composable
fun DialogBody(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
