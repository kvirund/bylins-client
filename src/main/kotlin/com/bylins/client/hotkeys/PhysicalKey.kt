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

    /** Как Compose помечает клавиши цифрового блока в младших битах кода. */
    private const val NUMPAD_LOCATION = 0x80000000L

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
        return of(awt) ?: event.key
    }

    fun of(awt: java.awt.event.KeyEvent): Key? {
        val field = rawCodeField ?: return null
        val raw = runCatching { field.getLong(awt) }.getOrNull() ?: return null
        if (raw <= 0) return null
        // Расположение кодируем так же, как Compose, иначе уже назначенные
        // хоткеи на цифровой клавиатуре перестанут совпадать: у них в конфиге
        // лежит именно это значение
        val location = if (awt.keyLocation == java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD) {
            NUMPAD_LOCATION
        } else {
            0L
        }
        return Key((raw shl 32) or location)
    }
}
