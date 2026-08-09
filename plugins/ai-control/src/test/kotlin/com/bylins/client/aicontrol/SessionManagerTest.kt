package com.bylins.client.aicontrol

import com.bylins.client.plugins.PluginAPI
import com.bylins.client.plugins.TriggerHandle
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionManagerTest {

    /** Список триггеров, снятых через API — так проверяем освобождение ресурсов. */
    private val removedTriggers = mutableListOf<TriggerHandle>()

    /**
     * PluginAPI — большой интерфейс; в тестах нужен только removeTrigger,
     * поэтому подставляем динамический прокси вместо ручной заглушки.
     */
    private val api: PluginAPI = Proxy.newProxyInstance(
        PluginAPI::class.java.classLoader,
        arrayOf(PluginAPI::class.java)
    ) { _, method, args ->
        when (method.name) {
            "removeTrigger" -> {
                removedTriggers.add(args[0] as TriggerHandle)
                null
            }
            "cancelTimer" -> null
            else -> null
        }
    } as PluginAPI

    /**
     * TriggerHandle имеет internal-конструктор (плагины получают хэндлы только
     * из API) — в тесте создаём через рефлексию, не ослабляя production-код.
     */
    private fun triggerHandle(id: String): TriggerHandle {
        val ctor = TriggerHandle::class.java.getDeclaredConstructor(String::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(id)
    }

    private fun manager(idleTimeoutMs: Long = 60_000) =
        SessionManager(api, OutputJournal(100), idleTimeoutMs)

    @Test
    fun `first session gets write lease, second does not`() {
        val m = manager()
        val a = m.open("claude")
        val b = m.open("gpt")

        assertTrue(a.hasWriteLease)
        assertFalse(b.hasWriteLease)
        assertTrue(m.canWrite(a))
        assertFalse(m.canWrite(b))
    }

    @Test
    fun `lease can be handed over explicitly`() {
        val m = manager()
        val a = m.open("claude")
        val b = m.open("gpt")

        assertTrue(m.grantWriteLease(b.id))
        assertFalse(a.hasWriteLease)
        assertTrue(m.canWrite(b))
    }

    @Test
    fun `closing session releases its triggers`() {
        val m = manager()
        val s = m.open("claude")
        val handle = triggerHandle("t1")
        s.addTrigger(handle)

        assertTrue(m.close(s.id))
        assertEquals(listOf(handle), removedTriggers)
        assertNull(m.get(s.id))
    }

    @Test
    fun `lease passes to remaining session when holder closes`() {
        val m = manager()
        val a = m.open("claude")
        val b = m.open("gpt")

        m.close(a.id)
        assertTrue(b.hasWriteLease)
        assertTrue(m.canWrite(b))
    }

    @Test
    fun `muted session cannot write even with lease`() {
        val m = manager()
        val s = m.open("claude")
        s.muted = true
        assertFalse(m.canWrite(s))
    }

    @Test
    fun `session is found by token and not after close`() {
        val m = manager()
        val s = m.open("claude")
        assertNotNull(m.byToken(s.token))
        m.close(s.id)
        assertNull(m.byToken(s.token))
    }

    @Test
    fun `idle sessions are evicted and their resources freed`() {
        val m = manager(idleTimeoutMs = 1000)
        val s = m.open("claude")
        s.addTrigger(triggerHandle("t1"))

        // Имитируем «агент замолчал» — двигаем время вперёд
        val evicted = m.evictIdle(now = System.currentTimeMillis() + 5000)

        assertEquals(listOf(s.id), evicted)
        assertEquals(1, removedTriggers.size)
        assertTrue(m.all().isEmpty())
    }

    @Test
    fun `new session starts at journal head so it sees only new output`() {
        val journal = OutputJournal(100)
        journal.append("старое", 0)
        val m = SessionManager(api, journal, 60_000)

        val s = m.open("claude")
        assertEquals(journal.headSeq, s.cursorSeq)

        journal.append("новое", 0)
        val read = journal.read(s.cursorSeq)
        assertEquals(listOf("новое"), read.lines.map { it.text })
    }
}
