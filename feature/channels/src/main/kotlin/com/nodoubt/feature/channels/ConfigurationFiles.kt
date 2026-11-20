package com.nodoubt.feature.channels

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal fun readConfiguration(input: InputStream): String {
    val maxBytes = 2 * 1024 * 1024
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size() + count <= maxBytes) { "文件超过 2 MB" }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
}
