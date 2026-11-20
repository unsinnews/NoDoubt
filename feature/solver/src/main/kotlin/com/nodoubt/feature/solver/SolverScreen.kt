package com.nodoubt.feature.solver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.AppConfig
import com.nodoubt.core.model.Usage

/** The screenshot answer sheet: all questions share one reading column in each mode. */
@Composable
fun SolverScreen(
    controller: SolverController,
    config: AppConfig,
    onCapture: () -> Unit,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
    onResizeBy: (Float) -> Unit = {},
    onInputActiveChange: (Boolean) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val state by controller.state.collectAsState()
    var usage by rememberSaveable(state.session) { mutableStateOf(Usage.FAST) }
    val reading = rememberSaveable(state.session, saver = SolverReadingState.Saver) { SolverReadingState() }
    val activeInputs = remember(state.session) { mutableStateMapOf<String, Boolean>() }
    val retryErrors = remember(state.session) { mutableStateMapOf<String, RetryFailure>() }
    var editorSession by remember { mutableIntStateOf(0) }
    val inputCallback by rememberUpdatedState(onInputActiveChange)
    LaunchedEffect(activeInputs.isNotEmpty()) { inputCallback(activeInputs.isNotEmpty()) }
    DisposableEffect(Unit) { onDispose { inputCallback(false) } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        activeInputs.clear()
        inputCallback(false)
        editorSession++
    }
    val running = answerModeIsRunning(state, usage)
    val status = answerModeStatus(state, usage)
    var copyFeedback by remember { mutableStateOf(0) }
    var showCopied by remember { mutableStateOf(false) }
    LaunchedEffect(copyFeedback) {
        if (copyFeedback > 0) {
            showCopied = true
            kotlinx.coroutines.delay(1400)
            showCopied = false
        }
    }
    Surface(modifier.fillMaxSize(), shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().imePadding()) {
            AnswerDragHandle(onResizeBy)
            AnswerModePicker(usage) { usage = it }
            Box(Modifier.weight(1f).fillMaxWidth().answerModeSwipe { usage = it }) {
                    Column(Modifier.fillMaxSize().verticalScroll(reading.scrollFor(usage))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.error?.let {
                            InlineStatus(it, error = true)
                            Row {
                                QuietButton("重新识别", controller::retryRecognition)
                                QuietButton("检查配置", onConfigure)
                            }
                        }
                        if (state.questions.isEmpty() && state.recognition == RunStatus.IDLE) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("截图后，题目和解答会出现在这里", style = MaterialTheme.typography.titleMedium)
                                Caption("在题目页面点击悬浮球截图")
                                QuietButton("设置模型服务", onConfigure)
                            }
                        }
                        state.questions.forEach { question ->
                            key(question.id) {
                                val runId = question.answers.getValue(usage).runId
                                val inputKey = "${usage.name}:${question.id}"
                                val retryError = retryErrors[inputKey]?.takeIf { it.runId == runId }?.message
                                LaunchedEffect(inputKey, runId) {
                                    if (retryErrors[inputKey]?.runId != runId) retryErrors.remove(inputKey)
                                }
                                QuestionAnswerCard(question, usage, state.recognition, config,
                                    thinkingExpanded = reading.isThinkingExpanded(usage, question.id, runId),
                                    onThinkingExpandedChange = { reading.setThinkingExpanded(usage, question.id, runId, it) },
                                    onRetry = {
                                        controller.retry(question.id, usage).fold(
                                            onSuccess = { retryErrors.remove(inputKey) },
                                            onFailure = { retryErrors[inputKey] = RetryFailure(runId,
                                                it.message ?: "重试失败，请检查模型") })
                                    },
                                    onModel = { retryErrors.remove(inputKey); controller.answer(question.id, usage, it) },
                                    onCopied = { copyFeedback++ },
                                    thinkingOptions = controller.thinkingOptions(question.id, usage),
                                    onRetryWithThinking = { controller.retryWithThinking(question.id, usage, it) },
                                    onInputActiveChange = { active ->
                                        if (active) activeInputs[inputKey] = true else activeInputs.remove(inputKey)
                                    }, editorSession = editorSession, retryError = retryError)
                            }
                        }
                    }
                if (showCopied) Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    shape = MaterialTheme.shapes.small, shadowElevation = 3.dp,
                    color = MaterialTheme.colorScheme.inverseSurface) {
                    Text("已复制", Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodyMedium)
                }
            }
            AnswerBottomBar(status, running, onCapture, onStop = { controller.stopMode(usage) }, onDismiss = onDismiss)
        }
    }
}

private data class RetryFailure(val runId: Int, val message: String)
