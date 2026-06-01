package com.bylins.client.ui.scroll

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutputScrollControllerTest {

    private fun geom(firstSeq: Long, lineCount: Int) = BufferGeometry(firstSeq, lineCount)

    @Test
    fun `follows by default and returns Bottom on new content`() {
        val c = OutputScrollController()
        assertTrue(c.followMode)
        assertFalse(c.isSplit)
        assertEquals(ScrollTarget.Bottom, c.onContentChanged(geom(0, 100)))
    }

    @Test
    fun `scrolling up splits and anchors`() {
        val c = OutputScrollController()
        c.onUserScroll(atBottom = false, visibleTopSeq = 42)

        assertTrue(c.isSplit)
        assertFalse(c.followMode)
        assertEquals(42L, c.anchorSeq)
    }

    @Test
    fun `append while anchored keeps the same anchor`() {
        val c = OutputScrollController()
        c.onUserScroll(atBottom = false, visibleTopSeq = 100)

        assertEquals(ScrollTarget.ToLine(100), c.onContentChanged(geom(0, 200)))
        // буфер вырос снизу — позиция не меняется
        assertEquals(ScrollTarget.ToLine(100), c.onContentChanged(geom(0, 250)))
    }

    @Test
    fun `eviction past anchor clamps to buffer start`() {
        val c = OutputScrollController()
        c.onUserScroll(atBottom = false, visibleTopSeq = 100)

        val target = c.onContentChanged(geom(150, 50)) // firstSeq=150 > anchor 100
        assertEquals(ScrollTarget.ToLine(150), target)
        assertEquals(150L, c.anchorSeq) // якорь подтянулся к началу
    }

    @Test
    fun `scrolling back to bottom collapses and follows`() {
        val c = OutputScrollController()
        c.onUserScroll(atBottom = false, visibleTopSeq = 100)
        c.onUserScroll(atBottom = true, visibleTopSeq = 0)

        assertFalse(c.isSplit)
        assertTrue(c.followMode)
        assertEquals(ScrollTarget.Bottom, c.onContentChanged(geom(0, 300)))
    }

    @Test
    fun `empty buffer returns None`() {
        val c = OutputScrollController()
        c.onUserScroll(atBottom = false, visibleTopSeq = 5)
        assertEquals(ScrollTarget.None, c.onContentChanged(geom(0, 0)))
    }

    @Test
    fun `anchor below buffer falls back to Bottom`() {
        val c = OutputScrollController()
        c.onUserScroll(atBottom = false, visibleTopSeq = 500)
        assertEquals(ScrollTarget.Bottom, c.onContentChanged(geom(0, 100)))
    }

    @Test
    fun `jumpToLine anchors and splits`() {
        val c = OutputScrollController()
        c.jumpToLine(77)

        assertTrue(c.isSplit)
        assertEquals(77L, c.anchorSeq)
        assertEquals(ScrollTarget.ToLine(77), c.onContentChanged(geom(0, 200)))
    }

    @Test
    fun `jumpToBottom collapses and follows`() {
        val c = OutputScrollController()
        c.jumpToLine(77)
        c.jumpToBottom()

        assertFalse(c.isSplit)
        assertEquals(ScrollTarget.Bottom, c.onContentChanged(geom(0, 200)))
    }
}
