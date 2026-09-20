package com.nodoubt.app

import androidx.compose.runtime.*
import com.nodoubt.core.designsystem.NoDoubtTheme
import com.nodoubt.feature.solver.SolverScreen

@Composable
internal fun AnswerOverlay(graph: AppGraph, onCapture: () -> Unit, onConfigure: () -> Unit, onResize: (Float) -> Unit,
    onInputActiveChange: (Boolean) -> Unit = {}, onDismiss: () -> Unit = {}) {
    val config by graph.repository.state.collectAsState()
    NoDoubtTheme(config.preferences) {
        SolverScreen(graph.solver, config, onCapture, onConfigure, onResizeBy = onResize,
            onInputActiveChange = onInputActiveChange, onDismiss = onDismiss)
    }
}
