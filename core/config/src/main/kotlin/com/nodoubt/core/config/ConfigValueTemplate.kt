package com.nodoubt.core.config

/** pi-compatible $NAME / ${NAME} templates; commands are deliberately unsupported. */
internal object ConfigValueTemplate {
    private val variable = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun references(value: String): Set<String> {
        val names = linkedSetOf<String>()
        expand(value) { names += it; "" }
        return names
    }

    fun expand(value: String, lookup: (String) -> String): String {
        val result = StringBuilder()
        var index = 0
        while (index < value.length) {
            if (value[index] != '$' || index + 1 == value.length) {
                result.append(value[index++]); continue
            }
            val next = value[index + 1]
            if (next == '$' || next == '!') {
                result.append(next); index += 2; continue
            }
            if (next == '{') {
                val end = value.indexOf('}', index + 2)
                val name = if (end >= 0) value.substring(index + 2, end) else ""
                if (variable.matches(name)) {
                    result.append(lookup(name)); index = end + 1; continue
                }
            } else {
                val match = variable.find(value, index + 1)?.takeIf { it.range.first == index + 1 }
                if (match != null) {
                    result.append(lookup(match.value)); index = match.range.last + 1; continue
                }
            }
            result.append('$'); index++
        }
        return result.toString()
    }
}
