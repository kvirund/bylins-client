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

        val hotkeyCode = extractKeyCode(key.keyCode)
        val pressedCode = extractKeyCode(pressedKey.keyCode)
        val hotkeyLocation = extractLocation(key.keyCode)
        val pressedLocation = extractLocation(pressedKey.keyCode)

        val hotkeyIsNumPad = isNumPadLocation(hotkeyLocation)
        val pressedIsNumPad = isNumPadLocation(pressedLocation)

        // Если коды совпадают
        if (hotkeyCode == pressedCode) {
            // Если обе клавиши НЕ с NumPad, или обе с NumPad - совпадение
            if (hotkeyIsNumPad == pressedIsNumPad) {
                return true
            }
            // Если одна NumPad, другая нет - НЕ совпадение (разные физические клавиши)
            // Это позволяет различать NumPad стрелки от обычных стрелок
            return false
        }

        // Если глобальная настройка ignoreNumLock включена
        if (globalIgnoreNumLock && hotkeyIsNumPad && pressedIsNumPad) {
            // Обе клавиши с NumPad - нормализуем и сравниваем
            val hotkeyNumPad = normalizeToNumPadCode(hotkeyCode)
            val pressedNumPad = normalizeToNumPadCode(pressedCode)
            if (hotkeyNumPad == pressedNumPad) {
                return true
            }
        }

        return false
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
        // Значения расположения клавиш в Compose Desktop
        // NumPad клавиши имеют location = -2147483648 (0x80000000)
        // Стандартные клавиши имеют location = 536870912 (0x20000000)
        private const val NUMPAD_LOCATION = -2147483648
        private const val STANDARD_LOCATION = 536870912

        // VK коды NumPad клавиш
        private const val VK_NUMPAD0 = 96
        private const val VK_NUMPAD1 = 97
        private const val VK_NUMPAD2 = 98
        private const val VK_NUMPAD3 = 99
        private const val VK_NUMPAD4 = 100
        private const val VK_NUMPAD5 = 101
        private const val VK_NUMPAD6 = 102
        private const val VK_NUMPAD7 = 103
        private const val VK_NUMPAD8 = 104
        private const val VK_NUMPAD9 = 105
        private const val VK_MULTIPLY = 106
        private const val VK_ADD = 107
        private const val VK_SUBTRACT = 109
        private const val VK_DECIMAL = 110
        private const val VK_DIVIDE = 111

        // VK коды навигационных клавиш (NumPad при выключенном NumLock)
        private const val VK_LEFT = 37
        private const val VK_UP = 38
        private const val VK_RIGHT = 39
        private const val VK_DOWN = 40
        private const val VK_PAGE_UP = 33
        private const val VK_PAGE_DOWN = 34
        private const val VK_HOME = 36
        private const val VK_END = 35
        private const val VK_INSERT = 45
        private const val VK_DELETE = 46
        private const val VK_CLEAR = 12

        /**
         * Извлекает "чистый" код клавиши (верхние 32 бита keyCode)
         */
        fun extractKeyCode(keyCode: Long): Int = (keyCode shr 32).toInt()

        /**
         * Извлекает расположение клавиши (нижние 32 бита)
         */
        fun extractLocation(keyCode: Long): Int = (keyCode and 0xFFFFFFFFL).toInt()

        /**
         * Проверяет, является ли location значением для NumPad
         */
        fun isNumPadLocation(location: Int): Boolean {
            return location == NUMPAD_LOCATION
        }

        /**
         * Проверяет, является ли VK-код кодом NumPad клавиши или её альтернативой
         */
        fun isNumPadKeyCode(code: Int): Boolean {
            return code in VK_NUMPAD0..VK_NUMPAD9 ||
                   code in listOf(VK_MULTIPLY, VK_ADD, VK_SUBTRACT, VK_DECIMAL, VK_DIVIDE) ||
                   // VK_CLEAR - это NumPad5 при выключенном NumLock
                   code == VK_CLEAR
        }

        /**
         * Проверяет, может ли код быть NumPad клавишей (включая навигационные при NumLock OFF)
         */
        fun canBeNumPadKey(code: Int): Boolean {
            return isNumPadKeyCode(code) ||
                   // Навигационные клавиши, которые могут быть NumPad при выключенном NumLock
                   code in listOf(VK_LEFT, VK_UP, VK_RIGHT, VK_DOWN,
                                  VK_PAGE_UP, VK_PAGE_DOWN, VK_HOME, VK_END,
                                  VK_INSERT, VK_DELETE)
        }

        /**
         * Нормализует VK-код к NumPad коду (для сравнения при ignoreNumLock)
         * Навигационные клавиши преобразуются в соответствующие NumPad коды
         */
        fun normalizeToNumPadCode(code: Int): Int {
            return when (code) {
                // Навигация -> NumPad
                VK_INSERT -> VK_NUMPAD0
                VK_END -> VK_NUMPAD1
                VK_DOWN -> VK_NUMPAD2
                VK_PAGE_DOWN -> VK_NUMPAD3
                VK_LEFT -> VK_NUMPAD4
                VK_CLEAR -> VK_NUMPAD5
                VK_RIGHT -> VK_NUMPAD6
                VK_HOME -> VK_NUMPAD7
                VK_UP -> VK_NUMPAD8
                VK_PAGE_UP -> VK_NUMPAD9
                VK_DELETE -> VK_DECIMAL
                // Уже NumPad - возвращаем как есть
                else -> code
            }
        }

        /**
         * Проверяет, является ли клавиша клавишей NumPad (по keyCode)
         */
        fun isNumPadKey(key: Key): Boolean {
            val code = extractKeyCode(key.keyCode)
            val location = extractLocation(key.keyCode)
            return isNumPadLocation(location) || isNumPadKeyCode(code)
        }

        /**
         * Возвращает читаемое имя клавиши
         */
        fun getKeyName(key: Key): String {
            val code = extractKeyCode(key.keyCode)
            val location = extractLocation(key.keyCode)
            val isNumPad = isNumPadLocation(location)

            // Если это NumPad клавиша (по расположению), показываем Num-префикс
            if (isNumPad) {
                return when (code) {
                    // NumPad цифры (VK_NUMPAD0-9: 96-105)
                    96 -> "Num0"
                    97 -> "Num1"
                    98 -> "Num2"
                    99 -> "Num3"
                    100 -> "Num4"
                    101 -> "Num5"
                    102 -> "Num6"
                    103 -> "Num7"
                    104 -> "Num8"
                    105 -> "Num9"
                    // NumPad операторы
                    106 -> "Num*"
                    107 -> "Num+"
                    109 -> "Num-"
                    110 -> "Num."
                    111 -> "Num/"
                    // NumPad Enter
                    10 -> "NumEnter"
                    // NumPad навигация (NumLock OFF)
                    38 -> "Num8"  // VK_UP
                    40 -> "Num2"  // VK_DOWN
                    37 -> "Num4"  // VK_LEFT
                    39 -> "Num6"  // VK_RIGHT
                    36 -> "Num7"  // VK_HOME
                    35 -> "Num1"  // VK_END
                    33 -> "Num9"  // VK_PAGE_UP
                    34 -> "Num3"  // VK_PAGE_DOWN
                    45 -> "Num0"  // VK_INSERT
                    46 -> "Num."  // VK_DELETE
                    12 -> "Num5"  // VK_CLEAR
                    else -> "Num?"
                }
            }

            // Стандартные клавиши по VK-коду
            return when (code) {
                // F-клавиши (VK_F1-F12: 112-123)
                112 -> "F1"
                113 -> "F2"
                114 -> "F3"
                115 -> "F4"
                116 -> "F5"
                117 -> "F6"
                118 -> "F7"
                119 -> "F8"
                120 -> "F9"
                121 -> "F10"
                122 -> "F11"
                123 -> "F12"

                // NumPad (без флага NUMPAD - маловероятно, но на всякий случай)
                96 -> "Num0"
                97 -> "Num1"
                98 -> "Num2"
                99 -> "Num3"
                100 -> "Num4"
                101 -> "Num5"
                102 -> "Num6"
                103 -> "Num7"
                104 -> "Num8"
                105 -> "Num9"
                106 -> "Num*"
                107 -> "Num+"
                109 -> "Num-"
                110 -> "Num."
                111 -> "Num/"

                // Навигация
                38 -> "Up"
                40 -> "Down"
                37 -> "Left"
                39 -> "Right"
                33 -> "PgUp"
                34 -> "PgDown"
                36 -> "Home"
                35 -> "End"
                45 -> "Insert"
                46 -> "Delete"

                // Специальные
                27 -> "Esc"
                9 -> "Tab"
                32 -> "Space"
                10 -> "Enter"
                8 -> "Backspace"
                154 -> "PrtSc"
                145 -> "ScrLk"
                12 -> "Clear"

                // Буквы A-Z (VK_A-Z: 65-90)
                65 -> "A"
                66 -> "B"
                67 -> "C"
                68 -> "D"
                69 -> "E"
                70 -> "F"
                71 -> "G"
                72 -> "H"
                73 -> "I"
                74 -> "J"
                75 -> "K"
                76 -> "L"
                77 -> "M"
                78 -> "N"
                79 -> "O"
                80 -> "P"
                81 -> "Q"
                82 -> "R"
                83 -> "S"
                84 -> "T"
                85 -> "U"
                86 -> "V"
                87 -> "W"
                88 -> "X"
                89 -> "Y"
                90 -> "Z"

                // Цифры (VK_0-9: 48-57)
                48 -> "0"
                49 -> "1"
                50 -> "2"
                51 -> "3"
                52 -> "4"
                53 -> "5"
                54 -> "6"
                55 -> "7"
                56 -> "8"
                57 -> "9"

                // Знаки препинания
                192 -> "`"
                189 -> "-"
                187 -> "="
                219 -> "["
                221 -> "]"
                220 -> "\\"
                186 -> ";"
                222 -> "'"
                188 -> ","
                190 -> "."
                191 -> "/"

                else -> "Key($code)"
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

            // Извлекаем чистый код клавиши (без флагов расположения)
            val code = extractKeyCode(key.keyCode)

            // Набор допустимых кодов клавиш (VK_* коды из java.awt.event.KeyEvent)
            val allowedCodes = setOf(
                // F-клавиши (F1-F12: VK 112-123)
                extractKeyCode(Key.F1.keyCode), extractKeyCode(Key.F2.keyCode),
                extractKeyCode(Key.F3.keyCode), extractKeyCode(Key.F4.keyCode),
                extractKeyCode(Key.F5.keyCode), extractKeyCode(Key.F6.keyCode),
                extractKeyCode(Key.F7.keyCode), extractKeyCode(Key.F8.keyCode),
                extractKeyCode(Key.F9.keyCode), extractKeyCode(Key.F10.keyCode),
                extractKeyCode(Key.F11.keyCode), extractKeyCode(Key.F12.keyCode),
                // NumPad (VK_NUMPAD0-9: 96-105, операторы: 106-111)
                extractKeyCode(Key.NumPad0.keyCode), extractKeyCode(Key.NumPad1.keyCode),
                extractKeyCode(Key.NumPad2.keyCode), extractKeyCode(Key.NumPad3.keyCode),
                extractKeyCode(Key.NumPad4.keyCode), extractKeyCode(Key.NumPad5.keyCode),
                extractKeyCode(Key.NumPad6.keyCode), extractKeyCode(Key.NumPad7.keyCode),
                extractKeyCode(Key.NumPad8.keyCode), extractKeyCode(Key.NumPad9.keyCode),
                extractKeyCode(Key.NumPadDivide.keyCode), extractKeyCode(Key.NumPadMultiply.keyCode),
                extractKeyCode(Key.NumPadSubtract.keyCode), extractKeyCode(Key.NumPadAdd.keyCode),
                extractKeyCode(Key.NumPadEnter.keyCode), extractKeyCode(Key.NumPadDot.keyCode),
                // Навигация
                extractKeyCode(Key.DirectionUp.keyCode), extractKeyCode(Key.DirectionDown.keyCode),
                extractKeyCode(Key.DirectionLeft.keyCode), extractKeyCode(Key.DirectionRight.keyCode),
                extractKeyCode(Key.PageUp.keyCode), extractKeyCode(Key.PageDown.keyCode),
                extractKeyCode(Key.MoveHome.keyCode), extractKeyCode(Key.MoveEnd.keyCode),
                extractKeyCode(Key.Insert.keyCode), extractKeyCode(Key.Delete.keyCode),
                // Специальные
                extractKeyCode(Key.Escape.keyCode), extractKeyCode(Key.PrintScreen.keyCode),
                extractKeyCode(Key.ScrollLock.keyCode),
                // VK_CLEAR = 12 (NumPad5 при выключенном NumLock)
                12
            )

            return code in allowedCodes
        }
    }
}
