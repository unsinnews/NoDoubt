package com.nodoubt.feature.solver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.*
import com.nodoubt.core.model.ThinkingOptions
import com.nodoubt.core.model.ThinkingOverride

/** Only manual budgets need an editor; keyboard focus stays in the answer window. */
@Composable
internal fun ThinkingBudgetEditor(
    options: ThinkingOptions, initial: ThinkingOverride.Budget?,
    onRetry: (ThinkingOverride?) -> Result<Unit>, onClose: () -> Unit, onInputActiveChange: (Boolean) -> Unit
) {
    var budget by remember { mutableStateOf(initial?.tokens?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val tokens = LocalNoDoubtTokens.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val inputCallback by rememberUpdatedState(onInputActiveChange)
    DisposableEffect(Unit) { inputCallback(true); onDispose { inputCallback(false) } }
    fun close() { focus.clearFocus(); keyboard?.hide(); onClose() }
    Column(Modifier.fillMaxWidth().testTag("thinking-budget-editor")
        .background(tokens.inset, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("预算", style = MaterialTheme.typography.bodyMedium)
            BasicTextField(budget, { budget = it; error = null },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).wrapContentHeight()
                    .testTag("thinking-budget").semantics { contentDescription = "思考预算" },
                singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                // A text keyboard exposes the minus sign needed for Gemini's explicit -1 value.
                keyboardOptions = KeyboardOptions(keyboardType = if (options.minimumBudget == -1) KeyboardType.Text else KeyboardType.Number),
                cursorBrush = SolidColor(tokens.foregroundAccent), decorationBox = { inner ->
                    Box {
                        if (budget.isEmpty()) Caption(if (options.minimumBudget == -1) "-1 / 0 / 正数" else "≥ ${options.minimumBudget}")
                        inner()
                    }
                })
            QuietButton("确认", {
                val result = thinkingDraft("budget", budget, options).fold(onSuccess = onRetry, onFailure = { Result.failure(it) })
                result.fold(onSuccess = { close() }, onFailure = { error = it.message ?: "无法使用此预算" })
            }, Modifier.testTag("retry-with-thinking"))
            QuietButton("取消", ::close, Modifier.testTag("thinking-budget-cancel"))
        }
        error?.let {
            Text(it, Modifier.testTag("thinking-option-error").padding(bottom = 6.dp),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
