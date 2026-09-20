package com.nodoubt.platform.capture

import com.nodoubt.core.model.ContentPart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureBusTest {
    @After fun resetCaptureBus() {
        CaptureBus.invalidateRequests()
        CaptureBus.status(CaptureStatus())
    }

    @Test fun `finishing authorization preserves projection for the next separate capture`() {
        val authorization = CaptureBus.beginRequest()
        CaptureBus.status(CaptureStatus(ready = true, requestId = authorization))
        assertTrue(CaptureBus.isCurrentRequest(authorization))
        assertTrue(CaptureBus.finishRequest(authorization))
        assertTrue(CaptureBus.status.value.ready)
        assertFalse(CaptureBus.status.value.capturing)
        assertFalse(CaptureBus.isCurrentRequest(authorization))

        val screenshot = CaptureBus.beginRequest()
        assertNotEquals(authorization, screenshot)
        assertTrue(CaptureBus.status.value.ready)
        assertTrue(CaptureBus.isCurrentRequest(screenshot))
    }

    @Test fun `late authorization readiness cannot restore a canceled or replaced request`() {
        val canceled = CaptureBus.beginRequest()
        CaptureBus.invalidateRequests()
        CaptureBus.status(CaptureStatus(ready = true, requestId = canceled))
        assertFalse(CaptureBus.status.value.ready)

        val newer = CaptureBus.beginRequest()
        CaptureBus.status(CaptureStatus(ready = true, requestId = canceled))
        assertFalse(CaptureBus.status.value.ready)
        assertEquals(newer, CaptureBus.status.value.requestId)
        CaptureBus.status(CaptureStatus(ready = true, requestId = newer))
        assertTrue(CaptureBus.status.value.ready)
    }

    @Test fun `closing answer rejects a result that was already delivered but not yet consumed`() = runTest {
        val result = async(UnconfinedTestDispatcher(testScheduler)) { CaptureBus.images.first() }
        val requestId = CaptureBus.beginRequest()
        CaptureBus.image(requestId, ContentPart.Image("pending"))
        val queued = result.await()
        CaptureBus.invalidateRequests()
        assertFalse(CaptureBus.finishRequest(queued.requestId))
    }

    @Test fun `rapid replacement accepts only the newest screenshot and consumes it once`() = runTest {
        val received = mutableListOf<CapturedFrame>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { CaptureBus.images.collect { received += it } }
        val oldRequest = CaptureBus.beginRequest()
        val newRequest = CaptureBus.beginRequest()
        CaptureBus.image(oldRequest, ContentPart.Image("old"))
        CaptureBus.image(newRequest, ContentPart.Image("new"))
        assertEquals(listOf(newRequest), received.map { it.requestId })
        assertTrue(CaptureBus.finishRequest(newRequest))
        assertFalse(CaptureBus.finishRequest(newRequest))
    }

    @Test fun `old screenshot failure cannot overwrite a new capture or reopen a closed answer`() = runTest {
        val oldRequest = CaptureBus.beginRequest()
        val newRequest = CaptureBus.beginRequest()
        CaptureBus.status(CaptureStatus(ready = true, capturing = true, requestId = newRequest))
        CaptureBus.status(CaptureStatus(error = "old failure", requestId = oldRequest))
        assertEquals(newRequest, CaptureBus.status.value.requestId)
        assertTrue(CaptureBus.status.value.capturing)
        assertNull(CaptureBus.status.value.error)
        CaptureBus.invalidateRequests()
        CaptureBus.status(CaptureStatus(error = "late failure", requestId = newRequest))
        assertFalse(CaptureBus.status.value.capturing)
        assertNull(CaptureBus.status.value.error)
    }
}
