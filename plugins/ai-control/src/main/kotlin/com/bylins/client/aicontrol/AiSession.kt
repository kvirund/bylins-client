package com.bylins.client.aicontrol

import com.bylins.client.plugins.PluginAPI
import com.bylins.client.plugins.TimerHandle
import com.bylins.client.plugins.TriggerHandle
import java.util.concurrent.atomic.AtomicLong

/**
 * Контекст одного ИИ-агента.
 *
 * Ключевая идея — владение ресурсами: всё, что агент создал (триггеры, таймеры),
 * записывается в сессию, и [release] снимает это разом. Поэтому «агент ушёл» =
 * «его триггеры больше не висят», без ручной уборки.
 */
class AiSession(
    val id: String,
    val name: String,
    /** Токен именно этой сессии: агент предъявляет его при каждом вызове. */
    val token: String,
    /** С какого номера строки агент ещё не читал вывод. */
    startSeq: Long,
    val createdAt: Long
) {
    /** Курсор чтения вывода (следующий непрочитанный seq). */
    @Volatile
    var cursorSeq: Long = startSeq

    /** Последняя активность — по ней истекают «мёртвые» сессии. */
    @Volatile
    var lastSeenAt: Long = createdAt

    /** Запрещена ли отправка команд (игрок может «замьютить» агента). */
    @Volatile
    var muted: Boolean = false

    /** Держит ли сессия право на запись (эксклюзивный доступ к отправке команд). */
    @Volatile
    var hasWriteLease: Boolean = false

    /** Закрыта ли сессия (ресурсы освобождены). */
    @Volatile
    var closed: Boolean = false
        private set

    private val commandsSent = AtomicLong(0)
    private val commandsRejected = AtomicLong(0)

    val stats: Stats
        get() = Stats(
            commandsSent = commandsSent.get(),
            commandsRejected = commandsRejected.get(),
            triggers = triggers.size,
            timers = timers.size
        )

    // Ресурсы, которыми владеет сессия
    private val triggers = mutableListOf<TriggerHandle>()
    private val timers = mutableListOf<TimerHandle>()
    private val lock = Any()

    fun addTrigger(handle: TriggerHandle) = synchronized(lock) { triggers.add(handle) }
    fun addTimer(handle: TimerHandle) = synchronized(lock) { timers.add(handle) }

    fun countCommandSent() { commandsSent.incrementAndGet() }
    fun countCommandRejected() { commandsRejected.incrementAndGet() }

    fun touch() { lastSeenAt = System.currentTimeMillis() }

    /**
     * Снимает все ресурсы сессии. Идемпотентна: повторный вызов безопасен
     * (сессию могут закрыть одновременно игрок и таймаут).
     */
    fun release(api: PluginAPI) = synchronized(lock) {
        if (closed) return
        closed = true
        triggers.forEach { runCatching { api.removeTrigger(it) } }
        timers.forEach { runCatching { api.cancelTimer(it) } }
        triggers.clear()
        timers.clear()
        hasWriteLease = false
    }

    data class Stats(
        val commandsSent: Long,
        val commandsRejected: Long,
        val triggers: Int,
        val timers: Int
    )
}
