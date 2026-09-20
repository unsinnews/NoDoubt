package com.nodoubt.platform.capture

import org.junit.Assert.*
import org.junit.Test

class CaptureRequestGateTest {
    @Test fun `replacing a request rejects the old frame and its delayed timeout`() {
        val gate = CaptureRequestGate()
        val first = gate.begin()
        val second = gate.begin()
        assertFalse(gate.isCurrent(first))
        assertFalse(gate.finish(first))
        assertTrue(gate.isCurrent(second))
        assertTrue(gate.finish(second))
        assertFalse(gate.finish(second))
    }

    @Test fun `closing then reopening does not accept the prior service result`() {
        val gate = CaptureRequestGate()
        val oldRequest = gate.begin()
        gate.invalidate()
        assertFalse(gate.isCurrent(oldRequest))
        val reopened = gate.begin()
        assertFalse(gate.finish(oldRequest))
        assertTrue(gate.isCurrent(reopened))
        assertFalse(gate.isCurrent(0))
    }
}
