package com.nodoubt.app.network

import com.nodoubt.app.data.AIConfig
import com.nodoubt.app.data.Question
import okhttp3.Call

class ChatAPI(private val config: AIConfig) {

    companion object {
        val SOLVER_SYSTEM_PROMPT = """你是一个专业的解题助手，擅长解答各类学科题目。请按照以下要求回答问题：

1. 解题步骤清晰，逻辑严谨
2. 如果是数学题，请展示详细的计算过程，数学公式使用LaTeX格式
3. 如果是选择题，先分析各选项，再给出正确答案
4. 如果是填空题，直接给出答案并简要说明理由
5. 使用中文回答
6. 答案要准确、完整

格式要求：
- 使用简洁清晰的语言
- 重要步骤或结论可以加粗显示
- 最终答案用【答案】标记""".trimIndent()
    }

    fun solveQuestion(question: Question, callback: StreamingCallback): Call {
        val client = OpenAIClient(config)

        val userPrompt = "请解答以下题目：\n\n${question.text}"

        val messages = listOf(
            mapOf("role" to "system", "content" to SOLVER_SYSTEM_PROMPT),
            mapOf("role" to "user", "content" to userPrompt)
        )

        return client.streamChatCompletion(messages, callback)
    }

    fun solveQuestionSync(question: Question): String {
        val client = OpenAIClient(config)

        val userPrompt = "请解答以下题目：\n\n${question.text}"

        val messages = listOf(
            mapOf("role" to "system", "content" to SOLVER_SYSTEM_PROMPT),
            mapOf("role" to "user", "content" to userPrompt)
        )

        val response = client.chatCompletion(messages, stream = false)
        return OpenAIClient.parseNonStreamingResponse(response)
    }
}
