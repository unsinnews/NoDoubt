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

2. 小题归属（重要）：必须整体判断小题是否隶属于某道大题，仔细观察题目结构，不要判断错误！如果小题隶属于大题，必须将大题和所有小题作为一道完整题目输出，绝对不能分开

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
