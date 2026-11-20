package com.nodoubt.feature.channels

import androidx.compose.runtime.*
import com.nodoubt.core.config.ConfigRepository
import com.nodoubt.core.model.*
import java.util.UUID

internal enum class ChannelPage { LIST, CHANNEL, CHANNEL_ADVANCED, MODEL, MODEL_ADVANCED, PARAMETERS, COMPATIBILITY, BINDINGS, FILES }

@Stable
internal class ChannelEditorState {
    var page by mutableStateOf(ChannelPage.LIST)
    var channel by mutableStateOf<ChannelConfig?>(null)
    var original by mutableStateOf<ChannelConfig?>(null)
    var modelIndex by mutableIntStateOf(-1)
    var modelScope by mutableStateOf(false)
    var keyDraft by mutableStateOf("")
    var keyChanged by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var confirmExit by mutableStateOf(false)
    val renamedModels = mutableMapOf<String, String>()
    private val modelOrigins = mutableListOf<String?>()
    /** What the page started from: the saved channel, or the untouched template of a new one. */
    private var baseline by mutableStateOf<ChannelConfig?>(null)

    val dirty get() = channel != baseline || keyChanged

    fun open(value: ChannelConfig?) {
        original = value
        channel = value ?: ChannelConfig(UUID.randomUUID().toString(), "新服务")
        baseline = channel
        keyDraft = ""
        keyChanged = false
        modelIndex = -1
        renamedModels.clear()
        modelOrigins.clear()
        modelOrigins.addAll(value?.models?.map { it.id }.orEmpty())
        error = null
        confirmExit = false
        page = ChannelPage.CHANNEL
    }

    fun discard() {
        channel = original
        baseline = original
        keyDraft = ""
        keyChanged = false
        renamedModels.clear()
        modelOrigins.clear()
        modelOrigins.addAll(original?.models?.map { it.id }.orEmpty())
        modelIndex = -1
        error = null
        confirmExit = false
        page = ChannelPage.LIST
    }

    fun updateModel(next: ModelConfig) {
        val current = channel ?: return
        if (current.models.getOrNull(modelIndex) == null) return
        modelOrigins.getOrNull(modelIndex)?.let { renamedModels[it] = next.id }
        channel = current.copy(models = current.models.toMutableList().apply { set(modelIndex, next) })
    }

    fun addModels(models: List<ModelConfig>) {
        val current = channel ?: return
        val added = models.distinctBy { it.id }.filterNot { candidate -> current.models.any { it.id == candidate.id } }
        modelOrigins.addAll(List(added.size) { null })
        channel = current.copy(models = current.models + added)
    }

    fun moveModel(from: Int, to: Int) {
        val current = channel ?: return
        if (from !in current.models.indices || to !in current.models.indices || from == to) return
        modelOrigins.add(to, modelOrigins.removeAt(from))
        channel = current.copy(models = current.models.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun removeModel(id: String) {
        val current = channel ?: return
        removeModelAt(current.models.indexOfFirst { it.id == id })
    }

    fun removeModelAt(index: Int) {
        val current = channel ?: return
        if (index !in current.models.indices) return
        modelOrigins[index]?.let(renamedModels::remove)
        modelOrigins.removeAt(index)
        channel = current.copy(models = current.models.filterIndexed { i, _ -> i != index })
    }

    fun currentModelId(originalId: String): String? {
        val models = channel?.models.orEmpty()
        return if (original?.models?.any { it.id == originalId } == true) {
            models.getOrNull(modelOrigins.indexOf(originalId))?.id
        } else models.firstOrNull { it.id == originalId }?.id
    }

    fun back() {
        error = null
        when (page) {
            ChannelPage.CHANNEL -> if (dirty) confirmExit = true else page = ChannelPage.LIST
            ChannelPage.MODEL -> page = ChannelPage.CHANNEL
            ChannelPage.CHANNEL_ADVANCED -> page = ChannelPage.CHANNEL
            ChannelPage.MODEL_ADVANCED -> page = ChannelPage.MODEL
            ChannelPage.PARAMETERS, ChannelPage.COMPATIBILITY -> page = if (modelScope) ChannelPage.MODEL else ChannelPage.CHANNEL
            else -> page = ChannelPage.LIST
        }
    }

    fun save(repository: ConfigRepository): Boolean = runCatching {
        val next = requireNotNull(channel)
        require(next.name.isNotBlank()) { "请输入服务名称" }
        require(next.models.none { it.id.isBlank() }) { "模型 ID 不能为空" }
        require(next.models.map { it.id }.distinct().size == next.models.size) { "模型 ID 重复" }
        val current = repository.state.value
        val providers = if (current.providers.any { it.id == next.id }) current.providers.map { if (it.id == next.id) next else it }
            else current.providers + next
        val bindings = current.bindings.mapValues { (_, binding) ->
            fun remap(ref: ModelRef): ModelRef? {
                if (ref.channelId != next.id) return ref
                return currentModelId(ref.modelId)?.let { ModelRef(next.id, it) }
            }
            val entries = binding.entries.mapNotNull(::remap).distinct()
            UsageBinding(entries, binding.selected?.let(::remap)?.takeIf { it in entries } ?: entries.firstOrNull())
        }
        repository.saveChannel(current.copy(providers = providers, bindings = bindings), next.id,
            if (keyChanged) keyDraft else null)
        val persisted = repository.state.value.providers.first { it.id == next.id }
        original = persisted
        channel = persisted
        baseline = persisted
        keyDraft = ""
        keyChanged = false
        renamedModels.clear()
        modelOrigins.clear()
        modelOrigins.addAll(persisted.models.map { it.id })
        error = null
        true
    }.getOrElse { error = it.message ?: "保存失败，请重试"; false }
}
