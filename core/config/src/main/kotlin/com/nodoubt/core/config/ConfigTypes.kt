package com.nodoubt.core.config

import com.nodoubt.core.model.AppConfig

data class ConfigProfile(val id: String, val name: String, val isActive: Boolean = false)
enum class ImportMode { MERGE, REPLACE }

/** Credentials exist only in memory between preview and application. Never log this object. */
class ImportPreview(
    val config: AppConfig,
    val warnings: List<String> = emptyList(),
    val changes: List<String> = emptyList(),
    val sourceUrl: String? = null,
    val credentials: Map<String, String> = emptyMap(),
    val remoteBaseline: String? = null,
    val isRefresh: Boolean = false,
    internal val expectedConfig: AppConfig? = null
) {
    override fun toString(): String = "ImportPreview(${config.providers.size} channels, ${warnings.size} warnings)"
}
