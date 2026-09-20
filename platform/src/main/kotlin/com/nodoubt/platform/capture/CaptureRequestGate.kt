package com.nodoubt.platform.capture

import java.util.concurrent.atomic.AtomicLong

/** Request generations keep delayed frames and callbacks out of a later capture session. */
internal class CaptureRequestGate {
    private val generation = AtomicLong(0)
    fun begin(): Long = generation.incrementAndGet()
    fun isCurrent(id: Long): Boolean = id > 0 && generation.get() == id
    fun finish(id: Long): Boolean = id > 0 && generation.compareAndSet(id, id + 1)
    fun invalidate() { generation.incrementAndGet() }
}
