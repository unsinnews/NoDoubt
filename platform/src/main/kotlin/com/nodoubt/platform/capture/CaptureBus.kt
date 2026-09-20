package com.nodoubt.platform.capture

import com.nodoubt.core.model.ContentPart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class CaptureStatus(val ready: Boolean = false, val capturing: Boolean = false, val error: String? = null, val requestId: Long = 0)
data class CapturedFrame(val requestId: Long, val image: ContentPart.Image)

object CaptureBus {
    private val requests = CaptureRequestGate()
    private val mutableStatus = MutableStateFlow(CaptureStatus())
    val status = mutableStatus.asStateFlow()
    private val mutableImages = MutableSharedFlow<CapturedFrame>(extraBufferCapacity = 1)
    val images = mutableImages.asSharedFlow()
    fun beginRequest(): Long = requests.begin().also {
        mutableStatus.value = mutableStatus.value.copy(capturing = false, error = null, requestId = it)
    }
    fun isCurrentRequest(id: Long): Boolean = requests.isCurrent(id)
    fun finishRequest(id: Long): Boolean = requests.finish(id).also {
        if (it) mutableStatus.value = mutableStatus.value.copy(capturing = false)
    }
    fun invalidateRequests() {
        requests.invalidate()
        mutableStatus.value = mutableStatus.value.copy(capturing = false)
    }
    internal fun status(value: CaptureStatus) {
        if (value.requestId == 0L || requests.isCurrent(value.requestId)) mutableStatus.value = value
    }
    internal suspend fun image(requestId: Long, value: ContentPart.Image) {
        if (requests.isCurrent(requestId)) mutableImages.emit(CapturedFrame(requestId, value))
    }
}
