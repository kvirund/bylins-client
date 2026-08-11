package com.bylins.client.hotkeys

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Хоткей определяется по физической клавише: код Windows (rawCode) одинаков в
 * любой раскладке, тогда как код Compose вычисляется по символу и меняется —
 * одна и та же клавиша приходит как Slash(47) в английской раскладке и как
 * Period(46) в русской. Хоткей, назначенный в одной, в другой не срабатывал.
 *
 * Здесь проверяется, что имена строятся по кодам Windows и что путь
 * «клавиша → имя → клавиша», которым хоткей проходит между сессиями, замкнут.
 */
class HotkeyKeyNameTest {

    /** Клавиша так, как её строит PhysicalKey: код Windows + расположение. */
    private fun windowsKey(vk: Int, location: Long = 0x20000000L) = Key((vk.toLong() shl 32) or location)

    private fun assertName(vk: Int, expected: String) {
        assertEquals(expected, Hotkey.getKeyName(windowsKey(vk)), "имя для VK $vk")
    }

    @Test
    fun `знаки препинания названы по кодам Windows`() {
        assertName(191, "/")   // VK_OEM_2 — та самая клавиша Ctrl+/
        assertName(190, ".")   // VK_OEM_PERIOD
        assertName(188, ",")   // VK_OEM_COMMA
        assertName(189, "-")   // VK_OEM_MINUS
        assertName(187, "=")   // VK_OEM_PLUS
        assertName(186, ";")   // VK_OEM_1
        assertName(222, "'")   // VK_OEM_7
        assertName(192, "`")   // VK_OEM_3
        assertName(219, "[")   // VK_OEM_4
        assertName(221, "]")   // VK_OEM_6
        assertName(220, "\\")  // VK_OEM_5
    }

    @Test
    fun `Delete и Insert не путаются со знаками`() {
        assertName(46, "Delete")
        assertName(45, "Insert")
    }

    @Test
    fun `буквы, цифры и функциональные клавиши на месте`() {
        assertName(65, "A")
        assertName(90, "Z")
        assertName(49, "1")
        assertName(48, "0")
        assertName(112, "F1")
        assertName(123, "F12")
    }

    @Test
    fun `имя разбирается обратно в ту же клавишу`() {
        // Конфиг хранит имя, поэтому важен именно круг «имя → клавиша → имя»
        listOf("/", ".", "-", ";", "[", "A", "F5", "Delete", "Insert", "Enter", "Esc").forEach { name ->
            val key = Hotkey.parseKey(name)
            assertTrue(key != null, "имя «$name» не разбирается")
            assertEquals(name, Hotkey.getKeyName(key!!), "круг для «$name» не замкнулся")
        }
    }

    @Test
    fun `цифры совпадают с константами Compose`() {
        // На Key.One..Key.Zero держатся контекстные команды Alt+1..0: если наш
        // код клавиши перестаёт им соответствовать, они молча перестают работать
        assertEquals(Key.One, windowsKey(49))
        assertEquals(Key.Two, windowsKey(50))
        assertEquals(Key.Zero, windowsKey(48))
        assertEquals(Key.A, windowsKey(65))
        assertEquals(Key.F1, windowsKey(112))
    }

    @Test
    fun `клавиши цифрового блока остаются отличимы от основных`() {
        // Compose помечает нумпад старшим битом в младшей половине кода;
        // уже назначенные Num-хоткеи хранят именно такое значение
        val numpadFour = windowsKey(100, 0x80000000L)
        assertEquals("Num4", Hotkey.getKeyName(numpadFour))
        assertEquals(Hotkey.parseKey("Num4"), numpadFour)
    }

    @Test
    fun `хоткей срабатывает на ту же физическую клавишу`() {
        val slash = windowsKey(191)
        val hotkey = Hotkey(id = "1", key = slash, ctrl = true, commands = listOf("смотреть"))

        assertTrue(
            hotkey.matches(slash, isCtrlPressed = true, isAltPressed = false, isShiftPressed = false),
            "хоткей на Ctrl+/ не сработал"
        )
    }
}
