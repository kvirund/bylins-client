package com.bylins.client.mapper

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Выход, прописанный вручную.
 *
 * Связи маппер строит по строке «Вых:» в промпте, поэтому проходы, которых там
 * нет, в карту не попадают никогда: скрытые (описаны прозой) и тёмные («юг:
 * слишком темно»). Комнаты за ними в базе есть, а рёбер нет — и маршрут туда
 * не строится, хотя игрок этим путём ходит.
 */
class ManualExitTest {

    private val tempDbs = mutableListOf<java.io.File>()

    private fun mapManager(): MapManager {
        val file = java.io.File.createTempFile("bylins-exit-test", ".db").also { tempDbs.add(it) }
        return MapManager(dbFileName = file.absolutePath).apply {
            clearMap()
            setMapEnabled(true)
        }
    }

    /** Лощина → (скрытый восток) → Логово → (тёмный юг) → Нора. */
    private fun MapManager.threeRooms() {
        addRoom(Room(id = "4759", name = "Лощина"))
        addRoom(Room(id = "4760", name = "Волчье логово"))
        addRoom(Room(id = "4761", name = "Узкая нора"))
    }

    @AfterTest
    fun cleanUp() {
        tempDbs.forEach { it.delete() }
        tempDbs.clear()
    }

    @Test
    fun `выход прописывается и связывает комнаты`() {
        val map = mapManager()
        map.threeRooms()

        val error = map.setExit("4759", Direction.EAST, "4760")

        assertNull(error, "выход не записан: $error")
        assertEquals("4760", map.getRoom("4759")?.exits?.get(Direction.EAST)?.targetRoomId)
    }

    @Test
    fun `обратный выход сам не появляется`() {
        // Односторонние переходы в игре обычны: падения, телепорты, сдвиги
        val map = mapManager()
        map.threeRooms()

        map.setExit("4759", Direction.EAST, "4760")

        assertTrue(map.getRoom("4760")?.exits?.isEmpty() == true, "появилось обратное ребро")
    }

    @Test
    fun `both записывает и обратный выход`() {
        val map = mapManager()
        map.threeRooms()

        map.setExit("4759", Direction.EAST, "4760", both = true)

        assertEquals("4759", map.getRoom("4760")?.exits?.get(Direction.WEST)?.targetRoomId)
    }

    @Test
    fun `несуществующая цель — отказ, а не висячее ребро`() {
        val map = mapManager()
        map.threeRooms()

        val error = map.setExit("4759", Direction.EAST, "9999")

        assertNotNull(error)
        assertTrue(error.contains("9999"), error)
        assertTrue(map.getRoom("4759")?.exits?.isEmpty() == true, "ребро всё равно записалось")
    }

    @Test
    fun `выход сам в себя не записывается`() {
        val map = mapManager()
        map.threeRooms()

        assertNotNull(map.setExit("4759", Direction.EAST, "4759"))
    }

    @Test
    fun `выход снимается`() {
        val map = mapManager()
        map.threeRooms()
        map.setExit("4759", Direction.EAST, "4760")

        assertTrue(map.removeExit("4759", Direction.EAST))
        assertTrue(map.getRoom("4759")?.exits?.isEmpty() == true)
    }

    @Test
    fun `снять несуществующий выход — не ошибка молчанием`() {
        val map = mapManager()
        map.threeRooms()

        assertEquals(false, map.removeExit("4759", Direction.EAST))
        assertEquals(false, map.removeExit("нет-такой", Direction.EAST))
    }

    @Test
    fun `правка полей комнаты выход не затирает`() {
        // Ради этого выходы и понадобились в API: правя заметки и выходы в один
        // заход, агент не должен думать о порядке вызовов
        val map = mapManager()
        map.threeRooms()
        map.setExit("4759", Direction.EAST, "4760")

        map.updateRoom("4759", notes = "за кустами лаз")

        assertEquals("4760", map.getRoom("4759")?.exits?.get(Direction.EAST)?.targetRoomId)
        assertEquals("за кустами лаз", map.getRoom("4759")?.notes)
    }

    @Test
    fun `маршрут строится через прописанный выход`() {
        // Симптом, ради которого всё затевалось: до Лощины путь есть, а до Норы
        // за скрытым и тёмным проходами — нет, хотя идти два шага
        val map = mapManager()
        map.threeRooms()
        map.setCurrentRoom("4759")

        assertTrue(map.findPath("4759", "4761").isNullOrEmpty(), "путь нашёлся до прописи выходов")

        map.setExit("4759", Direction.EAST, "4760")
        map.setExit("4760", Direction.SOUTH, "4761")

        assertEquals(listOf(Direction.EAST, Direction.SOUTH), map.findPath("4759", "4761"))
    }

    @Test
    fun `направление разбирается и по имени, и по-русски`() {
        // Ручки чтения отдают EAST — что прочитал, тем и пиши
        assertEquals(Direction.EAST, Direction.parse("EAST"))
        assertEquals(Direction.EAST, Direction.parse("east"))
        assertEquals(Direction.EAST, Direction.parse("восток"))
        assertEquals(Direction.EAST, Direction.parse("в"))
        assertEquals(Direction.SOUTHWEST, Direction.parse("юз"))
        assertNull(Direction.parse("вбок"))
    }
}
