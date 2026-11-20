package com.nodoubt.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nodoubt.core.designsystem.*

@Composable
internal fun HomeScreen(running: Boolean, captureReady: Boolean, error: String?, configured: Boolean,
    onAssistant: (Boolean) -> Unit, onConfigure: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(76.dp).background(LocalNoDoubtTokens.current.raised, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(74.dp),
                    tint = LocalNoDoubtTokens.current.foregroundAccent)
            }
            Text("AI 解题助手", style = MaterialTheme.typography.headlineSmall)
            Caption("截图识题，即时解答")
        }
        Surface(shape = RoundedCornerShape(18.dp), color = LocalNoDoubtTokens.current.raised) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("悬浮球", style = MaterialTheme.typography.titleMedium)
                    Caption(if (running) "已开启" else "点击悬浮球截图解题")
                }
                Switch(running, onAssistant, modifier = Modifier.semantics { contentDescription = "悬浮球" })
            }
        }
        if (running && !captureReady) Caption("请按系统提示允许截图")
        InlineStatus(error, error = true)
        if (!configured) {
            Surface(shape = RoundedCornerShape(16.dp), color = LocalNoDoubtTokens.current.raised, onClick = onConfigure) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("设置模型服务", style = MaterialTheme.typography.bodyLarge)
                        Caption("连接 API 后即可使用")
                    }
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 24.sp)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("使用方法", style = MaterialTheme.typography.titleMedium)
            listOf("开启悬浮球，打开题目", "点击悬浮球截图", "在弹出的窗口查看解答").forEachIndexed { index, text ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("0${index + 1}", color = LocalNoDoubtTokens.current.foregroundAccent, style = MaterialTheme.typography.labelLarge)
                    Caption(text)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
