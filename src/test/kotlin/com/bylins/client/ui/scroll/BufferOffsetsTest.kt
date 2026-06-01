package com.bylins.client.ui.scroll

import kotlin.test.Test
import kotlin.test.assertEquals

class BufferOffsetsTest {

    private val text = "abc\ndef\nghi" // строки: 0=abc(0..2), 1=def(4..6), 2=ghi(8..10)

    @Test
    fun `lineStartOffset of first line is zero`() {
        assertEquals(0, BufferOffsets.lineStartOffset(text, 0))
        assertEquals(0, BufferOffsets.lineStartOffset(text, -3))
    }

    @Test
    fun `lineStartOffset counts preceding newlines`() {
        assertEquals(4, BufferOffsets.lineStartOffset(text, 1))
        assertEquals(8, BufferOffsets.lineStartOffset(text, 2))
    }

    @Test
    fun `lineStartOffset beyond last line clamps to end`() {
        assertEquals(text.length, BufferOffsets.lineStartOffset(text, 5))
    }

    @Test
    fun `lineIndexOfOffset maps offset to its line`() {
        assertEquals(0, BufferOffsets.lineIndexOfOffset(text, 0))
        assertEquals(0, BufferOffsets.lineIndexOfOffset(text, 2))
        assertEquals(1, BufferOffsets.lineIndexOfOffset(text, 4))
        assertEquals(2, BufferOffsets.lineIndexOfOffset(text, 10))
    }

    @Test
    fun `lineColOfOffset returns line and column`() {
        assertEquals(0 to 0, BufferOffsets.lineColOfOffset(text, 0))
        assertEquals(1 to 1, BufferOffsets.lineColOfOffset(text, 5))
        assertEquals(2 to 0, BufferOffsets.lineColOfOffset(text, 8))
    }

    @Test
    fun `offsetOfLineCol is inverse of lineColOfOffset`() {
        for (off in 0..text.length) {
            val (line, col) = BufferOffsets.lineColOfOffset(text, off)
            assertEquals(off, BufferOffsets.offsetOfLineCol(text, line, col), "round-trip at $off")
        }
    }

    @Test
    fun `offsetOfLineCol clamps column to line length`() {
        assertEquals(3, BufferOffsets.offsetOfLineCol(text, 0, 100)) // конец "abc"
    }

    @Test
    fun `empty text`() {
        assertEquals(0, BufferOffsets.lineStartOffset("", 0))
        assertEquals(0, BufferOffsets.lineIndexOfOffset("", 0))
        assertEquals(0 to 0, BufferOffsets.lineColOfOffset("", 0))
        assertEquals(0, ContentSnapshot.countLines(""))
    }

    @Test
    fun `single line without newline`() {
        assertEquals(0, BufferOffsets.lineStartOffset("hello", 0))
        assertEquals("hello".length, BufferOffsets.lineStartOffset("hello", 1))
        assertEquals(0 to 3, BufferOffsets.lineColOfOffset("hello", 3))
        assertEquals(1, ContentSnapshot.countLines("hello"))
    }

    @Test
    fun `trailing newline creates an empty last line`() {
        val t = "a\n"
        assertEquals(2, ContentSnapshot.countLines(t))
        assertEquals(2, BufferOffsets.lineStartOffset(t, 1))
        assertEquals(1 to 0, BufferOffsets.lineColOfOffset(t, 2))
        assertEquals(2, BufferOffsets.offsetOfLineCol(t, 1, 0))
    }
}
