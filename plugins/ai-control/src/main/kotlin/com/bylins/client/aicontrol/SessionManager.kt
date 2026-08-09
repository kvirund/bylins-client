package com.bylins.client.aicontrol

import com.bylins.client.plugins.PluginAPI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Реестр контекстов ИИ-агентов.
 *
 * Отвечает за: выдачу сессий, арбитраж права на запись (чтобы агенты не
 * перебивали друг друга командами) и уборку «мёртвых» сессий по таймауту.
 */
class SessionManager(
    private val api: PluginAPI,
    private val journal: OutputJournal,
    /** Сколько миллисекунд без активности до автозакрытия сессии. */
    private val idleTimeoutMs: Long = 5 * 60_000,
    /** Через сколько молчания держателя право записи можно забрать. */
    private val leaseIdleMs: Long = 30_000
) {
    private val sessions = ConcurrentHashMap<String, AiSession>()

    /** Кто сейчас держит право отправлять команды (id сессии). */
    @Volatile
    private var writeLeaseHolder: String? = null

    fun all(): List<AiSession> = sessions.values.sortedBy { it.createdAt }

    fun get(id: String): AiSession? = sessions[id]

    /** Находит сессию по её токену — так авторизуются запросы агента. */
    fun byToken(token: String): AiSession? = sessions.values.find { it.token == token && !it.closed }

    /**
     * Открывает контекст. Курсор ставится на конец журнала: агент по умолчанию
     * видит только то, что произойдёт после его подключения.
     */
    fun open(name: String, fromStart: Boolean = false): AiSession {
        val session = AiSession(
            id = UUID.randomUUID().toString().take(8),
            name = name,
            token = UUID.randomUUID().toString().replace("-", ""),
            startSeq = if (fromStart) journal.tailSeq else journal.headSeq,
            createdAt = System.currentTimeMillis()
        )
        sessions[session.id] = session
        // Первый подключившийся сразу получает право писать
        if (writeLeaseHolder == null) {
            writeLeaseHolder = session.id
            session.hasWriteLease = true
        }
        return session
    }

    /** Закрывает контекст и снимает все его триггеры/таймеры. */
    fun close(id: String): Boolean {
        val session = sessions.remove(id) ?: return false
        session.release(api)
        if (writeLeaseHolder == id) {
            writeLeaseHolder = null
            // Право писать переходит к самой старой из оставшихся сессий
            all().firstOrNull()?.let { next ->
                writeLeaseHolder = next.id
                next.hasWriteLease = true
            }
        }
        return true
    }

    fun closeAll() {
        all().forEach { close(it.id) }
    }

    /**
     * Передаёт право записи сессии [id] (принудительно — как «#ai take»
     * со стороны игрока или запрос агента с более высоким приоритетом).
     */
    fun grantWriteLease(id: String): Boolean {
        val session = sessions[id] ?: return false
        sessions.values.forEach { it.hasWriteLease = false }
        session.hasWriteLease = true
        writeLeaseHolder = id
        return true
    }

    /** Может ли сессия отправлять команды прямо сейчас. */
    fun canWrite(session: AiSession): Boolean =
        !session.closed && !session.muted && session.hasWriteLease

    /**
     * Запрос права записи агентом.
     *
     * Отдаём, если держатель молчит дольше [leaseIdleMs] — иначе живой агент
     * не может отобрать перо у зависшего, и остальные простаивают. Активного
     * держателя не перебиваем: для этого есть явная передача игроком.
     */
    fun requestWriteLease(session: AiSession, now: Long = System.currentTimeMillis()): Boolean {
        if (session.hasWriteLease) return true
        val holder = sessions.values.find { it.hasWriteLease && !it.closed }
        if (holder != null && now - holder.lastSeenAt < leaseIdleMs) return false
        return grantWriteLease(session.id)
    }

    /**
     * Закрывает сессии, от которых давно не было запросов.
     * @return id закрытых сессий (для лога игроку).
     */
    fun evictIdle(now: Long = System.currentTimeMillis()): List<String> {
        val dead = sessions.values.filter { now - it.lastSeenAt > idleTimeoutMs }
        dead.forEach { close(it.id) }
        return dead.map { it.id }
    }
}
