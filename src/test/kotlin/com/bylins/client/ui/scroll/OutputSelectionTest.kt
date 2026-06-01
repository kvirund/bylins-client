package com.bylins.client.ui.scroll

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutputSelectionTest {

    private val text = "abc\ndef\nghi" // seq 0..2 при firstSeq=0

    @Test
    fun `fresh selection is empty`() {
        val s = OutputSelection()
        assertTrue(s.isEmpty)
        assertNull(s.charRange(0, text))
        assertEquals("", s.copyText(0, text))
    }

    @Test
    fun `single click without drag selects nothing`() {
        val s = OutputSelection()
        s.start(SelPoint(1, 1))
        assertTrue(s.isEmpty)
        assertNull(s.charRange(0, text))
    }

    @Test
    fun `selection is normalized regardless of drag direction`() {
        val s = OutputSelection()
        s.start(SelPoint(2, 1))
        s.extendTo(SelPoint(0, 0)) // тянем вверх
        assertEquals("abc\ndef\ng", s.copyText(0, text))
    }

    @Test
    fun `charRange spans multiple lines`() {
        val s = OutputSelection()
        s.start(SelPoint(0, 1))
        s.extendTo(SelPoint(1, 2))
        assertEquals(1 until 6, s.charRange(0, text))
        assertEquals("bc\nde", s.copyText(0, text))
    }

    @Test
    fun `selection start clamps to buffer start after eviction`() {
        val s = OutputSelection()
        s.start(SelPoint(0, 1))   // строка, которая позже будет вытеснена
        s.extendTo(SelPoint(2, 2))
        // firstSeq=2 → строки 0,1 вытеснены, в буфере только "ghi"
        assertEquals("gh", s.copyText(2, "ghi"))
    }

    @Test
    fun `selectAll covers whole buffer`() {
        val s = OutputSelection()
        s.selectAll(firstSeq = 0, lineCount = 3)
        assertEquals(text, s.copyText(0, text))
    }

    @Test
    fun `selectAll on empty buffer clears selection`() {
        val s = OutputSelection()
        s.selectAll(firstSeq = 0, lineCount = 0)
        assertTrue(s.isEmpty)
    }

    @Test
    fun `clear resets selection`() {
        val s = OutputSelection()
        s.start(SelPoint(0, 0))
        s.extendTo(SelPoint(2, 2))
        s.clear()
        assertTrue(s.isEmpty)
        assertNull(s.charRange(0, text))
    }
}
