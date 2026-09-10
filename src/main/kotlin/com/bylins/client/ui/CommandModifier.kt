package com.bylins.client.ui

import com.bylins.client.OperatingSystem

/**
 * Модификатор «команда»: Ctrl везде и Cmd на macOS.
 *
 * Копирование, выделение и поиск в панели вывода были прибиты к Ctrl, а на
 * macOS эти сочетания живут на Cmd — Ctrl+C там означает совсем другое. Чтобы
 * места, где это проверяется, не разъезжались, правило одно на всех.
 */
object CommandModifier {

    /**
     * @param os передаётся ради тестов; в работе берётся текущая система
     */
    fun isPressed(
        isCtrlPressed: Boolean,
        isMetaPressed: Boolean,
        os: OperatingSystem = OperatingSystem.current
    ): Boolean = isCtrlPressed || (os == OperatingSystem.MacOS && isMetaPressed)
}
