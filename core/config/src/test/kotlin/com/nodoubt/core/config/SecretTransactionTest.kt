package com.nodoubt.core.config

import org.junit.Assert.*
import org.junit.Test

class SecretTransactionTest {
    @Test fun failedProfileWriteRollsBackUpdatedAndNewCredentials() {
        val storage = mutableMapOf("existing" to "old-ciphertext")
        val result = runCatching { SecretTransaction.update(
            changes = mapOf("existing" to "new-ciphertext", "new" to "added-ciphertext"),
            read = { storage[it] },
            write = { values -> values.forEach { (key, value) -> if (value == null) storage.remove(key) else storage[key] = value } }
        ) { error("Disk full") } }
        assertEquals("Disk full", result.exceptionOrNull()!!.message)
        assertEquals(mapOf("existing" to "old-ciphertext"), storage)
    }

    @Test fun successCommitsTheNewCredentialAndReturnsTheActionResult() {
        val storage = mutableMapOf<String, String>()
        val result = SecretTransaction.update(mapOf("new" to "ciphertext"), { storage[it] }, { values ->
            values.forEach { (key, value) -> if (value == null) storage.remove(key) else storage[key] = value }
        }) { "saved" }
        assertEquals("saved", result)
        assertEquals("ciphertext", storage["new"])
    }
}
