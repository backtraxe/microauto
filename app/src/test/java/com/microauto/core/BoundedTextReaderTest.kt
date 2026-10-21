package com.microauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class BoundedTextReaderTest {

    @Test
    fun readsTextWithinLimit() {
        assertEquals("hello 世界", BoundedTextReader.read("hello 世界".byteInputStream()))
    }

    @Test
    fun rejectsTextOverByteLimit() {
        val data = ByteArray(BoundedTextReader.MAX_BYTES + 1) { 'a'.code.toByte() }
        val error = assertThrows(IllegalArgumentException::class.java) {
            BoundedTextReader.read(ByteArrayInputStream(data))
        }
        assertTrue(error.message?.contains("128 KB") == true)
    }

    @Test
    fun rejectsNulBytes() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BoundedTextReader.read(byteArrayOf('a'.code.toByte(), 0, 'b'.code.toByte()).inputStream())
        }
        assertEquals("不是文本文件", error.message)
    }

    @Test
    fun stopsReadingShortlyAfterByteLimit() {
        val stream = EndlessStream()
        assertThrows(IllegalArgumentException::class.java) {
            BoundedTextReader.read(stream)
        }
        assertTrue(stream.bytesRead <= BoundedTextReader.MAX_BYTES + 8 * 1024L)
        assertTrue(stream.bytesRead < BoundedTextReader.MAX_BYTES * 2L)
    }

    @Test
    fun acceptsExactlyByteLimit() {
        val data = ByteArray(BoundedTextReader.MAX_BYTES) { 'a'.code.toByte() }
        val text = BoundedTextReader.read(ByteArrayInputStream(data))
        assertEquals(BoundedTextReader.MAX_BYTES, text.length)
    }

    private class EndlessStream : InputStream() {
        var bytesRead = 0L

        override fun read(): Int {
            bytesRead++
            return 'a'.code
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            b.fill('a'.code.toByte(), off, off + len)
            bytesRead += len
            return len
        }
    }
}
