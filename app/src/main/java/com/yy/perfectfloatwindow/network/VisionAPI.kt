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

2. 小题归属判断（最重要，必须严格遵守）：
   【判断流程】
   - 第一步：通读全图，识别所有题目编号和层级结构
   - 第二步：找出主题号（如1、2、3或一、二、三）和子题号（如(1)(2)、①②、a.b.c.）
   - 第三步：判断子题号前是否有共同的题干、材料、图表、背景说明

   【必须合并为一道题的情况】
   - 有明确子题号（如(1)(2)(3)、①②③、a.b.c.、第1问/第2问）且前面有共同引导文字或材料
   - 多个问题围绕同一段文字、同一张图、同一个表格、同一个情境
   - 题目开头有"根据以下材料/图表/信息回答问题"、"阅读下文回答"、"结合材料"等引导语
   - 子题之间有逻辑递进关系（如"求...""再求...""由此可得..."）

   【特别注意：无明显子题符号的情况】
   - 有时小题没有(1)(2)等符号，而是用"问："、"求："、"计算："、"说明："等文字引导
   - 连续多个问句但共享同一个前提条件或背景描述
   - 一段材料后紧跟多个独立问句，即使没有编号也属于同一道题
   - 图表下方的多个相关问题，即使格式不统一也要合并

   【常见题型】阅读理解、材料分析、综合计算、实验探究、案例分析、图表分析、证明题、应用题、完形填空、短文改错、语法填空、解答题、论述题、情境题、探究题

   【错误示例】把"1.(1)...(2)...(3)..."拆成三道独立题目 ❌
   【正确示例】将"1."及其下属的所有小问作为一道完整题目输出 ✓

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
