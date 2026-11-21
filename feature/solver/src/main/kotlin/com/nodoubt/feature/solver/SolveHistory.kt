package com.nodoubt.feature.solver

import android.util.AtomicFile
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nodoubt.core.model.Usage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** One finished answer; thinking is deliberately not kept. */
data class HistoryAnswer(val usage: Usage, val model: String, val text: String)

data class HistoryEntry(val id: String, val createdAt: Long, val question: String, val answers: List<HistoryAnswer>)

interface HistoryStorage {
    fun read(): List<HistoryEntry>
    fun write(entries: List<HistoryEntry>)
}

/** Private app storage; a damaged file is set aside instead of blocking new records. */
class FileHistoryStorage(private val file: File) : HistoryStorage {
    override fun read(): List<HistoryEntry> {
        if (!file.exists()) return emptyList()
        return try { HistoryCodec.decode(AtomicFile(file).readFully().toString(Charsets.UTF_8)) }
        catch (_: Exception) { file.renameTo(File("${file.path}.bad")); emptyList() }
    }

    override fun write(entries: List<HistoryEntry>) {
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(HistoryCodec.encode(entries).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (error: Exception) { atomic.failWrite(output); throw error }
    }
}

/**
 * Records each question once its FAST or DEEP answer completes. A later completion of the same question
 * (retry, other mode, other model) updates the same record; unfinished or failed answers keep the last finished one.
 */
class SolveHistory(
    private val storage: HistoryStorage, scope: CoroutineScope,
    io: CoroutineDispatcher = Dispatchers.IO, private val clock: () -> Long = System::currentTimeMillis
) {
    private val mutableEntries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = mutableEntries.asStateFlow()
    private val ids = mutableMapOf<Pair<Long, Int>, String>()
    private val saves = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch(io) {
            val stored = runCatching { storage.read() }.getOrDefault(emptyList())
            // Records made while loading stay in front; the file is only written after it was read.
            mutableEntries.update { current -> (current + stored.filter { old -> current.none { it.id == old.id } }).take(MAX_ENTRIES) }
            if (mutableEntries.value != stored) saves.trySend(Unit)
            for (unit in saves) runCatching { storage.write(mutableEntries.value) }
        }
    }

    fun record(state: SolverState) {
        for (question in state.questions) {
            if (!question.ready || question.text.isBlank()) continue
            val finished = HISTORY_USAGES.mapNotNull { usage ->
                question.answers[usage]?.takeIf { it.status == RunStatus.COMPLETE && it.text.isNotBlank() }
                    ?.let { HistoryAnswer(usage, it.model?.modelId.orEmpty(), it.text.trim()) }
            }
            if (finished.isEmpty()) continue
            val id = ids.getOrPut(state.session to question.id) { UUID.randomUUID().toString() }
            change { upsertHistory(it, id, question.text.trim(), finished, clock()) }
        }
    }

    fun delete(id: String) = change { list -> list.filterNot { it.id == id } }

    fun restore(entry: HistoryEntry, index: Int) = change { list ->
        if (list.any { it.id == entry.id }) list
        else list.toMutableList().apply { add(index.coerceIn(0, size), entry) }
    }

    fun clear() = change { emptyList() }

    private fun change(transform: (List<HistoryEntry>) -> List<HistoryEntry>) {
        val before = mutableEntries.value
        mutableEntries.update(transform)
        if (mutableEntries.value !== before) saves.trySend(Unit)
    }

    companion object { const val MAX_ENTRIES = 300 }
}

private val HISTORY_USAGES = listOf(Usage.FAST, Usage.DEEP)

internal fun upsertHistory(
    entries: List<HistoryEntry>, id: String, question: String, finished: List<HistoryAnswer>, now: Long
): List<HistoryEntry> {
    val index = entries.indexOfFirst { it.id == id }
    val existing = entries.getOrNull(index)
    val answers = HISTORY_USAGES.mapNotNull { usage ->
        finished.firstOrNull { it.usage == usage } ?: existing?.answers?.firstOrNull { it.usage == usage }
    }
    val entry = HistoryEntry(id, existing?.createdAt ?: now, question, answers)
    return when {
        entry == existing -> entries
        existing != null -> entries.toMutableList().apply { set(index, entry) }
        else -> (listOf(entry) + entries).take(SolveHistory.MAX_ENTRIES)
    }
}

internal object HistoryCodec {
    fun encode(entries: List<HistoryEntry>): String = JsonObject().apply {
        addProperty("version", 1)
        add("entries", JsonArray().apply { entries.forEach { entry ->
            add(JsonObject().apply {
                addProperty("id", entry.id)
                addProperty("createdAt", entry.createdAt)
                addProperty("question", entry.question)
                add("answers", JsonArray().apply { entry.answers.forEach { answer ->
                    add(JsonObject().apply {
                        addProperty("usage", answer.usage.name)
                        addProperty("model", answer.model)
                        addProperty("text", answer.text)
                    })
                } })
            })
        } })
    }.toString()

    fun decode(text: String): List<HistoryEntry> =
        JsonParser.parseString(text).asJsonObject.getAsJsonArray("entries").mapNotNull { element ->
            runCatching {
                val item = element.asJsonObject
                HistoryEntry(item.get("id").asString, item.get("createdAt").asLong, item.get("question").asString,
                    item.getAsJsonArray("answers").mapNotNull { answer ->
                        runCatching {
                            val value = answer.asJsonObject
                            HistoryAnswer(Usage.valueOf(value.get("usage").asString),
                                value.get("model")?.asString.orEmpty(), value.get("text").asString)
                        }.getOrNull()?.takeIf { it.usage != Usage.OCR }
                    })
            }.getOrNull()
        }
}
