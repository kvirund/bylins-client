package com.bylins.client.hotkeys

import androidx.compose.ui.input.key.Key

data class Hotkey(
    val id: String,
    val name: String,
    val key: Key,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val commands: List<String>,
    val enabled: Boolean = true
) {
    /**
     * Проверяет, соответствует ли хоткей нажатым клавишам
     */
    fun matches(
        pressedKey: Key,
        isCtrlPressed: Boolean,
        isAltPressed: Boolean,
        isShiftPressed: Boolean
    ): Boolean {
        return key == pressedKey &&
                ctrl == isCtrlPressed &&
                alt == isAltPressed &&
                shift == isShiftPressed
    }

    /**
     * Возвращает строковое представление комбинации клавиш
     */
    fun getKeyCombo(): String {
        val parts = mutableListOf<String>()
        if (ctrl) parts.add("Ctrl")
        if (alt) parts.add("Alt")
        if (shift) parts.add("Shift")
        parts.add(getKeyName(key))
        return parts.joinToString("+")
    }

    companion object {
        fun getKeyName(key: Key): String {
            return when (key) {
                Key.F1 -> "F1"
                Key.F2 -> "F2"
                Key.F3 -> "F3"
                Key.F4 -> "F4"
                Key.F5 -> "F5"
                Key.F6 -> "F6"
                Key.F7 -> "F7"
                Key.F8 -> "F8"
                Key.F9 -> "F9"
                Key.F10 -> "F10"
                Key.F11 -> "F11"
                Key.F12 -> "F12"
                Key.NumPad0 -> "Num0"
                Key.NumPad1 -> "Num1"
                Key.NumPad2 -> "Num2"
                Key.NumPad3 -> "Num3"
                Key.NumPad4 -> "Num4"
                Key.NumPad5 -> "Num5"
                Key.NumPad6 -> "Num6"
                Key.NumPad7 -> "Num7"
                Key.NumPad8 -> "Num8"
                Key.NumPad9 -> "Num9"
                else -> key.keyCode.toString()
            }
        }

        fun parseKey(keyName: String): Key? {
            return when (keyName.uppercase()) {
                "F1" -> Key.F1
                "F2" -> Key.F2
                "F3" -> Key.F3
                "F4" -> Key.F4
                "F5" -> Key.F5
                "F6" -> Key.F6
                "F7" -> Key.F7
                "F8" -> Key.F8
                "F9" -> Key.F9
                "F10" -> Key.F10
                "F11" -> Key.F11
                "F12" -> Key.F12
                "NUM0" -> Key.NumPad0
                "NUM1" -> Key.NumPad1
                "NUM2" -> Key.NumPad2
                "NUM3" -> Key.NumPad3
                "NUM4" -> Key.NumPad4
                "NUM5" -> Key.NumPad5
                "NUM6" -> Key.NumPad6
                "NUM7" -> Key.NumPad7
                "NUM8" -> Key.NumPad8
                "NUM9" -> Key.NumPad9
                else -> null
            }
        }
    }
}
