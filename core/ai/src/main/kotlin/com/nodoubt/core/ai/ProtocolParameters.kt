package com.nodoubt.core.ai

import com.nodoubt.core.ai.ParameterType.ARRAY
import com.nodoubt.core.ai.ParameterType.BOOLEAN
import com.nodoubt.core.ai.ParameterType.INTEGER
import com.nodoubt.core.ai.ParameterType.NUMBER
import com.nodoubt.core.ai.ParameterType.OBJECT
import com.nodoubt.core.ai.ParameterType.STRING

internal object ProtocolParameters {
    private val cache = listOf(
        parameter("cacheRetention", "缓存保留策略", STRING, "none", "short", "long"),
        parameter("sessionId", "缓存会话标识"),
        parameter("metadata", "请求元数据", OBJECT)
    )
    private val sampling = listOf(
        parameter("temperature", "温度", NUMBER),
        parameter("top_p", "Top P", NUMBER)
    )
    val completions = sampling + listOf(
        parameter("max_tokens", "最大输出 Token", INTEGER),
        parameter("max_completion_tokens", "最大完成 Token", INTEGER),
        parameter("top_k", "Top K", INTEGER),
        parameter("min_p", "Min P", NUMBER),
        parameter("frequency_penalty", "频率惩罚", NUMBER),
        parameter("presence_penalty", "存在惩罚", NUMBER),
        parameter("repetition_penalty", "重复惩罚", NUMBER),
        parameter("seed", "随机种子", INTEGER),
        parameter("stop", "停止序列", ARRAY),
        parameter("reasoning_effort", "思考强度", STRING, "none", "minimal", "low", "medium", "high", "xhigh", "max"),
        parameter("thinkingBudgets", "思考档位预算", OBJECT),
        parameter("thinking_token_budget", "思考 Token 预算", INTEGER),
        parameter("thinking_budget", "思考预算（兼容服务）", INTEGER),
        parameter("thinking_budget_tokens", "思考预算（llama.cpp）", INTEGER),
        parameter("tool_choice", "工具选择", STRING, "auto", "none", "required"),
        parameter("parallel_tool_calls", "并行工具调用", BOOLEAN),
        parameter("response_format", "响应格式", OBJECT),
        parameter("logit_bias", "Token 偏置", OBJECT),
        parameter("logprobs", "返回 Token 概率", BOOLEAN),
        parameter("top_logprobs", "候选 Token 概率数量", INTEGER),
        parameter("store", "服务端保存响应", BOOLEAN),
        parameter("user", "用户标识"),
        parameter("stream_options.include_usage", "流式返回用量", BOOLEAN)
    ) + cache

    val responses = sampling + listOf(
        parameter("max_output_tokens", "最大输出 Token", INTEGER),
        parameter("reasoning.effort", "思考强度", STRING, "none", "minimal", "low", "medium", "high", "xhigh", "max"),
        parameter("reasoning.summary", "思考摘要", STRING, "auto", "concise", "detailed"),
        parameter("text.verbosity", "回答详细程度", STRING, "low", "medium", "high"),
        parameter("text.format", "响应格式", OBJECT),
        parameter("service_tier", "服务层级", STRING, "auto", "default", "flex", "priority"),
        parameter("tool_choice", "工具选择", STRING, "auto", "none", "required"),
        parameter("parallel_tool_calls", "并行工具调用", BOOLEAN),
        parameter("truncation", "上下文截断", STRING, "auto", "disabled"),
        parameter("include", "附加响应字段", ARRAY),
        parameter("store", "服务端保存响应", BOOLEAN),
        parameter("user", "用户标识"),
        parameter("prompt_cache_key", "缓存键"),
        parameter("prompt_cache_retention", "缓存保留时间", STRING, "in-memory", "24h"),
        parameter("prompt_cache_options", "显式缓存选项", OBJECT)
    ) + cache

    val anthropic = sampling + listOf(
        ParameterDefinition("max_tokens", "最大输出 Token", INTEGER, required = true,
            description = "协议必填。未设置时使用模型 maxTokens；模型也未设置时使用 1024。", suggestedValue = "1024"),
        parameter("top_k", "Top K", INTEGER),
        parameter("stop_sequences", "停止序列", ARRAY),
        parameter("thinking.type", "思考模式", STRING, "enabled", "disabled", "adaptive"),
        parameter("thinking.budget_tokens", "思考 Token 预算", INTEGER),
        parameter("thinking.display", "思考内容展示", STRING, "summarized", "omitted"),
        parameter("interleavedThinking", "交错思考 Beta 请求头", BOOLEAN),
        parameter("output_config.effort", "自适应思考强度", STRING, "low", "medium", "high", "xhigh", "max"),
        parameter("tool_choice", "工具选择", OBJECT),
        parameter("metadata.user_id", "用户标识"),
        parameter("service_tier", "服务层级", STRING, "auto", "standard_only")
    ) + cache

    val google = listOf(
        parameter("generationConfig.temperature", "温度", NUMBER),
        parameter("generationConfig.topP", "Top P", NUMBER),
        parameter("generationConfig.topK", "Top K", INTEGER),
        parameter("generationConfig.maxOutputTokens", "最大输出 Token", INTEGER),
        parameter("generationConfig.stopSequences", "停止序列", ARRAY),
        parameter("generationConfig.seed", "随机种子", INTEGER),
        parameter("generationConfig.responseMimeType", "响应 MIME 类型"),
        parameter("generationConfig.responseSchema", "响应结构", OBJECT),
        parameter("generationConfig.thinkingConfig.thinkingBudget", "思考预算（-1 动态，0 禁用）", INTEGER),
        parameter("generationConfig.thinkingConfig.thinkingLevel", "思考强度", STRING, "MINIMAL", "LOW", "MEDIUM", "HIGH"),
        parameter("generationConfig.thinkingConfig.includeThoughts", "返回思考摘要", BOOLEAN),
        parameter("toolConfig.functionCallingConfig.mode", "工具选择", STRING, "AUTO", "NONE", "ANY"),
        parameter("safetySettings", "安全过滤设置", ARRAY),
        parameter("cachedContent", "已缓存内容名称"),
        parameter("labels", "请求标签", OBJECT)
    )
}
