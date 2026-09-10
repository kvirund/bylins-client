package com.bylins.client.ui

import com.bylins.client.OperatingSystem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Копирование, выделение и поиск в панели вывода были прибиты к Ctrl. На macOS
 * эти сочетания живут на Cmd, так что мак оставался с половиной набора: Cmd+C
 * починили отдельно, а Cmd+A и Cmd+F не работали.
 */
class CommandModifierTest {

    @Test
    fun `на macOS команда — это и Ctrl, и Cmd`() {
        assertTrue(CommandModifier.isPressed(isCtrlPressed = false, isMetaPressed = true, os = OperatingSystem.MacOS))
        assertTrue(CommandModifier.isPressed(isCtrlPressed = true, isMetaPressed = false, os = OperatingSystem.MacOS))
    }

    @Test
    fun `на прочих системах Meta командой не считается`() {
        // Там Meta — это Win или Super, и на неё завязана сама оболочка
        listOf(OperatingSystem.Windows, OperatingSystem.Linux, OperatingSystem.Other).forEach { os ->
            assertFalse(CommandModifier.isPressed(isCtrlPressed = false, isMetaPressed = true, os = os), os.name)
            assertTrue(CommandModifier.isPressed(isCtrlPressed = true, isMetaPressed = false, os = os), os.name)
        }
    }

    @Test
    fun `без модификаторов команды нет`() {
        assertFalse(CommandModifier.isPressed(isCtrlPressed = false, isMetaPressed = false, os = OperatingSystem.MacOS))
    }

    @Test
    fun `поиск открывается тем же модификатором`() {
        val cmd = CommandModifier.isPressed(isCtrlPressed = false, isMetaPressed = true, os = OperatingSystem.MacOS)

        assertTrue(
            OutputSearchShortcut.isOpen(
                androidx.compose.ui.input.key.Key.F,
                isCommandPressed = cmd,
                isAltPressed = false,
                isShiftPressed = false
            ),
            "Cmd+F не открывает поиск"
        )
    }
}
