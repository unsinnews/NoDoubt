package com.nodoubt.core.config

import android.content.Context
import android.util.AtomicFile
import com.nodoubt.core.model.AppConfig
import java.io.File

internal class ProfileFiles(context: Context) {
    private val directory = File(context.noBackupFilesDir, "configurations").apply { mkdirs() }
    private val index = context.getSharedPreferences("nodoubt_config_index", Context.MODE_PRIVATE)
    val activeId: String get() = index.getString("active", "default") ?: "default"
    val hasActiveSelection: Boolean get() = index.contains("active")

    fun exists(id: String): Boolean = file(id).exists() || File(file(id).path + ".bak").exists()
    fun read(id: String): AppConfig = ConfigCodec.decode(AtomicFile(file(id)).openRead().bufferedReader().use { it.readText() }).config.also {
        require(it.id == id) { "配置集文件与内部 ID 不一致，请通过导入恢复" }
    }

    fun write(config: AppConfig, allowDamaged: Boolean = false) {
        val json = ConfigCodec.encode(config)
        ConfigCodec.decode(json)
        val target = file(config.id)
        if (exists(config.id) && !allowDamaged) {
            // Validate before replacing, so a damaged profile never silently becomes an empty profile.
            val previous = AtomicFile(target).openRead().bufferedReader().use { it.readText() }
            ConfigCodec.decode(previous)
            atomicWrite(previous(config.id), previous)
        }
        atomicWrite(target, json)
    }

    fun restore(id: String): AppConfig {
        val backup = previous(id)
        require(backup.exists()) { "没有可恢复的上一个版本" }
        val config = ConfigCodec.decode(backup.readText()).config
        require(config.id == id) { "上版配置 ID 不匹配，未覆盖当前文件" }
        write(config, allowDamaged = true)
        return config
    }

    fun setActive(id: String) {
        read(id)
        check(index.edit().putString("active", id).commit()) { "无法切换配置集" }
    }

    fun profiles(): List<ConfigProfile> = directory.listFiles().orEmpty()
        .filter { it.extension == "json" || it.name.endsWith(".json.bak") }
        .map { it.name.removeSuffix(".bak").removeSuffix(".json") }.distinct().sorted()
        .map { id ->
            val config = runCatching { read(id) }.getOrNull()
            ConfigProfile(id, config?.name ?: "损坏的配置（$id）", id == activeId)
        }

    fun delete(id: String) {
        require(id != activeId) { "请先切换到其他配置集" }
        AtomicFile(file(id)).delete()
        AtomicFile(previous(id)).delete()
    }

    private fun file(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "配置集 ID 无效" }
        return File(directory, "$id.json")
    }

    private fun previous(id: String): File = File(directory, "${file(id).nameWithoutExtension}.previous")

    private fun atomicWrite(target: File, text: String) {
        val atomic = AtomicFile(target)
        val output = atomic.startWrite()
        try {
            output.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (failure: Exception) {
            atomic.failWrite(output)
            throw failure
        }
    }
}
