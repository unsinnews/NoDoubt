package com.nodoubt.core.model

/** Recognizes request controls without interpreting business fields inside response schemas. */
object ThinkingParameterPaths {
    fun isThinking(path: String): Boolean = isThinking(path.split('.'))

    fun isThinking(rawPath: List<String>): Boolean {
        val path = normalize(rawPath)
        val first = path.firstOrNull() ?: return false
        fun control(name: String) = name.contains("thinking") || name.startsWith("reasoning") || name == "includethoughts"
        if (control(first)) return true
        val second = path.getOrNull(1) ?: return false
        return (first == "outputconfig" && second == "effort") ||
            (first in setOf("generationconfig", "chattemplatekwargs", "chattemplateargs") && control(second))
    }

    /** Matches the same model control across dotted keys, case variants and extraBody. */
    fun matchesField(candidate: String, field: String): Boolean {
        val expected = normalize(field.split('.'))
        val actual = normalize(candidate.split('.'))
        return expected.isNotEmpty() && isThinking(expected) && actual.take(expected.size) == expected
    }

    private fun normalize(rawPath: List<String>): List<String> = rawPath.flatMap { it.split('.') }
        .map { it.replace("_", "").lowercase() }
        .dropWhile { it == "extrabody" }
}
