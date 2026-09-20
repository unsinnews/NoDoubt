package com.nodoubt.core.config

import com.nodoubt.core.model.AppConfig
import com.nodoubt.core.model.ChannelConfig

internal object ConfigSecrets {
    private val sensitiveHeader = Regex("(?i).*(auth|key|token|secret|cookie).*")

    fun protect(config: AppConfig): Pair<AppConfig, Map<String, String>> {
        val pending = mutableMapOf<String, String>()
        fun capture(reference: String, value: String): String {
            require(!value.startsWith("!")) { "配置命令不会执行，请填写实际值或变量引用" }
            if (value.startsWith("secret:") || ConfigValueTemplate.references(value).isNotEmpty()) return value
            if (value.isBlank()) return value
            pending[reference] = value
            return reference
        }
        fun headers(channel: ChannelConfig, model: String?, values: Map<String, String>): Map<String, String> =
            values.mapValues { (name, value) ->
                require(!value.startsWith("!")) { "请求头命令不会执行，请填写实际值或变量引用" }
                if (sensitiveHeader.matches(name)) {
                    capture("secret:header:${channel.id}:${model.orEmpty()}:${name.lowercase()}", value)
                } else value
            }
        val safe = config.copy(
            providers = config.providers.map { channel -> channel.copy(
                credentialRef = if (channel.credentialRef.startsWith("channel:")) "secret:${channel.credentialRef}"
                    else capture("secret:channel:${channel.id}", channel.credentialRef),
                headers = headers(channel, null, channel.headers),
                models = channel.models.map { model -> model.copy(headers = headers(channel, model.id, model.headers)) }
            ) },
            variables = config.variables.mapValues { (name, value) -> capture("secret:variable:$name", value) },
            source = config.source?.let { source -> source.copy(baseline = source.baseline?.let { baseline ->
                val document = ConfigCodec.decode(baseline).config.copy(source = null)
                ConfigCodec.encode(protect(document).first)
            }) }
        )
        return safe to pending
    }

    fun resolve(value: String, config: AppConfig, read: (String) -> String, seen: Set<String> = emptySet()): String {
        require(!value.startsWith("!")) { "不支持执行配置命令" }
        require(value !in seen) { "变量引用存在循环" }
        if (value.startsWith("secret:") || value.startsWith("channel:")) return read(value.let {
            if (it.startsWith("channel:")) "secret:$it" else it
        })
        return ConfigValueTemplate.expand(value) { name ->
            val mapped = config.variables[name] ?: error("请先配置变量 $$name")
            resolve(mapped, config, read, seen + value)
        }
    }
}
