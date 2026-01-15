package com.bylins.client.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class TabTest {

    @Test
    fun `tab starts with empty content`() {
        val tab = Tab(id = "test", name = "Test")
        assertEquals("", tab.content.value)
    }

    @Test
    fun `appendText adds text to tab`() {
        val tab = Tab(id = "test", name = "Test")
        tab.appendText("Hello")
        tab.flush()

        assertEquals("Hello", tab.content.value)
    }

    @Test
    fun `appendText handles multiple lines`() {
        val tab = Tab(id = "test", name = "Test")
        tab.appendText("Line1\nLine2")
        tab.flush()

        assertTrue(tab.content.value.contains("Line1"))
        assertTrue(tab.content.value.contains("Line2"))
    }

    @Test
    fun `flush updates content immediately`() {
        val tab = Tab(id = "test", name = "Test")
        tab.appendText("First")
        tab.flush()

        assertEquals("First", tab.content.value)

        tab.appendText("Second")
        tab.flush()

        assertTrue(tab.content.value.contains("Second"))
    }

    @Test
    fun `clear removes all content`() {
        val tab = Tab(id = "test", name = "Test")
        tab.appendText("Some content")
        tab.flush()
        tab.clear()

        assertEquals("", tab.content.value)
    }

    @Test
    fun `tab respects maxLines limit`() {
        val tab = Tab(id = "test", name = "Test", maxLines = 5)

        for (i in 1..10) {
            tab.appendText("Line $i")
        }
        tab.flush()

        val lines = tab.content.value.lines()
        assertTrue(lines.size <= 5)
    }

    @Test
    fun `captureAndTransform returns null for non-matching line`() {
        val tab = Tab(
            id = "test",
            name = "Test",
            filters = listOf(TabFilter("pattern".toRegex()))
        )

        val result = tab.captureAndTransform("no match here", "no match here")
        assertNull(result)
    }

    @Test
    fun `captureAndTransform returns line for matching pattern`() {
        val tab = Tab(
            id = "test",
            name = "Test",
            filters = listOf(TabFilter("hello".toRegex()))
        )

        val result = tab.captureAndTransform("hello world", "hello world")
        assertEquals("hello world", result)
    }

    @Test
    fun `captureAndTransform applies replacement`() {
        val tab = Tab(
            id = "test",
            name = "Test",
            filters = listOf(TabFilter("(\\w+) says: (.+)".toRegex(), replacement = "[$1] $2"))
        )

        val result = tab.captureAndTransform("John says: hello", "John says: hello")
        assertEquals("[John] hello", result)
    }

    @Test
    fun `captureAndTransform matches with colors when enabled`() {
        val tab = Tab(
            id = "test",
            name = "Test",
            filters = listOf(TabFilter("\u001B\\[32m".toRegex(), matchWithColors = true))
        )

        val cleanLine = "Green text"
        val rawLine = "\u001B[32mGreen text\u001B[0m"

        val result = tab.captureAndTransform(cleanLine, rawLine)
        assertEquals(rawLine, result)
    }

    @Test
    fun `captureAndTransform does not match colors when disabled`() {
        val tab = Tab(
            id = "test",
            name = "Test",
            filters = listOf(TabFilter("\u001B\\[32m".toRegex(), matchWithColors = false))
        )

        val cleanLine = "Green text"
        val rawLine = "\u001B[32mGreen text\u001B[0m"

        val result = tab.captureAndTransform(cleanLine, rawLine)
        assertNull(result) // Should not match because matchWithColors is false
    }

    @Test
    fun `consecutive empty lines are skipped`() {
        val tab = Tab(id = "test", name = "Test")
        tab.appendText("Line1\n\n\n\nLine2")
        tab.flush()

        val content = tab.content.value
        // Should not have multiple consecutive empty lines
        assertTrue(!content.contains("\n\n\n"))
    }
}

class TabDtoTest {

    @Test
    fun `TabDto toTab creates correct Tab`() {
        val dto = TabDto(
            id = "test",
            name = "Test Tab",
            captureMode = "COPY",
            maxLines = 500
        )

        val tab = dto.toTab()

        assertEquals("test", tab.id)
        assertEquals("Test Tab", tab.name)
        assertEquals(CaptureMode.COPY, tab.captureMode)
        assertEquals(500, tab.maxLines)
    }

    @Test
    fun `TabDto toTab restores content`() {
        val dto = TabDto(
            id = "test",
            name = "Test",
            content = "Saved content"
        )

        val tab = dto.toTab()

        assertEquals("Saved content", tab.content.value)
    }

    @Test
    fun `TabDto fromTab preserves all fields`() {
        val tab = Tab(
            id = "test",
            name = "Test Tab",
            captureMode = CaptureMode.MOVE,
            maxLines = 300
        )
        tab.appendText("Some content")
        tab.flush()

        val dto = TabDto.fromTab(tab)

        assertEquals("test", dto.id)
        assertEquals("Test Tab", dto.name)
        assertEquals("MOVE", dto.captureMode)
        assertEquals(300, dto.maxLines)
        assertEquals("Some content", dto.content)
    }

    @Test
    fun `TabDto handles ONLY captureMode gracefully`() {
        // ONLY was removed, should default to COPY
        val dto = TabDto(
            id = "test",
            name = "Test",
            captureMode = "ONLY"
        )

        val tab = dto.toTab()
        assertEquals(CaptureMode.COPY, tab.captureMode)
    }
}

class TabFilterTest {

    @Test
    fun `transform returns null for non-matching text`() {
        val filter = TabFilter("hello".toRegex())
        val result = filter.transform("goodbye", "goodbye")
        assertNull(result)
    }

    @Test
    fun `transform returns rawLine for matching text without replacement`() {
        val filter = TabFilter("hello".toRegex())
        val result = filter.transform("hello world", "\u001B[32mhello world\u001B[0m")
        assertEquals("\u001B[32mhello world\u001B[0m", result)
    }

    @Test
    fun `transform applies replacement with capture groups`() {
        val filter = TabFilter("(\\d+) items".toRegex(), replacement = "Count: $1")
        val result = filter.transform("You have 42 items", "You have 42 items")
        assertEquals("Count: 42", result)
    }

    @Test
    fun `transform uses rawLine when matchWithColors is true`() {
        val filter = TabFilter("\u001B".toRegex(), matchWithColors = true)
        val result = filter.transform("clean", "\u001B[32mcolored\u001B[0m")
        assertEquals("\u001B[32mcolored\u001B[0m", result)
    }

    @Test
    fun `transform uses cleanLine when matchWithColors is false`() {
        val filter = TabFilter("colored".toRegex(), matchWithColors = false)
        val result = filter.transform("colored", "\u001B[32mcolored\u001B[0m")
        assertEquals("\u001B[32mcolored\u001B[0m", result)
    }
}
