package com.nodoubt.platform.capture

internal data class CaptureFrameTicket(val requestId: Long, val sourceVersion: Long)

/** Every capture (or resize while waiting) owns a newly bound image source, accepted only once. */
internal class CaptureFrameGate {
    private var sourceVersion = 0L
    private var pending: CaptureFrameTicket? = null
    val pendingRequestId: Long? get() = pending?.requestId

    fun begin(requestId: Long): CaptureFrameTicket {
        require(requestId > 0)
        return CaptureFrameTicket(requestId, ++sourceVersion).also { pending = it }
    }

    fun take(ticket: CaptureFrameTicket): Boolean {
        if (pending != ticket) return false
        pending = null
        return true
    }

    fun cancel(requestId: Long? = null) {
        if (requestId == null || pending?.requestId == requestId) pending = null
    }
}
