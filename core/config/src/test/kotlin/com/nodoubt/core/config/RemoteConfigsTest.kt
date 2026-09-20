package com.nodoubt.core.config

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigsTest {
    @Test fun unsupportedSchemesAndEmbeddedCredentialsAreRejectedBeforeNetworking() = runBlocking {
        val remote = RemoteConfigs()
        listOf("file:///tmp/models.json", "ftp://example.com/models.json", "not a url",
            "https://user:password@example.com/models.json").forEach { url ->
            val failure = runCatching { remote.fetch(url) }.exceptionOrNull()
            assertTrue("Expected invalid URL failure", failure is IllegalArgumentException)
            assertTrue(failure!!.message!!.contains("HTTP/HTTPS"))
        }
    }
}
