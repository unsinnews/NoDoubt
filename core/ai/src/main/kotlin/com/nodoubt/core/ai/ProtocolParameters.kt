package com.nodoubt.core.ai

import com.nodoubt.core.ai.ParameterType.ARRAY
import com.nodoubt.core.ai.ParameterType.BOOLEAN
import com.nodoubt.core.ai.ParameterType.INTEGER
import com.nodoubt.core.ai.ParameterType.OBJECT
import com.nodoubt.core.ai.ParameterType.STRING

/** Request parameters per protocol. Thinking entries are edited only in each model's 思考 section. */
internal object ProtocolParameters {
    private val outputLimit = "未设置时使用模型的最大输出"

    val completions = listOf(
        ParameterDefinition("max_tokens", "最大输出 Token", INTEGER,
            description = "$outputLimit；实际字段名由兼容选项决定"),
        parameter("reasoning_effort", "思考强度", STRING, "none", "minimal", "low", "medium", "high", "xhigh", "max"),
        ParameterDefinition("thinkingBudgets", "思考档位预算", OBJECT,
            description = "配置了思考预算字段时使用，默认 minimal 1024、low 2048、medium 8192、high 16384",
            suggestedValue = """{"low":2048,"high":16384}""")
    )

    val responses = listOf(
        ParameterDefinition("max_output_tokens", "最大输出 Token", INTEGER, description = outputLimit),
        parameter("reasoning.effort", "思考强度", STRING, "none", "minimal", "low", "medium", "high", "xhigh", "max"),
        ParameterDefinition("reasoning.summary", "思考摘要", STRING, listOf("auto", "concise", "detailed"),
            "开启思考时默认 auto"),
        parameter("text.verbosity", "回答详细程度", STRING, "low", "medium", "high")
    )

    val anthropic = listOf(
        ParameterDefinition("max_tokens", "最大输出 Token", INTEGER, required = true,
            description = "协议必填；未设置时使用模型的最大输出，模型也未设置时为 16384", suggestedValue = "16384"),
        parameter("thinking.type", "思考模式", STRING, "enabled", "disabled", "adaptive"),
        ParameterDefinition("thinking.budget_tokens", "思考 Token 预算", INTEGER,
            description = "手动开启时默认 1024，至少 1024"),
        ParameterDefinition("thinking.display", "思考内容展示", STRING, listOf("summarized", "omitted"),
            "开启思考时默认 summarized"),
        ParameterDefinition("interleavedThinking", "交错思考", BOOLEAN, description = "开启预算思考时默认发送 Beta 请求头"),
        parameter("output_config.effort", "自适应思考强度", STRING, "low", "medium", "high", "xhigh", "max")
    )

    val google = listOf(
        ParameterDefinition("generationConfig.maxOutputTokens", "最大输出 Token", INTEGER, description = outputLimit),
        parameter("generationConfig.thinkingConfig.thinkingBudget", "思考预算（-1 动态，0 禁用）", INTEGER),
        parameter("generationConfig.thinkingConfig.thinkingLevel", "思考强度", STRING, "MINIMAL", "LOW", "MEDIUM", "HIGH"),
        ParameterDefinition("generationConfig.thinkingConfig.includeThoughts", "返回思考摘要", BOOLEAN,
            description = "开启思考时默认返回"),
        parameter("safetySettings", "安全过滤设置", ARRAY)
    )
}
