package com.nodoubt.platform.capture

import org.junit.Assert.*
import org.junit.Test

class CaptureFrameGateTest {
    @Test fun `recapture never accepts a buffered frame from the previous image source`() {
        val gate = CaptureFrameGate()
        val oldSource = gate.begin(11)
        val currentSource = gate.begin(12)
        assertFalse(gate.take(oldSource))
        assertEquals(12L, gate.pendingRequestId)
        assertTrue(gate.take(currentSource))
        assertNull(gate.pendingRequestId)
    }

    @Test fun `resize invalidates old source even while the screenshot request id stays the same`() {
        val gate = CaptureFrameGate()
        val portrait = gate.begin(30)
        val landscape = gate.begin(30)
        assertFalse(gate.take(portrait))
        assertTrue(gate.take(landscape))
        assertFalse(gate.take(landscape))
    }

    @Test fun `cancelled screenshot cannot be completed by a late frame or cancel a newer screenshot`() {
        val gate = CaptureFrameGate()
        val dismissed = gate.begin(40)
        gate.cancel()
        assertFalse(gate.take(dismissed))
        val newer = gate.begin(41)
        gate.cancel(40)
        assertFalse(gate.take(dismissed))
        assertTrue(gate.take(newer))
    }

    @Test fun `waiting for a newly bound static screen source cannot reuse a consumed frame`() {
        val gate = CaptureFrameGate()
        val previous = gate.begin(50)
        assertTrue(gate.take(previous))
        val next = gate.begin(51)
        repeat(3) { assertFalse(gate.take(previous)) }
        assertEquals(51L, gate.pendingRequestId)
        // No wall-clock or producer timestamp is required: a newly bound source owns its frames.
        assertTrue(gate.take(next))
        assertFalse(gate.take(next))
    }
}
