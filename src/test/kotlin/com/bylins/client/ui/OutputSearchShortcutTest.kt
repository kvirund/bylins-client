package com.bylins.client.ui

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Поиск по выводу должен занимать одно сочетание, а не все с Ctrl+F:
 * Ctrl+Shift+F и Ctrl+Alt+F перехватывались окном и не доходили до хоткеев.
 */
class OutputSearchShortcutTest {

    private fun open(key: Key, ctrl: Boolean = true, alt: Boolean = false, shift: Boolean = false) =
        OutputSearchShortcut.isOpen(key, ctrl, alt, shift)

    @Test
    fun `Ctrl+F открывает поиск`() {
        assertTrue(open(Key.F))
    }

    @Test
    fun `сочетания с Shift и Alt остаются свободны`() {
        assertFalse(open(Key.F, shift = true), "Ctrl+Shift+F занят поиском")
        assertFalse(open(Key.F, alt = true), "Ctrl+Alt+F занят поиском")
        assertFalse(open(Key.F, alt = true, shift = true))
    }

    @Test
    fun `без Ctrl и на другой клавише не срабатывает`() {
        assertFalse(open(Key.F, ctrl = false))
        assertFalse(open(Key.G))
    }
}
