package com.yy.perfectfloatwindow.network

import android.graphics.Bitmap
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.yy.perfectfloatwindow.data.AIConfig
import com.yy.perfectfloatwindow.data.Question
import com.yy.perfectfloatwindow.data.QuestionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VisionAPI(private val config: AIConfig) {

    private val gson = Gson()

    companion object {
        val OCR_SYSTEM_PROMPT = """你是一个专业的题目识别助手。请仔细分析图片中的所有内容，完整提取每道题目的全部信息。

重要要求：
1. 必须完整识别题目的所有内容，包括：
   - 题目编号（如"1."、"第一题"、"Question 1"等）
   - 题目正文（完整的问题描述）
   - 所有选项（A、B、C、D等，必须包含每个选项的完整内容）
   - 填空题的空格位置用____表示
   - 图表描述（如果题目包含图表，简要描述）

2. 数学公式处理：
   - 识别所有数学符号和公式
   - 转换为LaTeX格式

3. 多道题目处理：
   - 如果图片中有多道题目，每道题单独识别
   - 保持题目原始顺序

请以JSON格式返回，格式如下：
{
  "questions": [
    {
      "id": 1,
      "text": "完整的题目内容，包括所有选项。例如：下列哪个是正确的？\nA. 选项一的完整内容\nB. 选项二的完整内容\nC. 选项三的完整内容\nD. 选项四的完整内容",
      "latex": "如果有数学公式则填写，否则为null",
      "type": "MULTIPLE_CHOICE"
    }
  ]
}

type可选值：TEXT、MATH、MULTIPLE_CHOICE、FILL_BLANK

重要：选择题必须包含所有选项的完整文字！
只返回JSON，不要有其他文字。""".trimIndent()
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
                        "text" to "请识别图片中的所有题目，按JSON格式返回。"
                    )
                )
            )
        )

        val response = client.chatCompletion(messages, stream = false)
        val content = OpenAIClient.parseNonStreamingResponse(response)
        parseQuestionsFromJson(content)
    }

    private fun parseQuestionsFromJson(json: String): List<Question> {
        return try {
            // Try to extract JSON from the response (in case there's extra text)
            val jsonStart = json.indexOf('{')
            val jsonEnd = json.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1) {
                return listOf(createFallbackQuestion(json))
            }

            val cleanJson = json.substring(jsonStart, jsonEnd + 1)
            val jsonObject = JsonParser.parseString(cleanJson).asJsonObject
            val questionsArray = jsonObject.getAsJsonArray("questions")

            if (questionsArray == null || questionsArray.size() == 0) {
                return emptyList()
            }

            questionsArray.mapIndexed { index, element ->
                val obj = element.asJsonObject
                Question(
                    id = obj.get("id")?.asInt ?: (index + 1),
                    text = obj.get("text")?.asString ?: "",
                    latex = obj.get("latex")?.let { if (it.isJsonNull) null else it.asString },
                    type = parseQuestionType(obj.get("type")?.asString)
                )
            }.filter { it.text.isNotBlank() }
        } catch (e: Exception) {
            // If parsing fails, return the whole response as a single question
            listOf(createFallbackQuestion(json))
        }
    }

    private fun parseQuestionType(type: String?): QuestionType {
        return when (type?.uppercase()) {
            "MATH" -> QuestionType.MATH
            "MULTIPLE_CHOICE" -> QuestionType.MULTIPLE_CHOICE
            "FILL_BLANK" -> QuestionType.FILL_BLANK
            else -> QuestionType.TEXT
        }
    }

    private fun createFallbackQuestion(text: String): Question {
        return Question(
            id = 1,
            text = text.trim(),
            type = QuestionType.TEXT
        )
    }
}
