package com.nodoubt.feature.solver

import org.junit.Assert.*
import org.junit.Test

class QuestionStreamParserTest {
    @Test fun delimitersAcrossEveryChunkBoundaryDoNotLeakOrDuplicateQuestions() {
        val source = "第一题\n\n| A | B |\n\n选项|||第二题||||||第三题"
        for (size in 1..source.length) {
            val questions = mutableListOf<Pair<Int, String>>()
            val parser = QuestionStreamParser({ _, _ -> }, { id, text -> questions += id to text })
            source.chunked(size).forEach(parser::append)
            parser.finish()
            assertEquals(listOf(1 to "第一题\n\n| A | B |\n\n选项", 2 to "第二题", 3 to "第三题"), questions)
        }
    }

    @Test fun noQuestionSentinelDoesNotLaunchAnswer() {
        val questions = mutableListOf<String>()
        val parser = QuestionStreamParser({ _, _ -> }, { _, text -> questions += text })
        listOf("未识", "别到有效", "题目").forEach(parser::append)
        parser.finish()
        assertTrue(parser.noQuestion)
        assertTrue(questions.isEmpty())
    }

    @Test fun completeQuestionIsAvailableBeforeStreamFinishes() {
        val questions = mutableListOf<String>()
        val parser = QuestionStreamParser({ _, _ -> }, { _, text -> questions += text })
        parser.append("第一题||")
        assertTrue(questions.isEmpty())
        parser.append("|第二题")
        assertEquals(listOf("第一题"), questions)
        parser.finish()
        assertEquals(listOf("第一题", "第二题"), questions)
    }
}
