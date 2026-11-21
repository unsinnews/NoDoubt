package com.nodoubt.app

import android.app.Application
import android.content.Context
import com.nodoubt.core.ai.AiClient
import com.nodoubt.core.config.ConfigRepository
import com.nodoubt.feature.solver.FileHistoryStorage
import com.nodoubt.feature.solver.SolveHistory
import com.nodoubt.feature.solver.SolverController
import com.nodoubt.platform.capture.CaptureBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class NoDoubtApplication : Application() {
    lateinit var graph: AppGraph
        private set
    override fun onCreate() { super.onCreate(); graph = AppGraph(this) }
}

class AppGraph(context: Context) {
    val repository = ConfigRepository(context)
    val ai = AiClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val solver = SolverController(repository, ai, scope)
    val history = SolveHistory(FileHistoryStorage(File(context.filesDir, "history.json")), scope)
    init {
        scope.launch {
            CaptureBus.images.collect { frame ->
                if (AssistantService.running.value && CaptureBus.finishRequest(frame.requestId)) solver.recognize(frame.image)
            }
        }
        // Finished answers are recorded as they complete; thinking is not stored.
        scope.launch { solver.state.collect(history::record) }
    }
}

val Context.appGraph: AppGraph get() = (applicationContext as NoDoubtApplication).graph
