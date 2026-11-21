package com.nodoubt.feature.solver

import com.nodoubt.core.model.Usage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SolveHistoryTest {
    private class MemoryStorage(var stored: List<HistoryEntry> = emptyList()) : HistoryStorage {
        var writes = 0
        override fun read() = stored
        override fun write(entries: List<HistoryEntry>) { stored = entries; writes++ }
    }

    private fun answer(text: String, status: RunStatus = RunStatus.COMPLETE, thinking: String = "", model: String = "m") =
        AnswerState(text = text, thinking = thinking, status = status, model = ref(model))

    private fun state(session: Long, vararg questions: QuestionState) = SolverState(session, questions.toList())

    @Test fun `finished answers are recorded without thinking and updated in place`() = runTest {
        val storage = MemoryStorage()
        val history = SolveHistory(storage, backgroundScope, StandardTestDispatcher(testScheduler)) { 1000L }
        runCurrent()
        val running = QuestionState(1, "1+1=?", ready = true,
            answers = mapOf(Usage.FAST to answer("部分", RunStatus.RUNNING), Usage.DEEP to AnswerState()))
        history.record(state(1, running, QuestionState(2, "识别中")))
        assertTrue(history.entries.value.isEmpty())

        val fast = running.copy(answers = running.answers + (Usage.FAST to answer("答案 2", thinking = "很长的思考", model = "fast")))
        history.record(state(1, fast))
        history.record(state(1, fast))
        val deep = fast.copy(answers = fast.answers + (Usage.DEEP to answer("深度答案", model = "deep")))
        history.record(state(1, deep))
        // A failed retry keeps the last finished answer of that mode.
        history.record(state(1, deep.copy(answers = deep.answers + (Usage.FAST to answer("", RunStatus.ERROR)))))
        runCurrent()

        val entry = history.entries.value.single()
        assertEquals("1+1=?", entry.question)
        assertEquals(listOf(HistoryAnswer(Usage.FAST, "fast", "答案 2"), HistoryAnswer(Usage.DEEP, "deep", "深度答案")), entry.answers)
        assertEquals(entry, storage.stored.single())
        assertFalse(HistoryCodec.encode(storage.stored).contains("思考"))

        // A new screenshot with the same text is a new record, newest first.
        history.record(state(2, fast.copy(answers = fast.answers + (Usage.FAST to answer("再次")))))
        runCurrent()
        assertEquals(listOf("再次", "答案 2"), history.entries.value.map { it.answers.first().text })
    }

    @Test fun `stored records load, delete, restore, clear and survive a round trip`() = runTest {
        val old = HistoryEntry("old", 5, "旧题", listOf(HistoryAnswer(Usage.DEEP, "d", "旧答")))
        val storage = MemoryStorage(listOf(old))
        val history = SolveHistory(storage, backgroundScope, StandardTestDispatcher(testScheduler)) { 9L }
        runCurrent()
        assertEquals(listOf(old), history.entries.value)
        assertEquals(0, storage.writes)

        val removed = history.delete(setOf("old"))
        runCurrent()
        assertTrue(storage.stored.isEmpty())
        history.restore(removed)
        runCurrent()
        assertEquals(listOf(old), storage.stored)
        assertEquals(listOf(old), HistoryCodec.decode(HistoryCodec.encode(storage.stored)))
        history.clear()
        runCurrent()
        assertTrue(storage.stored.isEmpty())
    }

    @Test fun `several selected entries delete together and undo restores their order`() = runTest {
        val list = (1..5).map { HistoryEntry("e$it", it.toLong(), "q$it", listOf(HistoryAnswer(Usage.FAST, "", "a"))) }
        val storage = MemoryStorage(list)
        val history = SolveHistory(storage, backgroundScope, StandardTestDispatcher(testScheduler)) { 9L }
        runCurrent()
        val removed = history.delete(setOf("e1", "e3", "e5", "missing"))
        runCurrent()
        assertEquals(listOf("e2", "e4"), storage.stored.map { it.id })
        assertEquals(listOf(0, 2, 4), removed.map { it.first })
        history.restore(removed)
        runCurrent()
        assertEquals(list, storage.stored)
        assertTrue(history.delete(emptySet()).isEmpty())
    }

    @Test fun `history keeps the newest entries within the limit`() {
        var list = emptyList<HistoryEntry>()
        repeat(SolveHistory.MAX_ENTRIES + 5) { list = upsertHistory(list, "id$it", "q$it", listOf(HistoryAnswer(Usage.FAST, "", "a")), it.toLong()) }
        assertEquals(SolveHistory.MAX_ENTRIES, list.size)
        assertEquals("q${SolveHistory.MAX_ENTRIES + 4}", list.first().question)
    }

    @Test fun `damaged entries are skipped and previews are plain text`() {
        val decoded = HistoryCodec.decode("""{"entries":[{"id":"a"},{"id":"b","createdAt":1,"question":"q",
            "answers":[{"usage":"FAST","text":"t"},{"usage":"OCR","text":"x"},{"usage":"??","text":"y"}]}]}""")
        assertEquals(listOf(HistoryEntry("b", 1, "q", listOf(HistoryAnswer(Usage.FAST, "", "t")))), decoded)
        assertEquals("求 x^2+1=5 的解", plainPreview("求 \$x^2+1=5\$\n 的解"))
        assertEquals("化简 6/8 与 √x", plainPreview("化简 \$\\frac{6}{8}\$ 与 √\$\\mathrm{x}\$"))
        assertEquals("（空题目）", plainPreview("  "))
    }
}
