package com.bylins.client.ui

import androidx.compose.ui.input.key.Key

/**
 * Сочетание, открывающее поиск по выводу.
 *
 * Проверялся только Ctrl, поэтому поиск занимал заодно Ctrl+Shift+F и
 * Ctrl+Alt+F: назначить на них свой хоткей было нельзя — окно перехватывало
 * нажатие раньше, чем оно доходило до разбора хоткеев.
 */
object OutputSearchShortcut {

    /** Клавиша сравнивается физическая: код от Compose зависит от раскладки. */
    fun isOpen(key: Key, isCtrlPressed: Boolean, isAltPressed: Boolean, isShiftPressed: Boolean): Boolean =
        key == Key.F && isCtrlPressed && !isAltPressed && !isShiftPressed
}
