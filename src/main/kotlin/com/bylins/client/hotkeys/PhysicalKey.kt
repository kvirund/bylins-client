package com.bylins.client.hotkeys

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import mu.KotlinLogging
import java.lang.reflect.Field

private val logger = KotlinLogging.logger("PhysicalKey")

/**
 * Приводит нажатие к физической клавише, не зависящей от раскладки.
 *
 * Compose отдаёт код AWT, а тот вычисляется по символу текущей раскладки: одна
 * и та же клавиша приходит как Slash(47) в английской и как Period(46) в
 * русской. Хоткей, назначенный в одной раскладке, в другой просто не срабатывал.
 *
 * В самом событии AWT есть `rawCode` — код клавиши от Windows (VK_OEM_2 = 191
 * для «/?»), одинаковый в любой раскладке. Его и берём. Поле приватное,
 * поэтому нужен `--add-opens java.desktop/java.awt.event=ALL-UNNAMED`; если
 * доступа нет (другая ОС, запуск без флага), откатываемся на прежнее
 * поведение — хоткеи продолжат работать в раскладке, где их назначили.
 */
object PhysicalKey {

    private val rawCodeField: Field? = runCatching {
        java.awt.event.KeyEvent::class.java.getDeclaredField("rawCode").apply { isAccessible = true }
    }.onFailure {
        logger.warn { "Физический код клавиши недоступен (${it.javaClass.simpleName}): хоткеи будут зависеть от раскладки" }
    }.getOrNull()

    /** Доступен ли раскладко-независимый код. Для диагностики в UI. */
    val available: Boolean get() = rawCodeField != null

    /**
     * Клавиша, пригодная и для сохранения, и для сравнения.
     *
     * Возвращает Key с кодом Windows и тем же расположением (NumPad остаётся
     * отличим от основной клавиатуры).
     */
    fun of(event: KeyEvent): Key {
        val awt = event.nativeKeyEvent as? java.awt.event.KeyEvent ?: return event.key
        val field = rawCodeField ?: return event.key
        val raw = runCatching { field.getLong(awt) }.getOrNull() ?: return event.key
        if (raw <= 0) return event.key

        // Расположение берём из самого события, а не пересобираем: Compose
        // кодирует его по-своему, и своя нумерация ломала сравнение с его же
        // константами — Key.One переставала совпадать, а на ней держатся
        // контекстные команды Alt+1..0
        val location = event.key.keyCode and 0xFFFFFFFFL
        return Key((raw shl 32) or location)
    }
}
