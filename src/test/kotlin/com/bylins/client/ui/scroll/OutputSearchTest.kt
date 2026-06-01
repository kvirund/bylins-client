package com.bylins.client.ui.scroll

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class OutputSearchTest {

    private val text = "foo bar Foo baz foo"

    @Test
    fun `plain case-insensitive finds all`() {
        val m = OutputSearch.findMatches(text, "foo", caseSensitive = false, useRegex = false)
        assertEquals(listOf(SearchMatch(0, 3), SearchMatch(8, 11), SearchMatch(16, 19)), m)
    }

    @Test
    fun `plain case-sensitive respects case`() {
        val m = OutputSearch.findMatches(text, "Foo", caseSensitive = true, useRegex = false)
        assertEquals(listOf(SearchMatch(8, 11)), m)
    }

    @Test
    fun `empty query or text yields nothing`() {
        assertTrue(OutputSearch.findMatches(text, "", false, false).isEmpty())
        assertTrue(OutputSearch.findMatches("", "foo", false, false).isEmpty())
    }

    @Test
    fun `no match yields nothing`() {
        assertTrue(OutputSearch.findMatches(text, "zzz", false, false).isEmpty())
    }

    @Test
    fun `non-overlapping matches`() {
        val m = OutputSearch.findMatches("aaaa", "aa", false, false)
        assertEquals(listOf(SearchMatch(0, 2), SearchMatch(2, 4)), m)
    }

    @Test
    fun `regex matches`() {
        val m = OutputSearch.findMatches("a1 b22 c333", "\\d+", false, true)
        assertEquals(listOf(SearchMatch(1, 2), SearchMatch(4, 6), SearchMatch(8, 11)), m)
    }

    @Test
    fun `invalid regex yields nothing and no crash`() {
        assertTrue(OutputSearch.findMatches(text, "(unclosed", false, true).isEmpty())
    }

    @Test
    fun `update sets matches and selects first`() {
        val s = OutputSearch()
        s.update("foo", text)
        assertEquals(3, s.count)
        assertEquals(0, s.currentIndex)
        assertEquals(SearchMatch(0, 3), s.current)
        assertTrue(s.isActive)
    }

    @Test
    fun `next and prev wrap around`() {
        val s = OutputSearch()
        s.update("foo", text)
        s.next(); assertEquals(1, s.currentIndex)
        s.next(); assertEquals(2, s.currentIndex)
        s.next(); assertEquals(0, s.currentIndex) // wrap
        s.prev(); assertEquals(2, s.currentIndex) // wrap back
    }

    @Test
    fun `update keeps index when query unchanged`() {
        val s = OutputSearch()
        s.update("foo", text)
        s.next() // index 1
        s.update("foo", text + " foo") // same query, more text
        assertEquals(1, s.currentIndex)
        assertEquals(4, s.count)
    }

    @Test
    fun `update resets index when query changes`() {
        val s = OutputSearch()
        s.update("foo", text)
        s.next()
        s.update("bar", text)
        assertEquals(0, s.currentIndex)
        assertEquals(1, s.count)
    }

    @Test
    fun `no matches gives index -1 and null current`() {
        val s = OutputSearch()
        s.update("zzz", text)
        assertEquals(-1, s.currentIndex)
        assertNull(s.current)
    }

    @Test
    fun `regex error flag set on invalid pattern`() {
        val s = OutputSearch()
        s.useRegex = true
        s.update("(bad", text)
        assertTrue(s.regexError)
        assertEquals(0, s.count)
    }

    @Test
    fun `clear resets state`() {
        val s = OutputSearch()
        s.update("foo", text)
        s.clear()
        assertEquals("", s.query)
        assertEquals(0, s.count)
        assertEquals(-1, s.currentIndex)
        assertFalse(s.isActive)
    }
}
