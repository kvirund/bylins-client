package com.bylins.client.aicontrol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutputJournalTest {

    private fun journal(capacity: Int = 100) = OutputJournal(capacity)

    @Test
    fun `append assigns monotonic seq starting from 1`() {
        val j = journal()
        assertEquals(1L, j.append("a", 0))
        assertEquals(2L, j.append("b", 0))
        assertEquals(3L, j.headSeq)
    }

    @Test
    fun `read returns lines since cursor and advances it`() {
        val j = journal()
        j.append("one", 0); j.append("two", 0); j.append("three", 0)

        val first = j.read(since = 1)
        assertEquals(listOf("one", "two", "three"), first.lines.map { it.text })
        assertEquals(4L, first.nextSeq)
        assertEquals(0L, first.missed)

        // С нового курсора новых строк нет
        val empty = j.read(since = first.nextSeq)
        assertTrue(empty.lines.isEmpty())
        assertEquals(4L, empty.nextSeq)
    }

    @Test
    fun `read respects limit`() {
        val j = journal()
        repeat(10) { j.append("line$it", 0) }
        val page = j.read(since = 1, limit = 4)
        assertEquals(4, page.lines.size)
        assertEquals(5L, page.nextSeq)
    }

    @Test
    fun `eviction keeps seq monotonic and reports missed lines`() {
        val j = journal(capacity = 3)
        repeat(5) { j.append("line$it", 0) }  // seq 1..5, в буфере 3..5

        assertEquals(3L, j.tailSeq)
        assertEquals(6L, j.headSeq)

        // Агент просит с 1, но 1 и 2 уже вытеснены
        val result = j.read(since = 1)
        assertEquals(2L, result.missed)
        assertEquals(listOf("line2", "line3", "line4"), result.lines.map { it.text })
        assertEquals(6L, result.nextSeq)
    }

    @Test
    fun `read from future seq yields nothing and keeps cursor`() {
        val j = journal()
        j.append("a", 0)
        val result = j.read(since = 99)
        assertTrue(result.lines.isEmpty())
        assertEquals(99L, result.nextSeq)
    }

    @Test
    fun `empty journal reads cleanly`() {
        val j = journal()
        val result = j.read(since = 1)
        assertTrue(result.lines.isEmpty())
        assertEquals(0L, result.missed)
        assertEquals(1L, result.nextSeq)
    }

    @Test
    fun `clear drops lines but keeps numbering`() {
        val j = journal()
        j.append("a", 0); j.append("b", 0)
        j.clear()
        assertEquals(0, j.size)
        assertEquals(3L, j.append("c", 0))  // нумерация продолжается
    }
}
