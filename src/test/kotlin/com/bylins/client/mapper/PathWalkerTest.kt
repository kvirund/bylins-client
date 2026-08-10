package com.bylins.client.mapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ходок шлёт следующий шаг только после подтверждения предыдущего.
 *
 * Раньше маршрут уходил целиком (`север;север;...`) или по таймеру, и первая
 * же заминка — дверь, бой, «слишком устали» — уводила остаток команд в чужую
 * комнату. Тесты держат именно это: без смены комнаты новых команд нет.
 */
class PathWalkerTest {

    private val sent = mutableListOf<String>()
    private val notes = mutableListOf<String>()
    private var room: String? = "1"

    private fun walker(route: MutableList<Direction>, target: String) = PathWalker(
        currentRoomId = { room },
        send = { sent.add(it) },
        notify = { notes.add(it) }
    ).also { walker ->
        walker.onStopped = { }
        walker.start(
            next = { route.removeFirstOrNull() },
            arrived = { room == target }
        )
    }

    @Test
    fun `шлёт по одному шагу, ожидая смены комнаты`() {
        val walker = walker(mutableListOf(Direction.NORTH, Direction.EAST), target = "3")

        // Первый шаг ушёл сразу, второго ещё нет
        assertEquals(listOf("с"), sent)

        room = "2"
        walker.onRoomChanged()
        assertEquals(listOf("с", "в"), sent)

        room = "3"
        walker.onRoomChanged()
        assertFalse(walker.isWalking)
        assertTrue(notes.any { it.contains("Пришли") }, notes.toString())
    }

    @Test
    fun `не шлёт следующий шаг, пока комната прежняя`() {
        val walker = walker(mutableListOf(Direction.NORTH, Direction.EAST), target = "3")
        assertEquals(1, sent.size)

        // Сервер не пустил: события смены комнаты не было
        assertTrue(walker.isWalking)
        assertEquals(1, sent.size)
    }

    @Test
    fun `повторное событие без смены комнаты шаг не двигает`() {
        val walker = walker(mutableListOf(Direction.NORTH, Direction.EAST), target = "3")

        // MSDP шлёт ROOM на каждый промпт — событие приходит, комната прежняя
        walker.onRoomChanged()
        walker.onRoomChanged()

        assertEquals(listOf("с"), sent)
        assertTrue(walker.isWalking)
    }

    @Test
    fun `сторож останавливает застрявший маршрут`() {
        val walker = walker(mutableListOf(Direction.NORTH, Direction.EAST), target = "3")

        walker.onTimeout() // комната та же — шаг не прошёл

        assertFalse(walker.isWalking)
        assertEquals(1, sent.size)
        assertTrue(notes.any { it.contains("Застряли") && it.contains("север") }, notes.toString())
    }

    @Test
    fun `сторож молчит, если шаг всё-таки прошёл`() {
        val walker = walker(mutableListOf(Direction.NORTH, Direction.EAST), target = "3")

        room = "2"
        walker.onTimeout()

        assertTrue(walker.isWalking)
        assertTrue(notes.isEmpty(), notes.toString())
    }

    @Test
    fun `останавливается, когда шаги кончились, а цели нет`() {
        val walker = walker(mutableListOf(Direction.NORTH), target = "99")

        room = "2"
        walker.onRoomChanged()

        assertFalse(walker.isWalking)
        assertTrue(notes.any { it.contains("оборвался") }, notes.toString())
    }

    @Test
    fun `не выходит из дома, если уже на месте`() {
        val walker = walker(mutableListOf(Direction.NORTH), target = "1")

        assertFalse(walker.isWalking)
        assertTrue(sent.isEmpty())
        assertTrue(notes.any { it.contains("уже на месте") }, notes.toString())
    }

    @Test
    fun `остановка игроком прекращает отправку`() {
        val walker = walker(mutableListOf(Direction.NORTH, Direction.EAST), target = "3")

        walker.stop("Остановлено")
        room = "2"
        walker.onRoomChanged()

        assertEquals(listOf("с"), sent)
        assertFalse(walker.isWalking)
    }
}
