package com.nodoubt.feature.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nodoubt.core.ai.ConnectionResult
import com.nodoubt.core.designsystem.GroupDivider
import com.nodoubt.core.designsystem.LocalNoDoubtTokens

/** A coloured dot, the verdict, then model · latency and a short reason. */
@Composable
internal fun ConnectionResultRow(model: String, result: ConnectionResult) {
    val color = when (result.level) {
        ConnectionResult.Level.OK -> LocalNoDoubtTokens.current.success
        ConnectionResult.Level.WARNING -> Color(0xffd08a00)
        ConnectionResult.Level.ERROR -> MaterialTheme.colorScheme.error
    }
    val meta = listOfNotNull(model, result.millis.takeIf { it > 0 }?.let(::latency)).joinToString(" · ")
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics { contentDescription = "测试结果：${result.summary}" },
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(top = 7.dp).size(9.dp).background(color, CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(result.summary, style = MaterialTheme.typography.bodyLarge, color = color)
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                result.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                }
            }
        }
        GroupDivider()
    }
}

private fun latency(millis: Long) = if (millis < 1000) "$millis ms" else "%.1f 秒".format(millis / 1000.0)
