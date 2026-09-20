package com.nodoubt.core.config

/** Rolls credentials back if the atomic profile write fails. Values may already be ciphertext. */
internal object SecretTransaction {
    fun <T> update(
        changes: Map<String, String?>,
        read: (String) -> String?,
        write: (Map<String, String?>) -> Unit,
        action: () -> T
    ): T {
        val previous = changes.keys.associateWith(read)
        try {
            write(changes)
            return action()
        } catch (failure: Exception) {
            runCatching { write(previous) }.onFailure(failure::addSuppressed)
            throw failure
        }
    }
}
