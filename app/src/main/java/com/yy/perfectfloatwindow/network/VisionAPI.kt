package com.yy.perfectfloatwindow.network

import android.graphics.Bitmap
import com.yy.perfectfloatwindow.data.AIConfig
import com.yy.perfectfloatwindow.data.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface OCRStreamingCallback {
    fun onChunk(text: String)
    fun onQuestionsReady(questions: List<Question>)
    fun onError(error: Exception)
}

class VisionAPI(private val config: AIConfig) {

    companion object {
        val OCR_SYSTEM_PROMPT = """你是一个专业的题目识别助手。请仔细分析图片，完整提取每道题目。

要求：
1. 完整识别题目内容，包括：
   - 题目类型标识（如"单选题"、"多选题"、"填空题"、"判断题"等，放在题目最前面，用括号括起来）
   - 题目编号（如"1."、"第一题"等，保留原有编号）
   - 题目正文
   - 所有选项（A、B、C、D等，包含完整内容）
   - 填空题的空格位置用____表示

2. 小题归属判断（非常重要）：
   - 先通读整个图片内容，理解题目的整体结构
   - 判断依据：小题是否共享同一个题干、背景信息、图表或材料
   - 如果多个小题（如(1)(2)(3)、①②③、a.b.c.等）基于同一个题干或材料，必须作为一道完整题目输出
   - 常见需要合并的情况：阅读理解题、材料分析题、综合计算题、实验探究题、案例分析题、图表分析题、证明题（含多问）、应用题（含多步）、完形填空、短文改错、语法填空
   - 绝对不能将同一大题下的小题拆分成多道独立题目

3. 表格处理：如果题目包含表格，使用Markdown表格格式放在代码框中，例如：
```表格名称
| 列1 | 列2 |
|-----|-----|
| 值1 | 值2 |
```

4. 数学公式用LaTeX格式表示

5. 不同题目之间用两个空行分隔

只输出题目内容，不输出任何无关内容。""".trimIndent()
    }

    fun extractQuestionsStreaming(bitmap: Bitmap, callback: OCRStreamingCallback) {
        val base64Image = BitmapUtils.toDataUrl(bitmap)
        val client = OpenAIClient(config)

        val messages = listOf(
            mapOf("role" to "system", "content" to OCR_SYSTEM_PROMPT),
            mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf("url" to base64Image)
                    ),
                    mapOf(
                        "type" to "text",
                        "text" to "请识别图片中的所有题目。"
                    )
                )
            )
        )

        val accumulatedText = StringBuilder()

        client.streamChatCompletion(messages, object : StreamingCallback {
            override fun onChunk(text: String) {
                accumulatedText.append(text)
                callback.onChunk(text)
            }

            override fun onComplete() {
                val fullText = accumulatedText.toString()
                val questions = parseQuestionsFromText(fullText)
                callback.onQuestionsReady(questions)
            }

            override fun onError(error: Exception) {
                callback.onError(error)
            }
        })
    }

    suspend fun extractQuestions(bitmap: Bitmap): List<Question> = withContext(Dispatchers.IO) {
        val base64Image = BitmapUtils.toDataUrl(bitmap)
        val client = OpenAIClient(config)

        val messages = listOf(
            mapOf("role" to "system", "content" to OCR_SYSTEM_PROMPT),
            mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf("url" to base64Image)
                    ),
                    mapOf(
                        "type" to "text",
                        "text" to "请识别图片中的所有题目。"
                    )
                )
            )
        )

        val response = client.chatCompletion(messages, stream = false)
        val content = OpenAIClient.parseNonStreamingResponse(response)
        parseQuestionsFromText(content)
    }

    /**
     * 按两个连续换行符分割文本，解析为题目列表
     */
    private fun parseQuestionsFromText(text: String): List<Question> {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) {
            return emptyList()
        }

        // 按两个或更多连续换行符分割题目
        val questionTexts = trimmedText.split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return if (questionTexts.isEmpty()) {
            listOf(Question(id = 1, text = trimmedText))
        } else {
            questionTexts.mapIndexed { index, questionText ->
                Question(id = index + 1, text = questionText)
            }
        }
    }
}
