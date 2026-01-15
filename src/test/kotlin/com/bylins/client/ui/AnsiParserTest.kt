package com.bylins.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnsiParserTest {

    private val parser = AnsiParser()

    @Test
    fun `parse plain text without ANSI codes`() {
        val result = parser.parse("Hello world")
        assertEquals("Hello world", result.text)
    }

    @Test
    fun `parse text with color code at start`() {
        val result = parser.parse("\u001B[32mGreen text\u001B[0m")
        assertEquals("Green text", result.text)
        // Check that style is applied
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parse text with color code without reset at end`() {
        // This was the bug - last segment wasn't styled
        val result = parser.parse("\u001B[32mGreen text")
        assertEquals("Green text", result.text)
        assertTrue(result.spanStyles.isNotEmpty(), "Style should be applied to text after color code")
    }

    @Test
    fun `parse multiple colors in sequence`() {
        val result = parser.parse("\u001B[31mRed\u001B[32mGreen\u001B[0m")
        assertEquals("RedGreen", result.text)
        // Should have styles for both red and green
        assertTrue(result.spanStyles.size >= 2)
    }

    @Test
    fun `parse bold text`() {
        val result = parser.parse("\u001B[1mBold\u001B[0m")
        assertEquals("Bold", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parse bright colors`() {
        val result = parser.parse("\u001B[91mBright Red\u001B[0m")
        assertEquals("Bright Red", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parse combined bold and color`() {
        val result = parser.parse("\u001B[1;32mBold Green\u001B[0m")
        assertEquals("Bold Green", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `stripAnsi removes all ANSI codes`() {
        val result = parser.stripAnsi("\u001B[1;32mBold Green\u001B[0m text")
        assertEquals("Bold Green text", result)
    }

    @Test
    fun `stripAnsi handles plain text`() {
        val result = parser.stripAnsi("Plain text")
        assertEquals("Plain text", result)
    }

    @Test
    fun `stripAnsi handles multiple codes`() {
        val result = parser.stripAnsi("\u001B[31mRed\u001B[0m and \u001B[32mGreen\u001B[0m")
        assertEquals("Red and Green", result)
    }

    @Test
    fun `parse empty string`() {
        val result = parser.parse("")
        assertEquals("", result.text)
    }

    @Test
    fun `parse only ANSI code without text`() {
        val result = parser.parse("\u001B[32m\u001B[0m")
        assertEquals("", result.text)
    }

    @Test
    fun `parse text between color changes`() {
        val result = parser.parse("Start \u001B[32mgreen\u001B[0m end")
        assertEquals("Start green end", result.text)
    }

    @Test
    fun `parse 256 color code`() {
        val result = parser.parse("\u001B[38;5;196mRed 256\u001B[0m")
        assertEquals("Red 256", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parse RGB color code`() {
        val result = parser.parse("\u001B[38;2;255;0;0mRGB Red\u001B[0m")
        assertEquals("RGB Red", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parse background color`() {
        val result = parser.parse("\u001B[41mRed BG\u001B[0m")
        assertEquals("Red BG", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parse underline`() {
        val result = parser.parse("\u001B[4mUnderlined\u001B[0m")
        assertEquals("Underlined", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parse incomplete escape sequence`() {
        // Should not crash on incomplete sequences
        val result = parser.parse("\u001B[text")
        assertTrue(result.text.contains("text"))
    }
}
