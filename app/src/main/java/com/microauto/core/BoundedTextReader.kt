package com.microauto.core

import java.io.ByteArrayOutputStream
import java.io.InputStream

object BoundedTextReader {
    const val MAX_BYTES = ScriptLimits.MAX_SCRIPT_BYTES
    private const val BUFFER_SIZE = 8 * 1024

    fun read(input: InputStream, maxBytes: Int = MAX_BYTES): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) {
                val single = input.read()
                if (single < 0) break
                total++
                require(total <= maxBytes) { tooLargeMessage(maxBytes) }
                output.write(single)
                continue
            }
            total += read
            require(total <= maxBytes) { tooLargeMessage(maxBytes) }
            output.write(buffer, 0, read)
        }
        val text = output.toString(Charsets.UTF_8.name())
        require(!text.contains('\u0000')) { "不是文本文件" }
        return text
    }

    private fun tooLargeMessage(maxBytes: Int): String =
        "文件超过 ${(maxBytes + 1023) / 1024} KB"
}
