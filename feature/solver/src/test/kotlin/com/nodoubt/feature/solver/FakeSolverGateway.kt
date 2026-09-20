package com.nodoubt.feature.solver

import com.nodoubt.core.model.AiEvent
import com.nodoubt.core.model.AiRequest
import com.nodoubt.core.model.ContentPart
import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.ModelRef
import com.nodoubt.core.model.Protocol
import com.nodoubt.core.model.Usage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.withContext

internal class FakeSolverGateway : SolverGateway {
    val choices = mutableMapOf(
        Usage.OCR to listOf(ref("ocr1")),
        Usage.FAST to listOf(ref("fast1")),
        Usage.DEEP to listOf(ref("deep1"))
    )
    val calls = mutableListOf<FakeSolverCall>()
    val resolutionFailures = mutableSetOf<String>()
    val endpoints = mutableMapOf<String, ModelEndpoint>()

    override fun candidates(usage: Usage): List<ModelRef> = choices[usage].orEmpty()
    override fun resolve(ref: ModelRef): ModelEndpoint {
        check(ref.modelId !in resolutionFailures) { "missing configuration" }
        return endpoints[ref.modelId] ?: ModelEndpoint(Protocol.OPENAI_COMPLETIONS, "https://unused.invalid/v1", ref.modelId)
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun stream(request: AiRequest): Flow<AiEvent> = object : Flow<AiEvent> {
        override suspend fun collect(collector: FlowCollector<AiEvent>) {
            val call = FakeSolverCall(request)
            calls.add(call)
            try {
                for (command in call.commands) when (command) {
                    is FakeSolverCall.Event -> collector.emit(command.value)
                    is FakeSolverCall.Fail -> throw command.error
                    FakeSolverCall.End -> return
                }
            } catch (cancelled: CancellationException) {
                call.cancelled = true
                // Deliberately imitate a badly behaved upstream callback after cancellation.
                withContext(NonCancellable) {
                    if (call.afterCancellation.isNotEmpty() && call.afterCancellationDelayMs > 0) delay(call.afterCancellationDelayMs)
                    call.afterCancellation.forEach { collector.emit(it) }
                }
                throw cancelled
            } finally {
                call.finished = true
            }
        }
    }

    fun calls(model: String): List<FakeSolverCall> = calls.filter { it.request.endpoint.modelId == model }
    fun last(model: String): FakeSolverCall = calls(model).last()
}

internal class FakeSolverCall(val request: AiRequest) {
    sealed interface Command
    data class Event(val value: AiEvent) : Command
    data class Fail(val error: Exception) : Command
    data object End : Command
    val commands = Channel<Command>(Channel.UNLIMITED)
    var cancelled = false
    var finished = false
    var afterCancellation: List<AiEvent> = emptyList()
    var afterCancellationDelayMs = 1L

    fun text(value: String) { check(commands.trySend(Event(AiEvent.TextDelta(value))).isSuccess) }
    fun thinking(value: String) { check(commands.trySend(Event(AiEvent.ThinkingDelta(value))).isSuccess) }
    fun fail(message: String = "upstream failed") { check(commands.trySend(Fail(IllegalStateException(message))).isSuccess) }
    fun finish() {
        check(commands.trySend(Event(AiEvent.Complete)).isSuccess)
        check(commands.trySend(End).isSuccess)
    }
}

internal fun ref(model: String) = ModelRef("test-channel", model)
internal fun image(value: String = "image") = ContentPart.Image(value)
internal fun SolverController.answerState(usage: Usage) = state.value.questions.single().answers.getValue(usage)
