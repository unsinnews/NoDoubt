package com.nodoubt.feature.channels

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import com.nodoubt.core.config.ConfigProfile
import com.nodoubt.core.config.ConfigRepository
import com.nodoubt.core.designsystem.*

/** Profiles: tap to switch, pencil to rename, trash to delete, and a duplicate-current action. */
@Composable
internal fun ProfileSection(
    repository: ConfigRepository, profiles: List<ConfigProfile>, busy: Boolean, active: Boolean,
    apply: (String, () -> Unit) -> Boolean
) {
    var naming by remember { mutableStateOf<Pair<String, ConfigProfile?>?>(null) }
    var deleting by remember { mutableStateOf<ConfigProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Section("配置集", "轻点切换；每个配置集有独立的服务与密钥") {
        profiles.forEach { profile ->
            EditableRow(profile.name, if (profile.isActive) "正在使用" else null,
                onClick = { if (!profile.isActive && !busy) apply("已切换到 ${profile.name}") { repository.switchProfile(profile.id) } },
                onDelete = if (profiles.size > 1) ({ deleting = profile }) else null) {
                if (profile.isActive) Symbol(UiSymbol.CHECK, Modifier.semantics { contentDescription = "当前配置集" },
                    LocalNoDoubtTokens.current.foregroundAccent)
                RowIcon(UiSymbol.EDIT, "重命名${profile.name}") { error = null; naming = "rename" to profile }
            }
        }
        ActionListRow("新建空白配置集", { error = null; naming = "create" to null }, enabled = !busy)
        ActionListRow("复制当前配置集", { error = null; naming = "copy" to profiles.firstOrNull { it.isActive } }, UiSymbol.FOLDER,
            enabled = !busy && profiles.any { it.isActive })
    }
    naming?.takeIf { active }?.let { (mode, profile) ->
        val title = when (mode) { "create" -> "新建配置集"; "copy" -> "复制配置集"; else -> "重命名" }
        val initial = when (mode) { "copy" -> "${profile?.name} 副本"; "rename" -> profile?.name.orEmpty(); else -> "" }
        ProfileNameDialog(title, initial, { name ->
            val ok = apply(if (mode == "rename") "已重命名" else "已切换到 $name") {
                when (mode) {
                    "create" -> repository.switchProfile(repository.createProfile(name).id)
                    "copy" -> repository.switchProfile(repository.copyProfile(requireNotNull(profile).id, name).id)
                    else -> repository.renameProfile(requireNotNull(profile).id, name)
                }
            }
            if (ok) naming = null else error = "保存失败，请换个名称"
        }, { naming = null }, error = error)
    }
    deleting?.takeIf { active }?.let { profile ->
        ConfirmDialog("删除“${profile.name}”？", "其中的服务与密钥将一并删除，无法恢复。", "删除", {
            apply("已删除 ${profile.name}") {
                if (repository.state.value.id == profile.id) repository.switchProfile(profiles.first { it.id != profile.id }.id)
                repository.deleteProfile(profile.id)
            }
            deleting = null
        }, { deleting = null }, danger = true)
    }
}

@Composable
internal fun ProfileNameDialog(title: String, initial: String, onApply: (String) -> Unit, onDismiss: () -> Unit, error: String? = null) {
    var name by remember(initial) { mutableStateOf(initial) }
    Sheet(title, onDismiss, confirm = "完成", onConfirm = { onApply(name.trim()) }, confirmEnabled = name.isNotBlank()) {
        Section("") { Field("名称", name, { name = it }, hint = "例如 工作") }
        InlineStatus(error, error = true)
    }
}

@Composable
internal fun ExportConfigurationSheet(
    name: String, onFile: (Boolean) -> Unit, onClipboard: () -> Unit, onDismiss: () -> Unit, error: String?
) {
    var includeSecrets by remember { mutableStateOf(false) }
    Sheet("导出配置", onDismiss) {
        Section("", if (includeSecrets) "密钥将以明文写入文件，请妥善保管" else "不含密钥和变量值，导入后需重新填写") {
            ItemRow(name)
            ToggleRow("包含密钥", includeSecrets, { includeSecrets = it })
        }
        Section("", if (includeSecrets) "复制到剪贴板始终不含密钥" else null) {
            ActionListRow("保存为文件", { onFile(includeSecrets) }, UiSymbol.UPLOAD)
            ActionListRow("复制到剪贴板", onClipboard, UiSymbol.EDIT)
        }
        InlineStatus(error, error = true)
    }
}
