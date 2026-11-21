package com.nodoubt.core.designsystem

/** Markwon 4 uses double dollars for inline math as well as display blocks. */
internal fun normalizeMathDelimiters(markdown: String): String {
    var fenceCharacter: Char? = null
    var fenceLength = 0
    val fencePattern = Regex("^ {0,3}(`{3,}|~{3,})")
    return markdown.split('\n').joinToString("\n") { line ->
        val marker = fencePattern.find(line)?.groupValues?.get(1)
        if (fenceCharacter != null) {
            if (marker != null && marker.first() == fenceCharacter && marker.length >= fenceLength &&
                line.trim().removePrefix(marker).isBlank()) fenceCharacter = null
            line
        } else if (marker != null) {
            fenceCharacter = marker.first()
            fenceLength = marker.length
            line
        } else normalizeInlineMath(line)
    }
}

private fun normalizeInlineMath(line: String): String = buildString {
    var codeDelimiter = 0
    var index = 0
    while (index < line.length) {
        val current = line[index]
        if (current == '`' && !line.isEscaped(index)) {
            var end = index
            while (end < line.length && line[end] == '`') end++
            val length = end - index
            if (codeDelimiter == 0) codeDelimiter = length else if (codeDelimiter == length) codeDelimiter = 0
            append(line, index, end)
            index = end
        } else if (codeDelimiter == 0 && current == '\\' && !line.isEscaped(index) &&
            index + 1 < line.length && line[index + 1] in "[]()") {
            append("$$")
            index += 2
        } else if (codeDelimiter == 0 && current == '$' && !line.isEscaped(index)) {
            if (line.getOrNull(index + 1) == '$') {
                append("$$")
                index += 2
            } else {
                val end = line.inlineMathEnd(index)
                if (end == null) {
                    append(current)
                    index++
                } else {
                    append("$$").append(line, index + 1, end).append("$$")
                    index = end + 1
                }
            }
        } else {
            append(current)
            index++
        }
    }
}

private fun String.inlineMathEnd(start: Int): Int? {
    if (getOrNull(start + 1)?.isWhitespace() != false) return null
    var end = start + 1
    while (end < length) {
        if (this[end] == '`' && !isEscaped(end)) return null
        if (this[end] == '$' && !isEscaped(end)) {
            // A closing delimiter may not consume the start of another currency amount.
            if (end == start + 1 || this[end - 1].isWhitespace() ||
                getOrNull(end + 1)?.let { it == '$' || it.isDigit() } == true) return null
            return end
        }
        end++
    }
    return null
}

private fun String.isEscaped(index: Int): Boolean {
    var backslashes = 0
    var cursor = index - 1
    while (cursor >= 0 && this[cursor] == '\\') { backslashes++; cursor-- }
    return backslashes % 2 == 1
}

/**
 * While an answer streams, cut a trailing formula whose closing `$$` has not arrived yet (input is
 * already normalized), so the reader never sees raw LaTeX that turns into a formula a moment later.
 */
internal fun hideOpenMath(markdown: String): String {
    var count = 0
    var last = -1
    var index = markdown.indexOf("$$")
    while (index >= 0) {
        if (!markdown.isEscaped(index)) { count++; last = index }
        index = markdown.indexOf("$$", index + 2)
    }
    if (count % 2 == 1) return markdown.substring(0, last).trimEnd()
    // A single-dollar formula still open on the last line has not been normalized yet; hide it too.
    val lineStart = markdown.lastIndexOf('\n') + 1
    val singles = (lineStart until markdown.length).filter { i ->
        markdown[i] == '$' && !markdown.isEscaped(i) && markdown.getOrNull(i - 1) != '$' &&
            markdown.getOrNull(i + 1).let { it != null && it != '$' && !it.isWhitespace() && !it.isDigit() }
    }
    return if (singles.size % 2 == 1) markdown.substring(0, singles.last()).trimEnd() else markdown
}
