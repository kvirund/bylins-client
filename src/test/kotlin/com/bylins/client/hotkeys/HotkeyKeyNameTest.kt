package com.bylins.client.hotkeys

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Имя клавиши и разбор имени обязаны сходиться: хоткей хранится в конфиге
 * именем, а сравнивается по коду.
 *
 * Таблица имён была написана на кодах Windows Virtual-Key, тогда как Compose
 * отдаёт коды AWT. Совпадали только буквы, цифры и F-клавиши, а знаки
 * разъезжались: «/» приходил кодом 46 (в AWT это точка) и показывался как
 * «Delete», после чего сохранённый хоткей уже не находил свою клавишу — то
 * есть просто не срабатывал.
 */
class HotkeyKeyNameTest {

    private fun roundTrip(key: Key, expectedName: String) {
        val name = Hotkey.getKeyName(key)
        assertEquals(expectedName, name, "имя клавиши")
        assertEquals(key, Hotkey.parseKey(name), "разбор имени «$name» вернул другую клавишу")
    }

    @Test
    fun `знаки препинания называются собой и разбираются обратно`() {
        roundTrip(Key.Slash, "/")
        roundTrip(Key.Period, ".")
        roundTrip(Key.Comma, ",")
        roundTrip(Key.Minus, "-")
        roundTrip(Key.Equals, "=")
        roundTrip(Key.Semicolon, ";")
        roundTrip(Key.Apostrophe, "'")
        roundTrip(Key.Grave, "`")
        roundTrip(Key.LeftBracket, "[")
        roundTrip(Key.RightBracket, "]")
        roundTrip(Key.Backslash, "\\")
    }

    @Test
    fun `Delete и Insert не путаются со знаками`() {
        roundTrip(Key.Delete, "Delete")
        roundTrip(Key.Insert, "Insert")
    }

    @Test
    fun `буквы, цифры и функциональные клавиши не сломаны`() {
        roundTrip(Key.A, "A")
        roundTrip(Key.Z, "Z")
        roundTrip(Key.One, "1")
        roundTrip(Key.Zero, "0")
        roundTrip(Key.F1, "F1")
        roundTrip(Key.F12, "F12")
    }

    @Test
    fun `навигация и служебные клавиши не сломаны`() {
        roundTrip(Key.DirectionUp, "Up")
        roundTrip(Key.DirectionDown, "Down")
        roundTrip(Key.Home, "Home")
        roundTrip(Key.MoveEnd, "End")
        roundTrip(Key.PageUp, "PgUp")
        roundTrip(Key.PageDown, "PgDown")
        roundTrip(Key.Escape, "Esc")
        roundTrip(Key.Tab, "Tab")
        roundTrip(Key.Spacebar, "Space")
        roundTrip(Key.Backspace, "Backspace")
    }

    @Test
    fun `хоткей срабатывает на ту же клавишу после перезагрузки конфига`() {
        // Конфиг хранит имя, поэтому путь «клавиша → имя → клавиша» и есть то,
        // что происходит между сессиями
        val pressed = Key.Slash
        val stored = Hotkey.getKeyName(pressed)
        val restored = Hotkey.parseKey(stored)!!

        val hotkey = Hotkey(id = "1", key = restored, ctrl = true, commands = listOf("смотреть"))
        assertEquals(
            true,
            hotkey.matches(pressed, isCtrlPressed = true, isAltPressed = false, isShiftPressed = false),
            "хоткей на Ctrl+/ не сработал после сохранения и загрузки"
        )
    }
}
