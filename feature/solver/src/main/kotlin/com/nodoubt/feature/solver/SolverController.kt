package com.nodoubt.feature.solver

import com.nodoubt.core.ai.AiClient
import com.nodoubt.core.ai.ThinkingRequest
import com.nodoubt.core.ai.ThinkingRequests
import com.nodoubt.core.config.ConfigRepository
import com.nodoubt.core.model.AiEvent
import com.nodoubt.core.model.AiMessage
import com.nodoubt.core.model.AiRequest
import com.nodoubt.core.model.ContentPart
import com.nodoubt.core.model.ModelRef
import com.nodoubt.core.model.Usage
import com.nodoubt.core.model.ThinkingOptions
import com.nodoubt.core.model.ThinkingOverride
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SolverController(
    private val gateway: SolverGateway,
    private val scope: CoroutineScope
) {
    constructor(repository: ConfigRepository, client: AiClient, scope: CoroutineScope) :
        this(ConfiguredSolverGateway(repository, client), scope)

    private val mutableState = MutableStateFlow(SolverState())
    val state: StateFlow<SolverState> = mutableState.asStateFlow()
    private var recognitionJob: Job? = null
    private var recognitionRevision = 0L
    private val answerJobs = mutableMapOf<Pair<Int, Usage>, Job>()
    private val answerRequests = mutableMapOf<Pair<Int, Usage>, List<ModelRef>>()
    private val revisions = mutableMapOf<Pair<Int, Usage>, Int>()
    private var lastImage: ContentPart.Image? = null

    fun recognize(image: ContentPart.Image) {
        cancelRequests()
        lastImage = image
        val session = state.value.session + 1
        val revision = recognitionRevision
        mutableState.value = SolverState(session = session, recognition = RunStatus.RUNNING)
        recognitionJob = scope.launch { recognizeCandidates(session, revision, image) }
    }

    fun retryRecognition() { lastImage?.let(::recognize) }

    /** Preparing a replacement screenshot is not a user stop and must not flash stopped UI. */
    fun prepareCapture() {
        cancelRequests()
        lastImage = null
        mutableState.value = SolverState(session = state.value.session)
    }

    private suspend fun recognizeCandidates(session: Long, revision: Long, image: ContentPart.Image) {
        fun active() = state.value.session == session && recognitionRevision == revision
        val candidates = candidates(Usage.OCR)
        if (candidates.isEmpty()) {
            recognitionFailed(session, revision, "未设置截图识题模型")
            return
        }
        for ((position, ref) in candidates.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (!active()) return
            var received = false
            val parser = QuestionStreamParser(
                onProgress = { id, text -> if (active()) changeQuestion(session, id) { it.copy(text = text) } },
                onQuestion = { id, text ->
                    if (active()) {
                        changeQuestion(session, id) { it.copy(text = text, ready = true) }
                        if (active()) answer(id, Usage.FAST)
                        if (active()) answer(id, Usage.DEEP)
                    }
                }
            )
            try {
                val request = AiRequest(gateway.resolve(ref), listOf(AiMessage("user", listOf(
                    image, ContentPart.Text("请识别图片中的所有题目。")
                ))), SolverPrompts.ocr)
                gateway.stream(request).collect { event ->
                    currentCoroutineContext().ensureActive()
                    if (active()) when (event) {
                        is AiEvent.TextDelta -> { received = received || event.text.isNotEmpty(); parser.append(event.text) }
                        is AiEvent.ThinkingDelta -> received = received || event.text.isNotEmpty()
                        else -> Unit
                    }
                }
                currentCoroutineContext().ensureActive()
                if (!active()) return
                parser.finish()
                mutableState.update { if (!active()) it else it.copy(
                    recognition = RunStatus.COMPLETE,
                    questions = if (parser.noQuestion) emptyList() else it.questions,
                    error = if (parser.noQuestion || !parser.hasContent) "未识别到题目，请重截" else null
                ) }
                return
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (!active()) return
                // Never automatically replay a paid request after any response content arrived.
                if (received || position == candidates.lastIndex) {
                    recognitionFailed(session, revision, error.message ?: "识别失败")
                    return
                }
            }
        }
    }

    fun answer(questionId: Int, usage: Usage, model: ModelRef? = null) {
        val current = state.value.questions.firstOrNull { it.id == questionId }?.answers?.get(usage)
        val override = current?.thinkingOverride.takeIf { model != null && model == current?.model }
        startAnswer(questionId, usage, model, force = false, thinkingOverride = override)
    }

    private fun startAnswer(questionId: Int, usage: Usage, model: ModelRef?, force: Boolean,
        thinkingOverride: ThinkingOverride? = null, preflight: ThinkingRequest? = null) {
        if (usage == Usage.OCR) return
        val question = state.value.questions.firstOrNull { it.id == questionId && it.ready } ?: return
        val session = state.value.session
        val key = questionId to usage
        val refs = model?.let(::listOf) ?: candidates(usage)
        val current = question.answers.getValue(usage)
        if (!force && answerJobs[key]?.isActive == true && current.status == RunStatus.RUNNING &&
            (answerRequests[key] == refs || (model != null && current.model == model))) return
        val revision = (revisions[key] ?: 0) + 1
        revisions[key] = revision
        answerJobs.remove(key)?.cancel()
        answerRequests[key] = refs
        fun update(transform: (AnswerState) -> AnswerState) {
            if (revisions[key] == revision) changeQuestion(session, questionId) {
                it.copy(answers = it.answers + (usage to transform(it.answers.getValue(usage))))
            }
        }
        update { AnswerState(status = RunStatus.RUNNING, model = refs.firstOrNull(), runId = revision,
            thinkingSnapshot = preflight?.snapshot, thinkingOverride = thinkingOverride) }
        mutableState.update {
            if (it.session == session && revisions[key] == revision) it.copy(stoppedModes = it.stoppedModes - usage) else it
        }
        val job = scope.launch {
            if (state.value.session != session || revisions[key] != revision) return@launch
            if (refs.isEmpty()) {
                update { it.copy(status = RunStatus.ERROR, error = "未设置${usage.label}模型") }
                return@launch
            }
            for ((position, ref) in refs.withIndex()) {
                currentCoroutineContext().ensureActive()
                var received = false
                update { it.copy(model = ref, thinkingSnapshot = null) }
                try {
                    val prepared = preflight?.takeIf { position == 0 } ?: prepareAnswer(question.text, ref, thinkingOverride)
                    update { it.copy(thinkingSnapshot = prepared.snapshot) }
                    gateway.stream(prepared.request).collect { event ->
                        currentCoroutineContext().ensureActive()
                        when (event) {
                            is AiEvent.TextDelta -> { received = received || event.text.isNotEmpty(); update { it.appendText(event.text) } }
                            is AiEvent.ThinkingDelta -> { received = received || event.text.isNotEmpty(); update { it.appendThinking(event.text) } }
                            else -> Unit
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    update { it.finishThinking().copy(status = RunStatus.COMPLETE) }
                    return@launch
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (received || position == refs.lastIndex) {
                        update { it.finishThinking().copy(status = RunStatus.ERROR, error = error.message ?: "回答失败") }
                        return@launch
                    }
                }
            }
        }
        answerJobs[key] = job
        job.invokeOnCompletion {
            if (answerJobs[key] === job) {
                answerJobs.remove(key)
                answerRequests.remove(key)
            }
        }
    }

    fun stop(questionId: Int, usage: Usage) {
        if (usage == Usage.OCR) return
        val current = state.value.questions.firstOrNull { it.id == questionId && it.ready }?.answers?.get(usage) ?: return
        if (current.status != RunStatus.RUNNING && current.status != RunStatus.IDLE) return
        val key = questionId to usage
        revisions[key] = (revisions[key] ?: 0) + 1
        answerJobs.remove(key)?.cancel()
        answerRequests.remove(key)
        changeQuestion(state.value.session, questionId) {
            val answer = it.answers.getValue(usage)
            it.copy(answers = it.answers + (usage to answer.finishThinking().copy(status = RunStatus.STOPPED)))
        }
    }

    /** A deliberate retry restarts even a currently streaming request using its selected model. */
    fun retry(questionId: Int, usage: Usage): Result<Unit> {
        val current = state.value.questions.firstOrNull { it.id == questionId }?.answers?.get(usage)
        return retryWithThinking(questionId, usage, current?.thinkingOverride)
    }

    fun thinkingOptions(questionId: Int, usage: Usage): ThinkingOptions? = runCatching {
        require(usage != Usage.OCR)
        val question = state.value.questions.first { it.id == questionId && it.ready }
        val current = question.answers.getValue(usage)
        val protocol = current.thinkingSnapshot?.protocol ?: gateway.resolve(current.model ?: candidates(usage).first()).protocol
        ThinkingRequests.options(protocol)
    }.getOrNull()

    /** Validate before changing revisions or canceling the currently visible answer. */
    fun retryWithThinking(questionId: Int, usage: Usage, override: ThinkingOverride?): Result<Unit> = runCatching {
        require(usage != Usage.OCR) { "截图识题不支持此操作。" }
        val question = state.value.questions.firstOrNull { it.id == questionId && it.ready }
            ?: error("题目尚未识别完成。")
        val ref = question.answers.getValue(usage).model ?: candidates(usage).firstOrNull()
            ?: error("请先选择解题模型。")
        val prepared = prepareAnswer(question.text, ref, override)
        startAnswer(questionId, usage, ref, force = true, thinkingOverride = override, preflight = prepared)
    }

    private fun prepareAnswer(text: String, ref: ModelRef, override: ThinkingOverride?): ThinkingRequest =
        ThinkingRequests.prepare(AiRequest(gateway.resolve(ref), listOf(
            AiMessage("user", "请解答以下题目：\n\n$text")
        ), SolverPrompts.answer), override)

    /** Original sheet behavior: stop this mode across questions, plus any unfinished OCR. */
    fun stopMode(usage: Usage) {
        if (usage == Usage.OCR) return
        val questions = state.value.questions
        if (state.value.recognition != RunStatus.RUNNING && questions.none { q -> q.ready &&
            q.answers.getValue(usage).status in setOf(RunStatus.RUNNING, RunStatus.IDLE) }) return
        if (questions.none { q -> q.answers.getValue(usage).let { it.text.isNotEmpty() || it.thinking.isNotEmpty() } }) {
            stopAll()
            return
        }
        recognitionRevision++
        recognitionJob?.cancel()
        recognitionJob = null
        mutableState.update { it.copy(
            recognition = if (it.recognition == RunStatus.RUNNING) RunStatus.STOPPED else it.recognition,
            stoppedModes = it.stoppedModes + usage
        ) }
        questions.forEach { stop(it.id, usage) }
    }

    fun stopAll() {
        val active = state.value.recognition == RunStatus.RUNNING || state.value.questions.any { q ->
            q.ready && q.answers.values.any { it.status == RunStatus.RUNNING || it.status == RunStatus.IDLE }
        }
        cancelRequests()
        mutableState.update { current -> current.copy(
            recognition = if (current.recognition == RunStatus.RUNNING) RunStatus.STOPPED else current.recognition,
            stoppedModes = if (active) current.stoppedModes + setOf(Usage.FAST, Usage.DEEP) else current.stoppedModes,
            questions = current.questions.map { q -> q.copy(answers = q.answers.mapValues { (_, a) ->
                if (a.status == RunStatus.RUNNING || (q.ready && a.status == RunStatus.IDLE)) a.finishThinking().copy(status = RunStatus.STOPPED) else a
            }) }
        ) }
    }

    private fun cancelRequests() {
        recognitionRevision++
        revisions.keys.toList().forEach { revisions[it] = revisions.getValue(it) + 1 }
        recognitionJob?.cancel()
        recognitionJob = null
        answerJobs.values.toList().forEach { it.cancel() }
        answerJobs.clear()
        answerRequests.clear()
    }

    fun clear() {
        cancelRequests()
        lastImage = null
        mutableState.value = SolverState(session = state.value.session + 1)
    }

    fun candidates(usage: Usage): List<ModelRef> {
        return gateway.candidates(usage).distinct()
    }

    private fun recognitionFailed(session: Long, revision: Long, message: String) {
        mutableState.update {
            if (it.session == session && recognitionRevision == revision) it.copy(recognition = RunStatus.ERROR, error = message) else it
        }
    }

    private fun changeQuestion(session: Long, id: Int, transform: (QuestionState) -> QuestionState) {
        mutableState.update { current ->
            if (current.session != session) current else {
                val question = current.questions.firstOrNull { it.id == id } ?: QuestionState(id, "")
                current.copy(questions = (current.questions.filterNot { it.id == id } + transform(question)).sortedBy { it.id })
            }
        }
    }
}
