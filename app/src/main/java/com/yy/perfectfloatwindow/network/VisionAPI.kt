package com.yy.perfectfloatwindow.network

import android.graphics.Bitmap
import com.yy.perfectfloatwindow.data.AIConfig
import com.yy.perfectfloatwindow.data.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface OCRStreamingCallback {
    fun onChunk(text: String, currentQuestionIndex: Int)
    fun onQuestionReady(question: Question)
    fun onComplete()
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

2. 小题归属判断（最最重要，输出前必须反复确认）：

   【核心原则】宁可合并也不要错误拆分！如果不确定，就合并为一道题。

   【判断方法】
   - 看编号层级：主编号(1、2、3)下的子编号((1)(2)、①②、a.b)必须合并
   - 看内容关联：有共同的材料、图表、背景、情境、条件的必须合并
   - 看位置关系：紧跟在材料/图表后的所有问题必须合并
   - 看逻辑关系：有递进关系（"由此"、"进而"、"根据上题"）的必须合并

   【必须合并的标志词】
   "根据材料"、"根据上文"、"根据图表"、"结合上述"、"由此可知"、"综上所述"、"根据以上"、"阅读材料回答"、"看图回答"

   【必须合并的结构】
   - 一段文字/材料 + 后面的多个问题 = 一道题
   - 一个图/表 + 后面的多个问题 = 一道题
   - 主题号 + 所有子题号 = 一道题
   - 共同条件 + 多个小问 = 一道题

   【绝对禁止】
   - 禁止把(1)(2)(3)拆成三道独立题目
   - 禁止把材料和其后的问题分开
   - 禁止把图表题的图表和问题分开
   - 禁止把有递进关系的问题分开

3. 换行规则：
   - 同一道题内部只用单个换行
   - 不同独立题目之间用两个换行分隔
   - 切记：大题和小题之间绝对不要两个换行！

4. 表格处理：如果题目包含表格，使用Markdown表格格式放在代码框中，例如：
```表格名称
| 列1 | 列2 |
|-----|-----|
| 值1 | 值2 |
```

5. 数学公式用LaTeX格式

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
        var questionIndex = 1

        client.streamChatCompletion(messages, object : StreamingCallback {
            override fun onChunk(text: String) {
                accumulatedText.append(text)

                // 检测是否有两个连续换行符（题目分隔符）
                val content = accumulatedText.toString()
                val separatorIndex = content.indexOf("\n\n")

                if (separatorIndex != -1) {
                    // 找到分隔符，提取前面的题目
                    val questionText = content.substring(0, separatorIndex).trim()
                    if (questionText.isNotBlank()) {
                        val question = Question(id = questionIndex, text = questionText)
                        callback.onQuestionReady(question)
                        questionIndex++
                    }
                    // 保留分隔符后面的内容继续累积
                    accumulatedText.clear()
                    accumulatedText.append(content.substring(separatorIndex + 2))
                    // 通知UI当前chunk属于新的题目
                    callback.onChunk(text, questionIndex)
                } else {
                    callback.onChunk(text, questionIndex)
                }
            }

            override fun onComplete() {
                // 处理最后剩余的内容
                val remainingText = accumulatedText.toString().trim()
                if (remainingText.isNotBlank()) {
                    val question = Question(id = questionIndex, text = remainingText)
                    callback.onQuestionReady(question)
                }
                callback.onComplete()
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
