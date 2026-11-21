package com.nodoubt.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.nodoubt.core.designsystem.*
import com.nodoubt.feature.channels.ChannelsScreen
import com.nodoubt.feature.solver.HistoryScreen
import com.nodoubt.platform.capture.CaptureBus

@Composable
fun NoDoubtApp(graph: AppGraph, onAssistant: (Boolean) -> Unit, onStopCapture: () -> Unit, settingsRequest: Int = 0) {
    val config by graph.repository.state.collectAsState()
    val running by AssistantService.running.collectAsState()
    val capture by CaptureBus.status.collectAsState()
    val historyCount = graph.history.entries.collectAsState().value.size
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(settingsRequest) { if (settingsRequest > 0) { tab = 1; page = "channels" } }
    BackHandler(page != null && page != "channels" && page != "history") { page = null }
    NoDoubtTheme(config.preferences) {
        val view = LocalView.current
        val dark = LocalNoDoubtTokens.current.dark
        SideEffect {
            (view.context as? android.app.Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        Scaffold(bottomBar = {
            if (page == null) {
                NavigationBar(containerColor = LocalNoDoubtTokens.current.raised, tonalElevation = 0.dp) {
                    listOf("首页" to Icons.Outlined.Home, "我的" to Icons.Outlined.Person).forEachIndexed { index, (title, icon) ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index },
                            icon = { Icon(icon, null, Modifier.size(24.dp)) }, label = { Text(title) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent,
                                selectedIconColor = LocalNoDoubtTokens.current.foregroundAccent,
                                selectedTextColor = LocalNoDoubtTokens.current.foregroundAccent))
                    }
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    "channels" -> ChannelsScreen(graph.repository, graph.ai, onClose = { page = null })
                    "appearance" -> AppearanceScreen(graph.repository, onBack = { page = null })
                    "capture" -> CaptureSettingsScreen(graph.repository, capture.ready, onStopCapture, onBack = { page = null })
                    "history" -> HistoryScreen(graph.history, config.preferences.answerFontSize, onBack = { page = null })
                    else -> if (tab == 0) HomeScreen(running, capture.ready, capture.error,
                        config.bindings.values.all { it.entries.isNotEmpty() }, onAssistant, { tab = 1; page = "channels" })
                    else SettingsScreen(graph.repository, historyCount, onOpen = { page = it })
                }
            }
        }
    }
}
