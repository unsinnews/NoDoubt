package com.nodoubt.feature.channels

import com.nodoubt.core.model.ModelRef
import com.nodoubt.core.model.UsageBinding
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageOrderTest {
    private val a = ModelRef("c", "a")
    private val b = ModelRef("c", "b")

    @Test fun legacySelectedModelIsShownFirst() {
        assertEquals(listOf(b, a), UsageBinding(listOf(a, b), b).priority())
    }

    @Test fun firstCandidateBecomesTheDefault() {
        val binding = orderedBinding(listOf(b, a, b))
        assertEquals(listOf(b, a), binding.entries)
        assertEquals(b, binding.selected)
        assertEquals(UsageBinding(), orderedBinding(emptyList()))
    }
}
