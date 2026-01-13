package com.bylins.client.hotkeys

import androidx.compose.ui.input.key.Key

data class Hotkey(
    val id: String,
    val key: Key,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val commands: List<String>,
    val enabled: Boolean = true,
    val ignoreNumLock: Boolean = false // Игнорировать состояние NumLock для клавиш цифровой клавиатуры
) {
    /**
     * Проверяет, соответствует ли хоткей нажатым клавишам
     * @param globalIgnoreNumLock глобальная настройка игнорирования NumLock
     */
    fun matches(
        pressedKey: Key,
        isCtrlPressed: Boolean,
        isAltPressed: Boolean,
        isShiftPressed: Boolean,
        globalIgnoreNumLock: Boolean = false
    ): Boolean {
        // Проверяем модификаторы
        if (ctrl != isCtrlPressed || alt != isAltPressed || shift != isShiftPressed) {
            return false
        }

        // Если глобальная настройка ignoreNumLock включена и это NumPad клавиша
        if (globalIgnoreNumLock && (isNumPadKey(key) || isNumPadKey(pressedKey))) {
            // Сопоставляем обе формы NumPad клавиши
            return pressedKey == key || pressedKey == getNumPadAlternative(key) || key == getNumPadAlternative(pressedKey)
        }

        return key == pressedKey
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
        /**
         * Проверяет, является ли клавиша клавишей NumPad
         */
        fun isNumPadKey(key: Key): Boolean {
            return key in listOf(
                Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
                Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
                Key.NumPadDivide, Key.NumPadMultiply, Key.NumPadSubtract,
                Key.NumPadAdd, Key.NumPadEnter, Key.NumPadDot
            )
        }

        /**
         * Возвращает альтернативную клавишу NumPad (когда NumLock выключен)
         * NumPad8 -> Up, NumPad2 -> Down, и т.д.
         */
        fun getNumPadAlternative(key: Key): Key {
            return when (key) {
                Key.NumPad0 -> Key.Insert
                Key.NumPad1 -> Key.MoveEnd
                Key.NumPad2 -> Key.DirectionDown
                Key.NumPad3 -> Key.PageDown
                Key.NumPad4 -> Key.DirectionLeft
                Key.NumPad5 -> key // NumPad5 не имеет альтернативы
                Key.NumPad6 -> Key.DirectionRight
                Key.NumPad7 -> Key.MoveHome
                Key.NumPad8 -> Key.DirectionUp
                Key.NumPad9 -> Key.PageUp
                Key.NumPadDot -> Key.Delete
                // Обратное сопоставление
                Key.Insert -> Key.NumPad0
                Key.MoveEnd -> Key.NumPad1
                Key.DirectionDown -> Key.NumPad2
                Key.PageDown -> Key.NumPad3
                Key.DirectionLeft -> Key.NumPad4
                Key.DirectionRight -> Key.NumPad6
                Key.MoveHome -> Key.NumPad7
                Key.DirectionUp -> Key.NumPad8
                Key.PageUp -> Key.NumPad9
                Key.Delete -> Key.NumPadDot
                else -> key
            }
        }

        /**
         * Возвращает читаемое имя клавиши
         */
        fun getKeyName(key: Key): String {
            return when (key) {
                // Функциональные клавиши
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

                // NumPad
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
                Key.NumPadDivide -> "Num/"
                Key.NumPadMultiply -> "Num*"
                Key.NumPadSubtract -> "Num-"
                Key.NumPadAdd -> "Num+"
                Key.NumPadEnter -> "NumEnter"
                Key.NumPadDot -> "Num."

                // Навигация
                Key.DirectionUp -> "Up"
                Key.DirectionDown -> "Down"
                Key.DirectionLeft -> "Left"
                Key.DirectionRight -> "Right"
                Key.PageUp -> "PgUp"
                Key.PageDown -> "PgDown"
                Key.MoveHome -> "Home"
                Key.MoveEnd -> "End"
                Key.Insert -> "Insert"
                Key.Delete -> "Delete"

                // Специальные
                Key.Escape -> "Esc"
                Key.Tab -> "Tab"
                Key.Spacebar -> "Space"
                Key.Enter -> "Enter"
                Key.Backspace -> "Backspace"
                Key.PrintScreen -> "PrtSc"
                Key.ScrollLock -> "ScrLk"

                // Буквы A-Z
                Key.A -> "A"
                Key.B -> "B"
                Key.C -> "C"
                Key.D -> "D"
                Key.E -> "E"
                Key.F -> "F"
                Key.G -> "G"
                Key.H -> "H"
                Key.I -> "I"
                Key.J -> "J"
                Key.K -> "K"
                Key.L -> "L"
                Key.M -> "M"
                Key.N -> "N"
                Key.O -> "O"
                Key.P -> "P"
                Key.Q -> "Q"
                Key.R -> "R"
                Key.S -> "S"
                Key.T -> "T"
                Key.U -> "U"
                Key.V -> "V"
                Key.W -> "W"
                Key.X -> "X"
                Key.Y -> "Y"
                Key.Z -> "Z"

                // Цифры
                Key.Zero -> "0"
                Key.One -> "1"
                Key.Two -> "2"
                Key.Three -> "3"
                Key.Four -> "4"
                Key.Five -> "5"
                Key.Six -> "6"
                Key.Seven -> "7"
                Key.Eight -> "8"
                Key.Nine -> "9"

                // Знаки препинания и символы
                Key.Grave -> "`"
                Key.Minus -> "-"
                Key.Equals -> "="
                Key.LeftBracket -> "["
                Key.RightBracket -> "]"
                Key.Backslash -> "\\"
                Key.Semicolon -> ";"
                Key.Apostrophe -> "'"
                Key.Comma -> ","
                Key.Period -> "."
                Key.Slash -> "/"

                else -> "Key(${key.keyCode})"
            }
        }

        /**
         * Парсит имя клавиши в Key
         */
        fun parseKey(keyName: String): Key? {
            return when (keyName.uppercase()) {
                // Функциональные клавиши
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

                // NumPad
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
                "NUM/" -> Key.NumPadDivide
                "NUM*" -> Key.NumPadMultiply
                "NUM-" -> Key.NumPadSubtract
                "NUM+" -> Key.NumPadAdd
                "NUMENTER" -> Key.NumPadEnter
                "NUM." -> Key.NumPadDot

                // Навигация
                "UP" -> Key.DirectionUp
                "DOWN" -> Key.DirectionDown
                "LEFT" -> Key.DirectionLeft
                "RIGHT" -> Key.DirectionRight
                "PGUP", "PAGEUP" -> Key.PageUp
                "PGDOWN", "PAGEDOWN" -> Key.PageDown
                "HOME" -> Key.MoveHome
                "END" -> Key.MoveEnd
                "INSERT", "INS" -> Key.Insert
                "DELETE", "DEL" -> Key.Delete

                // Специальные
                "ESC", "ESCAPE" -> Key.Escape
                "TAB" -> Key.Tab
                "SPACE", "SPACEBAR" -> Key.Spacebar
                "ENTER", "RETURN" -> Key.Enter
                "BACKSPACE", "BS" -> Key.Backspace
                "PRTSC", "PRINTSCREEN" -> Key.PrintScreen
                "SCRLK", "SCROLLLOCK" -> Key.ScrollLock

                // Буквы
                "A" -> Key.A
                "B" -> Key.B
                "C" -> Key.C
                "D" -> Key.D
                "E" -> Key.E
                "F" -> Key.F
                "G" -> Key.G
                "H" -> Key.H
                "I" -> Key.I
                "J" -> Key.J
                "K" -> Key.K
                "L" -> Key.L
                "M" -> Key.M
                "N" -> Key.N
                "O" -> Key.O
                "P" -> Key.P
                "Q" -> Key.Q
                "R" -> Key.R
                "S" -> Key.S
                "T" -> Key.T
                "U" -> Key.U
                "V" -> Key.V
                "W" -> Key.W
                "X" -> Key.X
                "Y" -> Key.Y
                "Z" -> Key.Z

                // Цифры
                "0" -> Key.Zero
                "1" -> Key.One
                "2" -> Key.Two
                "3" -> Key.Three
                "4" -> Key.Four
                "5" -> Key.Five
                "6" -> Key.Six
                "7" -> Key.Seven
                "8" -> Key.Eight
                "9" -> Key.Nine

                // Символы
                "`" -> Key.Grave
                "-" -> Key.Minus
                "=" -> Key.Equals
                "[" -> Key.LeftBracket
                "]" -> Key.RightBracket
                "\\" -> Key.Backslash
                ";" -> Key.Semicolon
                "'" -> Key.Apostrophe
                "," -> Key.Comma
                "." -> Key.Period
                "/" -> Key.Slash

                else -> {
                    // Попытка распарсить Key(код)
                    val keyCodeMatch = Regex("KEY\\((\\d+)\\)").matchEntire(keyName.uppercase())
                    if (keyCodeMatch != null) {
                        val code = keyCodeMatch.groupValues[1].toLongOrNull()
                        if (code != null) {
                            Key(code)
                        } else null
                    } else null
                }
            }
        }

        /**
         * Проверяет, является ли клавиша "непечатной" (пригодной для хоткея)
         * Непечатные: F-клавиши, стрелки, Home/End/PgUp/PgDown, Insert/Delete,
         * NumPad, Escape, а также любые с Ctrl/Alt модификаторами
         */
        fun isNonPrintable(key: Key, hasModifier: Boolean): Boolean {
            // С модификатором Ctrl или Alt - любая клавиша подходит
            if (hasModifier) return true

            // Без модификаторов - только специальные клавиши
            return key in listOf(
                // F-клавиши
                Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6,
                Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12,
                // NumPad
                Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
                Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
                Key.NumPadDivide, Key.NumPadMultiply, Key.NumPadSubtract,
                Key.NumPadAdd, Key.NumPadEnter, Key.NumPadDot,
                // Навигация
                Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                Key.PageUp, Key.PageDown, Key.MoveHome, Key.MoveEnd,
                Key.Insert, Key.Delete,
                // Специальные
                Key.Escape, Key.PrintScreen, Key.ScrollLock
            )
        }
    }
}
