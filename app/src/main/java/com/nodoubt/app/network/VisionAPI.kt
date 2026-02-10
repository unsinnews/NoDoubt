package com.nodoubt.app.network

import android.graphics.Bitmap
import com.nodoubt.app.data.AIConfig
import com.nodoubt.app.data.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call

interface OCRStreamingCallback {
    fun onChunk(text: String, currentQuestionIndex: Int)
    fun onQuestionReady(question: Question)
    fun onNoQuestionDetected()  // Called when OCR returns "未识别到有效题目"
    fun onComplete()
    fun onError(error: Exception)
}

class VisionAPI(private val config: AIConfig) {

    companion object {
        val OCR_SYSTEM_PROMPT = """# 角色
你是一位**全学段学术文档结构化专家**。你的专业领域涵盖K12教育、高等教育（理工/文史/医学/艺术）、职业资格考试（CPA/法考/医考）及各类专业技术文档。你具备极强的版面分析能力，能处理复杂的学术符号、编程代码、长篇案例及非标准化逻辑结构。

# 任务
分析图片内容，精准识别并提取每一个独立的**"逻辑单元"**（Logical Unit）。
**核心目标**：将属于同一题目或同一知识点的所有内容（题干、代码块、案例材料、图表、小问、选项、证明过程）完整合并提取，坚决防止语义割裂。

# 思考步骤（请在脑中完成）
1.  **全视域扫描**：判断文档类型（试卷/教辅/讲义/APP截图）及学科背景（数学/代码/法律/英语）。
2.  **边界判定**：运用下述"四维边界判定法"确定内容归属。
3.  **结构化提取**：按规范格式输出内容。

# ⚠️ 核心策略：四维边界判定法

在判断"哪些内容属于同一道题"时，必须同时执行以下四个维度的扫描：

### 1. 编号层级判定 (Hierarchy)
- **父子强绑定**：子编号（如 `(1)`, `②`, `Step 1`）必须无条件归属于最近的父编号（如 `1.`, `一、`, `Q1`）。
- **级联依赖**：如果第(2)问需要用到第(1)问的结果，或者共享同一个题干背景，它们必须合并为一道题。

### 2. 视觉容器判定 (Visual & Layout)
- **缩进与对齐**：若某段文本（如选项或代码）的左侧缩进明显深于题号，它是该题的一部分。
- **区块包裹**：在APP截图或教辅中，被边框、背景色、阴影包裹的所有内容（含标题、正文、注释）为一个整体。
- **双栏排版**：注意跨栏阅读顺序，防止将左栏的半句话和右栏的半句话错误拼接。

### 3. 专业内容强绑定 (Special Content)
- **代码块 (Code)**：编程题中的代码段、输入输出示例，必须包含在题目内，且保留缩进和换行。
- **数学结构**：复杂的矩阵、行列式、分段函数、证明过程（"Proof:"后内容）属于题目核心，不可拆分。
- **表格 (Table)**：⚠️ 表格必须准确判断其归属——若表格是某道题的数据来源或题干的一部分，**必须与该题合并**，绝不可单独拆分。常见信号：题目中出现"根据表格"、"由表可知"、"下表数据"等。
- **长案例 (Case Study)**：法学/医学/商科中的案情描述、病历摘要，若后接多道小题，需将**案例与第一道小题合并**，或作为公共题干提取。
- **例题-解析对**：教材中"【例题】+【解析/解】"构成一个完整的教学闭环，**禁止**将题目和解析拆分开。

### 4. 语义连贯判定 (Semantics)
- **指代词**：句首出现"根据上表"、"该代码"、"结合案例"等指代词，必须与所指内容合并。
- **未尽语意**：以冒号(:)、破折号(——)、连接词(and/or/且)或"已知..."结尾的段落，必然与下一段属于同一题。

# 输出格式

## 1. 题型标识规范
根据内容性质，在每道题最前方添加以下标识之一（使用圆括号）：

*   **常规客观题**：`(单选题)`、`(多选题)`、`(填空题)`、`(判断题)`
*   **理工/专业类**：
    *   `(计算题)`：涉及数值计算、代数运算。
    *   `(证明题)`：要求逻辑推导或数学归纳法证明。
    *   `(编程题)`：涉及代码编写、算法分析、SQL语句。
    *   `(推断题)`：有机推断、地质分析、逻辑推理。
*   **文商法医类**：
    *   `(案例分析)`：基于长篇背景材料/病历/案情的问答。
    *   `(论述题)`、`(翻译题)`、`(作文题)`
*   **教学/其他类**：
    *   `(例题)`：包含题目及完整解答过程。
    *   `(解答题)`：通用兜底，无法归类但包含问答过程。
    *   `(知识点)`：纯定义、定理讲解、公式总结。

## 2. 内容提取规范
- **完整性**：题型标识 + 题号 + 题干(含代码/案例) + (图片/表格) + 所有选项 + 所有小问/解答。
- **填空**：使用 `____` 表示空格。
- **代码**：必须使用 Markdown 代码块包裹，保留缩进。
    ```python
    def example():
        return True
    ```
- **公式**：复杂数学公式强制使用 LaTeX 格式（如 $\sum_{i=1}^{n} x_i$ 或 $\begin{bmatrix} 1 & 0 \\ 0 & 1 \end{bmatrix}$）。
- **表格**：使用 Markdown 表格格式，表题加粗。
- **图片**：使用 `[图片]` 占位。若图中有关键解题文字（如地图地名、电路图参数），写作 `[图片](内容: xxx)`。

## 3. 换行符严格规则（严格遵守！）
- **题内换行**：同一逻辑单元内部（如代码行之间、证明步骤之间、选项之间）使用 **双换行 (`\n\n`)**。
- **题间分隔**：不同的大题/逻辑单元之间使用 **三竖线 (`|||`)** 作为分隔符。

# 输出前自检
□ 代码块是否使用了Markdown格式且保留了缩进？
□ 矩阵/微积分公式是否使用了LaTeX？
□ "例题+解析"是否合并为了一个单元？
□ 题目之间是否使用了 `|||` 分隔？

# ⚠️ 特殊情况：无有效题目
如果图片中**确实没有任何可识别的题目**（例如：纯图片无文字、风景照、头像、无关截图、乱码、模糊不清无法识别等），请**仅输出**以下固定文本，不要输出任何其他内容：
```
未识别到有效题目
```

# 开始
只输出提取后的题目内容，不要输出任何思考过程或解释。""".trimIndent()
    }

    fun extractQuestionsStreaming(bitmap: Bitmap, callback: OCRStreamingCallback): Call {
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
        var displayedLength = 0  // 已经通过onChunk发送显示的长度
        var questionStartIndex = 0  // 当前题目在accumulatedText中的起始位置
        var questionIndex = 1

        return client.streamChatCompletion(messages, object : StreamingCallback {
            override fun onChunk(text: String) {
                accumulatedText.append(text)

                val content = accumulatedText.toString()

                // 循环检查是否有分隔符（可能一次chunk中有多个分隔符）
                while (true) {
                    // 从questionStartIndex开始搜索，确保能找到跨chunk的|||
                    val separatorIndex = content.indexOf("|||", questionStartIndex)

                    if (separatorIndex == -1) {
                        // 没有完整的分隔符，发送内容但保留可能是分隔符开头的字符
                        // 避免发送 | 或 || 到UI（可能是 ||| 的开头）
                        val holdBack = when {
                            content.endsWith("||") -> 2
                            content.endsWith("|") -> 1
                            else -> 0
                        }
                        val safeEndIndex = content.length - holdBack
                        if (safeEndIndex > displayedLength) {
                            val newContent = content.substring(displayedLength, safeEndIndex)
                            callback.onChunk(newContent, questionIndex)
                            displayedLength = safeEndIndex
                        }
                        break
                    }

                    // 有分隔符
                    // 1. 发送分隔符之前未显示的内容到当前题目
                    if (separatorIndex > displayedLength) {
                        val beforeSeparator = content.substring(displayedLength, separatorIndex)
                        if (beforeSeparator.isNotEmpty()) {
                            callback.onChunk(beforeSeparator, questionIndex)
                        }
                    }

                    // 2. 完成当前题目（只提取当前题目的文本，不包含之前的题目）
                    val questionText = content.substring(questionStartIndex, separatorIndex).trim()
                    if (questionText.isNotBlank()) {
                        val question = Question(id = questionIndex, text = questionText)
                        callback.onQuestionReady(question)
                        questionIndex++
                    }

                    // 3. 更新位置到分隔符之后（跳过|||）
                    displayedLength = separatorIndex + 3
                    questionStartIndex = separatorIndex + 3  // 下一题的起始位置

                    // 4. 继续循环检查是否还有更多分隔符
                }
            }

            override fun onComplete() {
                // 处理最后剩余的内容（从当前题目起始位置到末尾）
                val content = accumulatedText.toString()

                // 发送之前保留的字符（流结束时不再需要保留）
                if (content.length > displayedLength) {
                    val remainingChunk = content.substring(displayedLength)
                    if (remainingChunk.isNotEmpty()) {
                        callback.onChunk(remainingChunk, questionIndex)
                    }
                }

                val remainingText = content.substring(questionStartIndex).trim()

                // Check for "no question detected" response
                val noQuestionPattern = "未识别到有效题目"
                if (remainingText.contains(noQuestionPattern) && questionIndex == 1) {
                    // No valid questions found - call the special callback
                    callback.onNoQuestionDetected()
                    return
                }

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
     * 按三竖线分隔符(|||)分割文本，解析为题目列表
     */
    private fun parseQuestionsFromText(text: String): List<Question> {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) {
            return emptyList()
        }

        // 按三竖线分隔符分割题目
        val questionTexts = trimmedText.split("|||")
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
