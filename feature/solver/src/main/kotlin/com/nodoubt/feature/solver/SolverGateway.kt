package com.nodoubt.feature.solver

import com.nodoubt.core.ai.AiClient
import com.nodoubt.core.config.ConfigRepository
import com.nodoubt.core.model.AiEvent
import com.nodoubt.core.model.AiRequest
import com.nodoubt.core.model.ModelEndpoint
import com.nodoubt.core.model.ModelRef
import com.nodoubt.core.model.Usage
import kotlinx.coroutines.flow.Flow

interface SolverGateway {
    fun candidates(usage: Usage): List<ModelRef>
    fun resolve(ref: ModelRef): ModelEndpoint
    fun stream(request: AiRequest): Flow<AiEvent>
}

class ConfiguredSolverGateway(
    private val repository: ConfigRepository,
    private val client: AiClient
) : SolverGateway {
    override fun candidates(usage: Usage): List<ModelRef> {
        val binding = repository.state.value.bindings[usage] ?: return emptyList()
        return (listOfNotNull(binding.selected) + binding.entries).distinct()
    }

    override fun resolve(ref: ModelRef): ModelEndpoint = repository.resolve(ref)
    override fun stream(request: AiRequest): Flow<AiEvent> = client.stream(request)
}
