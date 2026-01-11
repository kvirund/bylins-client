package com.bylins.client.mapper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Управляет картой мира MUD
 */
class MapManager(
    private val onRoomEnter: ((Room) -> Unit)? = null
) {
    private val _rooms = MutableStateFlow<Map<String, Room>>(emptyMap())
    val rooms: StateFlow<Map<String, Room>> = _rooms

    private val _currentRoomId = MutableStateFlow<String?>(null)
    val currentRoomId: StateFlow<String?> = _currentRoomId

    private val _mapEnabled = MutableStateFlow(true)
    val mapEnabled: StateFlow<Boolean> = _mapEnabled

    private val pathfinder = Pathfinder()
    private val database = MapDatabase()

    /**
     * Добавляет или обновляет комнату на карте
     */
    fun addRoom(room: Room) {
        _rooms.value = _rooms.value + (room.id to room)
    }

    /**
     * Получает комнату по ID
     */
    fun getRoom(id: String): Room? {
        return _rooms.value[id]
    }

    /**
     * Удаляет комнату с карты
     */
    fun removeRoom(id: String) {
        _rooms.value = _rooms.value - id
        if (_currentRoomId.value == id) {
            _currentRoomId.value = null
        }
    }

    /**
     * Устанавливает текущую комнату
     */
    fun setCurrentRoom(roomId: String) {
        if (_rooms.value.containsKey(roomId)) {
            _currentRoomId.value = roomId

            // Помечаем комнату как посещенную
            val room = _rooms.value[roomId]
            if (room != null && !room.visited) {
                val updatedRoom = room.copy(visited = true)
                _rooms.value = _rooms.value + (roomId to updatedRoom)

                // Уведомляем о входе в комнату
                onRoomEnter?.invoke(updatedRoom)
            } else if (room != null) {
                // Уведомляем о входе в комнату даже если уже посещали
                onRoomEnter?.invoke(room)
            }
        }
    }

    /**
     * Получает текущую комнату
     */
    fun getCurrentRoom(): Room? {
        val roomId = _currentRoomId.value ?: return null
        return _rooms.value[roomId]
    }

    /**
     * Обрабатывает движение в указанном направлении
     * Создает новую комнату если необходимо
     */
    fun handleMovement(direction: Direction, newRoomName: String, exits: List<Direction>): Room? {
        val currentRoom = getCurrentRoom()

        if (!_mapEnabled.value) {
            return null
        }

        // Вычисляем координаты новой комнаты
        val (x, y, z) = if (currentRoom != null) {
            Triple(
                currentRoom.x + direction.dx,
                currentRoom.y + direction.dy,
                currentRoom.z + direction.dz
            )
        } else {
            Triple(0, 0, 0)
        }

        // Проверяем есть ли уже комната в этих координатах
        val existingRoom = findRoomAt(x, y, z)

        val targetRoom = if (existingRoom != null) {
            // Комната уже существует, обновляем информацию
            val updated = existingRoom.copy(
                name = newRoomName,
                visited = true
            )
            // Обновляем выходы
            exits.forEach { dir ->
                if (!updated.hasExit(dir)) {
                    // Не перезаписываем существующие выходы
                }
            }
            addRoom(updated)
            updated
        } else {
            // Создаем новую комнату
            val newRoomId = generateRoomId(x, y, z)
            val newRoom = Room(
                id = newRoomId,
                name = newRoomName,
                x = x,
                y = y,
                z = z,
                visited = true
            )

            // Добавляем выходы в новую комнату
            exits.forEach { dir ->
                // Выходы будут добавлены при следующих движениях
            }

            addRoom(newRoom)
            newRoom
        }

        // Создаем связь между текущей и новой комнатой
        if (currentRoom != null) {
            // Добавляем выход из текущей комнаты
            val updatedCurrent = currentRoom.copy()
            updatedCurrent.addExit(direction, targetRoom.id)
            addRoom(updatedCurrent)

            // Добавляем обратный выход
            val updatedTarget = targetRoom.copy()
            updatedTarget.addExit(direction.getOpposite(), currentRoom.id)
            addRoom(updatedTarget)
        }

        // Устанавливаем новую текущую комнату
        setCurrentRoom(targetRoom.id)

        return targetRoom
    }

    /**
     * Находит комнату в указанных координатах
     */
    fun findRoomAt(x: Int, y: Int, z: Int): Room? {
        return _rooms.value.values.firstOrNull {
            it.x == x && it.y == y && it.z == z
        }
    }

    /**
     * Генерирует ID комнаты на основе координат
     */
    private fun generateRoomId(x: Int, y: Int, z: Int): String {
        return "room_${x}_${y}_${z}"
    }

    /**
     * Очищает всю карту
     */
    fun clearMap() {
        _rooms.value = emptyMap()
        _currentRoomId.value = null
    }

    /**
     * Включает/выключает маппинг
     */
    fun setMapEnabled(enabled: Boolean) {
        _mapEnabled.value = enabled
    }

    /**
     * Возвращает все комнаты на указанном уровне (z)
     */
    fun getRoomsOnLevel(z: Int): List<Room> {
        return _rooms.value.values.filter { it.z == z }
    }

    /**
     * Возвращает границы карты (min/max координаты)
     */
    fun getMapBounds(z: Int): MapBounds? {
        val roomsOnLevel = getRoomsOnLevel(z)
        if (roomsOnLevel.isEmpty()) return null

        val minX = roomsOnLevel.minOf { it.x }
        val maxX = roomsOnLevel.maxOf { it.x }
        val minY = roomsOnLevel.minOf { it.y }
        val maxY = roomsOnLevel.maxOf { it.y }

        return MapBounds(minX, maxX, minY, maxY)
    }

    /**
     * Устанавливает заметку для комнаты
     */
    fun setRoomNote(roomId: String, note: String) {
        val room = _rooms.value[roomId] ?: return
        val updated = room.copy(notes = note)
        addRoom(updated)
    }

    /**
     * Устанавливает цвет для комнаты
     */
    fun setRoomColor(roomId: String, color: String?) {
        val room = _rooms.value[roomId] ?: return
        val updated = room.copy(color = color)
        addRoom(updated)
    }

    /**
     * Экспортирует карту в JSON
     */
    fun exportMap(): Map<String, Room> {
        return _rooms.value
    }

    /**
     * Импортирует карту из JSON
     */
    fun importMap(rooms: Map<String, Room>) {
        _rooms.value = rooms
    }

    /**
     * Сохраняет карту в файл
     */
    fun saveToFile(filePath: String? = null): Boolean {
        return try {
            val mapsDir = Paths.get(System.getProperty("user.home"), ".bylins-client", "maps")
            if (!Files.exists(mapsDir)) {
                Files.createDirectories(mapsDir)
            }

            val file = if (filePath != null) {
                File(filePath)
            } else {
                mapsDir.resolve("autosave.json").toFile()
            }

            val json = Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            }

            val jsonString = json.encodeToString(_rooms.value)
            file.writeText(jsonString)

            println("[MapManager] Карта сохранена: ${file.absolutePath} (${_rooms.value.size} комнат)")
            true
        } catch (e: Exception) {
            println("[MapManager] Ошибка сохранения карты: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Загружает карту из файла
     */
    fun loadFromFile(filePath: String? = null): Boolean {
        return try {
            val mapsDir = Paths.get(System.getProperty("user.home"), ".bylins-client", "maps")
            val file = if (filePath != null) {
                File(filePath)
            } else {
                mapsDir.resolve("autosave.json").toFile()
            }

            if (!file.exists()) {
                println("[MapManager] Файл карты не найден: ${file.absolutePath}")
                return false
            }

            val json = Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            }

            val jsonString = file.readText()
            val rooms = json.decodeFromString<Map<String, Room>>(jsonString)

            _rooms.value = rooms
            println("[MapManager] Карта загружена: ${file.absolutePath} (${rooms.size} комнат)")
            true
        } catch (e: Exception) {
            println("[MapManager] Ошибка загрузки карты: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Находит путь между двумя комнатами
     */
    fun findPath(startRoomId: String, endRoomId: String): List<Direction>? {
        return pathfinder.findPath(_rooms.value, startRoomId, endRoomId)
    }

    /**
     * Находит путь от текущей комнаты до заданной
     */
    fun findPathFromCurrent(endRoomId: String): List<Direction>? {
        val currentId = _currentRoomId.value ?: return null
        return findPath(currentId, endRoomId)
    }

    /**
     * Находит путь к ближайшей непосещённой комнате
     */
    fun findNearestUnvisited(): List<Direction>? {
        val currentId = _currentRoomId.value ?: return null
        return pathfinder.findNearestUnvisited(_rooms.value, currentId)
    }

    /**
     * Находит все комнаты в заданном радиусе от текущей
     */
    fun findRoomsInRadius(maxSteps: Int): Map<String, Int> {
        val currentId = _currentRoomId.value ?: return emptyMap()
        return pathfinder.findRoomsInRadius(_rooms.value, currentId, maxSteps)
    }

    // === Работа с базой данных ===

    /**
     * Сохраняет текущую карту в базу данных
     */
    fun saveMapToDatabase(name: String, description: String = ""): Boolean {
        if (_rooms.value.isEmpty()) {
            println("[MapManager] Нет комнат для сохранения")
            return false
        }
        return database.saveMap(name, _rooms.value, description)
    }

    /**
     * Загружает карту из базы данных
     */
    fun loadMapFromDatabase(name: String): Boolean {
        val rooms = database.loadMap(name)
        if (rooms != null) {
            _rooms.value = rooms
            // Сбрасываем текущую комнату
            _currentRoomId.value = null
            return true
        }
        return false
    }

    /**
     * Возвращает список всех карт в базе данных
     */
    fun listMapsInDatabase(): List<MapInfo> {
        return database.listMaps()
    }

    /**
     * Удаляет карту из базы данных
     */
    fun deleteMapFromDatabase(name: String): Boolean {
        return database.deleteMap(name)
    }

    /**
     * Закрывает соединение с базой данных
     */
    fun closeDatabase() {
        database.close()
    }
}

/**
 * Границы карты
 */
data class MapBounds(
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int
) {
    val width: Int get() = maxX - minX + 1
    val height: Int get() = maxY - minY + 1
}
