package com.nodoubt.feature.channels

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.nodoubt.core.ai.AiClient
import com.nodoubt.core.config.ConfigRepository
import com.nodoubt.core.designsystem.QuietButton
import com.nodoubt.core.designsystem.ActionRow
import com.nodoubt.core.designsystem.LocalScreenActive
import com.nodoubt.core.model.*

@Composable
fun ChannelsScreen(repository: ConfigRepository, aiClient: AiClient, modifier: Modifier = Modifier, active: Boolean = true, onClose: () -> Unit = {}) {
    val config by repository.state.collectAsState()
    val context = LocalContext.current
    val editor = remember(repository, context) {
        context.viewModelOwner()?.let { ViewModelProvider(it)[ChannelsEditorViewModel::class.java].editor }
            ?: ChannelEditorState()
    }
    BackHandler(active) { if (editor.page == ChannelPage.LIST) onClose() else editor.back() }
    CompositionLocalProvider(LocalScreenActive provides active) {
    when (editor.page) {
        ChannelPage.LIST -> ChannelListScreen(config, repository.initializationError, modifier,
            onOpen = editor::open, onBindings = { editor.page = ChannelPage.BINDINGS },
            onFiles = { editor.page = ChannelPage.FILES }, onClose = onClose)
        ChannelPage.CHANNEL -> editor.channel?.let { channel ->
            ChannelEditorScreen(channel, editor, repository, aiClient, modifier, active,
                onModel = { editor.modelIndex = it; editor.page = ChannelPage.MODEL },
                onParameters = { editor.modelScope = false; editor.page = ChannelPage.PARAMETERS },
                onCompatibility = { editor.modelScope = false; editor.page = ChannelPage.COMPATIBILITY },
                onAdvanced = { editor.page = ChannelPage.CHANNEL_ADVANCED })
        }
        ChannelPage.CHANNEL_ADVANCED -> editor.channel?.let { channel ->
            ChannelAdvancedScreen(channel, { editor.channel = it }, editor::back, modifier)
        }
        ChannelPage.MODEL -> editor.channel?.models?.getOrNull(editor.modelIndex)?.let { model ->
            ModelEditorScreen(model, requireNotNull(editor.channel), editor::updateModel, editor::back, modifier,
                onParameters = { editor.modelScope = true; editor.page = ChannelPage.PARAMETERS },
                onCompatibility = { editor.modelScope = true; editor.page = ChannelPage.COMPATIBILITY },
                onAdvanced = { editor.page = ChannelPage.MODEL_ADVANCED },
                onRemove = { editor.removeModelAt(editor.modelIndex); editor.page = ChannelPage.CHANNEL }, active = active)
        }
        ChannelPage.MODEL_ADVANCED -> editor.channel?.models?.getOrNull(editor.modelIndex)?.let { model ->
            ModelAdvancedScreen(model, requireNotNull(editor.channel), editor::updateModel, editor::back, modifier)
        }
        ChannelPage.PARAMETERS -> editor.channel?.let { channel ->
            val model = if (editor.modelScope) channel.models.getOrNull(editor.modelIndex) else null
            ParametersScreen(model?.protocol ?: channel.protocol, model?.parameters ?: channel.parameters,
                modelScope = editor.modelScope, onChange = {
                    if (model != null) editor.updateModel(model.copy(parameters = it))
                    else editor.channel = channel.copy(parameters = it)
                }, onBack = editor::back, modifier = modifier)
        }
        ChannelPage.COMPATIBILITY -> editor.channel?.let { channel ->
            val model = if (editor.modelScope) channel.models.getOrNull(editor.modelIndex) else null
            CompatibilityScreen(model?.protocol ?: channel.protocol, model?.compat ?: channel.compat,
                onChange = { if (model != null) editor.updateModel(model.copy(compat = it))
                    else editor.channel = channel.copy(compat = it) }, onBack = editor::back, modifier = modifier)
        }
        ChannelPage.BINDINGS -> UsageBindingsScreen(repository, config, editor::back, modifier, active)
        ChannelPage.FILES -> ConfigurationScreen(repository, config, editor::back, modifier, active)
    }
    if (active && editor.confirmExit) AlertDialog(
        onDismissRequest = { editor.confirmExit = false },
        shape = MaterialTheme.shapes.large,
        title = { Text("保存更改？") },
        text = { Text("离开前是否保存这次修改？") },
        confirmButton = { QuietButton("保存并返回", {
            if (editor.save(repository)) { editor.confirmExit = false; editor.page = ChannelPage.LIST }
            else editor.confirmExit = false
        }) },
        dismissButton = {
            ActionRow {
                QuietButton("继续编辑", { editor.confirmExit = false })
                QuietButton("放弃更改", editor::discard, danger = true)
            }
        }
    )
    }
}

internal class ChannelsEditorViewModel : ViewModel() {
    val editor = ChannelEditorState()
    override fun onCleared() { editor.discard() }
}

private fun Context.viewModelOwner(): ViewModelStoreOwner? = when (this) {
    is ViewModelStoreOwner -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.viewModelOwner()
    else -> null
}
