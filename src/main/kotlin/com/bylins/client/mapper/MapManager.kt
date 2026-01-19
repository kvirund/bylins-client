package com.bylins.client.mapper

import mu.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Управляет картой мира MUD
 */
private val logger = KotlinLogging.logger("MapManager")
class MapManager(
    private val dbFileName: String = "maps.db",
    private val onRoomEnter: ((Room) -> Unit)? = null
) {
    private val _rooms = MutableStateFlow<Map<String, Room>>(emptyMap())
    val rooms: StateFlow<Map<String, Room>> = _rooms

    private val _currentRoomId = MutableStateFlow<String?>(null)
    val currentRoomId: StateFlow<String?> = _currentRoomId

    // Комната, на которую центрирована карта при просмотре (сохраняется между сессиями)
    private val _viewCenterRoomId = MutableStateFlow<String?>(null)
    val viewCenterRoomId: StateFlow<String?> = _viewCenterRoomId

    private val _mapEnabled = MutableStateFlow(false)
    val mapEnabled: StateFlow<Boolean> = _mapEnabled

    // Active path tracking (Direction-based, for automatic path following)
    private val _activePath = MutableStateFlow<List<Direction>>(emptyList())
    val activePath: StateFlow<List<Direction>> = _activePath

    private val _targetRoomId = MutableStateFlow<String?>(null)
    val targetRoomId: StateFlow<String?> = _targetRoomId

    // Path highlighting (room ID-based, for scripts)
    private val _pathHighlightRoomIds = MutableStateFlow<Set<String>>(emptySet())
    val pathHighlightRoomIds: StateFlow<Set<String>> = _pathHighlightRoomIds

    private val _pathHighlightTargetId = MutableStateFlow<String?>(null)
    val pathHighlightTargetId: StateFlow<String?> = _pathHighlightTargetId

    // Zone notes and names
    private val _zoneNotes = MutableStateFlow<Map<String, String>>(emptyMap())
    val zoneNotes: StateFlow<Map<String, String>> = _zoneNotes

    private val _zoneNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val zoneNames: StateFlow<Map<String, String>> = _zoneNames

    private val pathfinder = Pathfinder()
    private val database = MapDatabase(dbFileName)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        loadFromDatabase()
        deleteOldAutosaveJson()
    }

    /**
     * Удаляет старый autosave.json если существует (миграция)
     */
    private fun deleteOldAutosaveJson() {
        try {
            val mapsDir = Paths.get(System.getProperty("user.home"), ".bylins-client", "maps")
            val autosaveFile = mapsDir.resolve("autosave.json").toFile()
            if (autosaveFile.exists()) {
                autosaveFile.delete()
                logger.info { "Deleted old autosave.json (migrated to SQLite)" }
            }
        } catch (e: Exception) {
            logger.error { "Error deleting autosave.json: ${e.message}" }
        }
    }

    /**
     * Загружает данные из БД при старте
     */
    private fun loadFromDatabase() {
        val savedRooms = database.loadAllRooms()
        if (savedRooms != null && savedRooms.isNotEmpty()) {
            _rooms.value = savedRooms
            logger.info { "Loaded ${savedRooms.size} rooms from database" }
        }

        // Load zones (name and notes)
        val savedZones = database.loadAllZones()
        if (savedZones.isNotEmpty()) {
            val names = mutableMapOf<String, String>()
            val notes = mutableMapOf<String, String>()
            savedZones.forEach { (zoneId, pair) ->
                pair.first?.let { names[zoneId] = it }
                if (pair.second.isNotEmpty()) {
                    notes[zoneId] = pair.second
                }
            }
            _zoneNames.value = names
            _zoneNotes.value = notes
            logger.info { "Loaded ${savedZones.size} zones from database" }
        }
    }

    /**
     * Добавляет или обновляет комнату на карте
     */
    fun addRoom(room: Room) {
        _rooms.value = _rooms.value + (room.id to room)
        scope.launch {
            database.saveRoom(room)
        }
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
        scope.launch {
            database.deleteRoom(id)
        }
    }

    /**
     * Устанавливает текущую комнату
     */
    fun setCurrentRoom(roomId: String) {
        if (_rooms.value.containsKey(roomId)) {
            val previousRoomId = _currentRoomId.value
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

            // Update active path
            updateActivePath(previousRoomId, roomId)
        }
    }

    /**
     * Updates the active path when player moves
     */
    private fun updateActivePath(fromRoomId: String?, toRoomId: String) {
        val currentPath = _activePath.value
        val targetId = _targetRoomId.value

        if (currentPath.isEmpty() || targetId == null) {
            return // No active path
        }

        // Check if we reached the target
        if (toRoomId == targetId) {
            logger.info { "Reached target room, clearing path" }
            clearPath()
            return
        }

        // Check if the move matches the expected direction
        if (fromRoomId != null && currentPath.isNotEmpty()) {
            val expectedDir = currentPath.first()
            val fromRoom = _rooms.value[fromRoomId]
            val expectedTargetId = fromRoom?.exits?.get(expectedDir)?.targetRoomId

            if (expectedTargetId == toRoomId) {
                // Move matches expected direction, remove first step
                _activePath.value = currentPath.drop(1)
                logger.info { "Path updated: ${_activePath.value.size} steps remaining" }
            } else {
                // Player went off-path, recalculate
                logger.info { "Player went off-path, recalculating..." }
                recalculatePath()
            }
        }
    }

    /**
     * Recalculates path from current room to target
     */
    private fun recalculatePath() {
        val currentId = _currentRoomId.value
        val targetId = _targetRoomId.value

        if (currentId == null || targetId == null) {
            clearPath()
            return
        }

        val newPath = pathfinder.findPath(_rooms.value, currentId, targetId)
        if (newPath != null) {
            _activePath.value = newPath
            logger.info { "Path recalculated: ${newPath.size} steps" }
        } else {
            logger.warn { "Could not find path to $targetId, clearing" }
            clearPath()
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
     * Возвращает имя файла базы данных
     */
    fun getDbFileName(): String = dbFileName

    /**
     * Обрабатывает движение в указанном направлении
     * Создает новую комнату если необходимо
     * @param roomId ID комнаты из игры (обязателен для создания новых комнат)
     */
    fun handleMovement(direction: Direction, newRoomName: String, exits: List<Direction>, roomId: String? = null): Room? {
        logger.info { "handleMovement: dir=$direction name='$newRoomName' exits=$exits roomId=$roomId" }
        val currentRoom = getCurrentRoom()

        if (!_mapEnabled.value) {
            return null
        }

        // Проверяем есть ли уже комната с таким ID
        val existingRoom = roomId?.let { _rooms.value[it] }

        val targetRoom = if (existingRoom != null) {
            // Комната уже существует, обновляем информацию
            val updated = existingRoom.copy(
                name = newRoomName,
                visited = true
            )
            addRoom(updated)
            updated
        } else {
            // Создаем новую комнату - требуется ID из игры
            val newRoomId = roomId ?: run {
                logger.info { "ERROR: roomId required for new rooms" }
                return null
            }
            val newRoom = Room(
                id = newRoomId,
                name = newRoomName,
                visited = true
            )

            addRoom(newRoom)
            newRoom
        }

        // Создаем связь между текущей и новой комнатой
        if (currentRoom != null) {
            // Добавляем выход из текущей комнаты
            val updatedCurrent = currentRoom.copy()
            updatedCurrent.addExit(direction, targetRoom.id)
            addRoom(updatedCurrent)

            // Добавляем обратный выход и все известные выходы на целевую комнату
            val updatedTarget = targetRoom.copy()
            updatedTarget.addExit(direction.getOpposite(), currentRoom.id)
            // Добавляем неизведанные выходы из промпта
            exits.forEach { exitDir ->
                updatedTarget.addUnexploredExit(exitDir)
            }
            addRoom(updatedTarget)
        } else {
            // Начальная комната - добавляем неизведанные выходы
            val updatedTarget = targetRoom.copy()
            exits.forEach { exitDir ->
                updatedTarget.addUnexploredExit(exitDir)
            }
            addRoom(updatedTarget)
        }

        // Устанавливаем новую текущую комнату
        setCurrentRoom(targetRoom.id)

        return targetRoom
    }

    /**
     * Расширенная версия handleMovement с поддержкой zone, terrain
     */
    fun handleMovement(
        direction: Direction,
        newRoomName: String,
        exits: List<Direction>,
        roomId: String?,
        zone: String?,
        terrain: String?
    ): Room? {
        logger.info { "handleMovement(extended): dir=$direction name='$newRoomName' exits=$exits roomId=$roomId zone=$zone terrain=$terrain" }
        val currentRoom = getCurrentRoom()

        if (!_mapEnabled.value) {
            return null
        }

        // Проверяем есть ли уже комната с таким ID
        val existingRoom = roomId?.let { _rooms.value[it] }

        val targetRoom = if (existingRoom != null) {
            // Комната уже существует, обновляем информацию
            val updated = existingRoom.copy(
                name = newRoomName,
                visited = true,
                zone = zone ?: existingRoom.zone,
                terrain = terrain ?: existingRoom.terrain
            )
            addRoom(updated)
            updated
        } else {
            // Создаем новую комнату - требуется ID из игры
            val newRoomId = roomId ?: run {
                logger.info { "ERROR: roomId required for new rooms" }
                return null
            }
            val newRoom = Room(
                id = newRoomId,
                name = newRoomName,
                visited = true,
                zone = zone,
                terrain = terrain
            )

            addRoom(newRoom)
            newRoom
        }

        // Создаем связь между текущей и новой комнатой
        if (currentRoom != null) {
            // Добавляем выход из текущей комнаты
            val updatedCurrent = currentRoom.copy()
            updatedCurrent.addExit(direction, targetRoom.id)
            addRoom(updatedCurrent)

            // Добавляем обратный выход и все известные выходы на целевую комнату
            val updatedTarget = targetRoom.copy()
            updatedTarget.addExit(direction.getOpposite(), currentRoom.id)
            // Добавляем неизведанные выходы из промпта
            exits.forEach { exitDir ->
                updatedTarget.addUnexploredExit(exitDir)
            }
            addRoom(updatedTarget)
        } else {
            // Начальная комната - добавляем неизведанные выходы
            val updatedTarget = targetRoom.copy()
            exits.forEach { exitDir ->
                updatedTarget.addUnexploredExit(exitDir)
            }
            addRoom(updatedTarget)
        }

        // Устанавливаем новую текущую комнату
        setCurrentRoom(targetRoom.id)

        return targetRoom
    }

    /**
     * Находит направление между двумя комнатами
     * Используется для определения направления движения из fromRoom в toRoom
     */
    fun findDirectionBetween(fromRoomId: String, toRoomId: String): Direction? {
        val fromRoom = _rooms.value[fromRoomId] ?: return null
        // Ищем выход, ведущий в toRoom
        return fromRoom.exits.entries.find { it.value.targetRoomId == toRoomId }?.key
    }

    /**
     * Очищает всю карту
     */
    fun clearMap() {
        _rooms.value = emptyMap()
        _currentRoomId.value = null
        scope.launch {
            database.clearAllRooms()
        }
    }

    /**
     * Включает/выключает маппинг
     */
    fun setMapEnabled(enabled: Boolean) {
        _mapEnabled.value = enabled
    }

    /**
     * Устанавливает комнату для центрирования карты при просмотре
     */
    fun setViewCenterRoom(roomId: String?) {
        _viewCenterRoomId.value = roomId
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
     * Устанавливает тип поверхности (terrain) для комнаты
     */
    fun setRoomTerrain(roomId: String, terrain: String?) {
        val room = _rooms.value[roomId] ?: return
        val updated = room.copy(terrain = terrain)
        addRoom(updated)
    }

    /**
     * Добавляет тег к комнате
     */
    fun addRoomTag(roomId: String, tag: String) {
        val room = _rooms.value[roomId] ?: return
        val updatedTags = room.tags + tag
        val updated = room.copy(tags = updatedTags)
        addRoom(updated)
    }

    /**
     * Удаляет тег у комнаты
     */
    fun removeRoomTag(roomId: String, tag: String) {
        val room = _rooms.value[roomId] ?: return
        val updatedTags = room.tags - tag
        val updated = room.copy(tags = updatedTags)
        addRoom(updated)
    }

    /**
     * Устанавливает теги для комнаты
     */
    fun setRoomTags(roomId: String, tags: Set<String>) {
        val room = _rooms.value[roomId] ?: return
        val updated = room.copy(tags = tags)
        addRoom(updated)
    }

    /**
     * Полное обновление комнаты - название, заметки, terrain, теги, зона, выходы, visited
     */
    fun updateRoom(
        roomId: String,
        name: String,
        note: String,
        terrain: String?,
        tags: Set<String>,
        zone: String,
        exits: Map<Direction, Exit>,
        visited: Boolean
    ) {
        val room = _rooms.value[roomId] ?: return
        val updated = room.copy(
            name = name,
            notes = note,
            terrain = terrain,
            tags = tags,
            zone = zone.ifBlank { null },
            exits = exits.toMutableMap(),
            visited = visited
        )
        addRoom(updated)
        logger.info { "Updated room $roomId: name='$name', terrain=$terrain, visited=$visited, exits=${exits.keys}" }
    }

    /**
     * Получает все уникальные теги со всех комнат
     */
    fun getAllTags(): Set<String> {
        return _rooms.value.values.flatMap { it.tags }.toSet()
    }

    /**
     * Фильтрует комнаты по тегу
     */
    fun getRoomsByTag(tag: String): List<Room> {
        return _rooms.value.values.filter { tag in it.tags }
    }

    /**
     * Экспортирует карту в Map (для сериализации)
     */
    fun exportMap(): Map<String, Room> {
        return _rooms.value
    }

    /**
     * Импортирует карту из Map
     */
    fun importMap(rooms: Map<String, Room>) {
        _rooms.value = rooms
        // Сохраняем все комнаты в БД
        scope.launch {
            rooms.values.forEach { room ->
                database.saveRoom(room)
            }
        }
    }

    /**
     * Находит путь между двумя комнатами используя BFS
     */
    fun findPath(startRoomId: String, endRoomId: String): List<Direction>? {
        return pathfinder.findPath(_rooms.value, startRoomId, endRoomId)
    }

    /**
     * Находит путь между двумя комнатами используя A* (более эффективный)
     */
    fun findPathAStar(startRoomId: String, endRoomId: String): List<Direction>? {
        return pathfinder.findPathAStar(_rooms.value, startRoomId, endRoomId)
    }

    /**
     * Находит путь от текущей комнаты до заданной (использует A*)
     */
    fun findPathFromCurrent(endRoomId: String): List<Direction>? {
        val currentId = _currentRoomId.value ?: return null
        return findPathAStar(currentId, endRoomId)
    }

    /**
     * Sets an active path to the target room
     * The path will be tracked and updated as the player moves
     */
    fun setPathTo(targetRoomId: String): Boolean {
        val path = findPathFromCurrent(targetRoomId)
        if (path != null) {
            _activePath.value = path
            _targetRoomId.value = targetRoomId
            logger.info { "Path set: ${path.size} steps to $targetRoomId" }
            return true
        }
        return false
    }

    /**
     * Clears the active path
     */
    fun clearPath() {
        _activePath.value = emptyList()
        _targetRoomId.value = null
    }

    /**
     * Sets path highlighting (for scripts)
     * This is separate from the Direction-based activePath
     */
    fun setPathHighlight(roomIds: Set<String>, targetRoomId: String?) {
        _pathHighlightRoomIds.value = roomIds
        _pathHighlightTargetId.value = targetRoomId
    }

    /**
     * Clears path highlighting
     */
    fun clearPathHighlight() {
        _pathHighlightRoomIds.value = emptySet()
        _pathHighlightTargetId.value = null
    }

    /**
     * Gets the next direction in the active path (or null if no path)
     */
    fun getNextPathDirection(): Direction? {
        return _activePath.value.firstOrNull()
    }

    /**
     * Gets preview of upcoming directions (up to N steps)
     */
    fun getPathPreview(steps: Int = 5): List<Direction> {
        return _activePath.value.take(steps)
    }

    /**
     * Находит путь к ближайшей непосещённой комнате
     */
    fun findNearestUnvisited(): List<Direction>? {
        val currentId = _currentRoomId.value ?: return null
        return pathfinder.findNearestUnvisited(_rooms.value, currentId)
    }

    /**
     * Ищет комнаты по имени или описанию
     */
    fun searchRooms(query: String, searchInDescription: Boolean = false): List<Room> {
        return pathfinder.searchRooms(_rooms.value, query, searchInDescription)
    }

    /**
     * Находит ближайшую комнату из списка результатов поиска
     */
    fun findNearestFromSearch(targetRooms: List<Room>): Pair<Room, List<Direction>>? {
        val currentId = _currentRoomId.value ?: return null
        return pathfinder.findNearestRoom(_rooms.value, currentId, targetRooms)
    }

    /**
     * Находит все комнаты в заданном радиусе от текущей
     */
    fun findRoomsInRadius(maxSteps: Int): Map<String, Int> {
        val currentId = _currentRoomId.value ?: return emptyMap()
        return pathfinder.findRoomsInRadius(_rooms.value, currentId, maxSteps)
    }

    // === Работа с зонами ===

    private val zoneDetector = ZoneDetector()

    /**
     * Автоматически детектирует и присваивает зоны всем комнатам
     */
    fun detectAndAssignZones() {
        _rooms.value = zoneDetector.detectAndAssignZones(_rooms.value)
        logger.info { "Zones detected and assigned" }
    }

    /**
     * Возвращает статистику по зонам
     */
    fun getZoneStatistics(): Map<String, Int> {
        return zoneDetector.getZoneStatistics(_rooms.value)
    }

    /**
     * Возвращает все комнаты в указанной зоне
     */
    fun getRoomsByZone(zoneName: String): List<Room> {
        return _rooms.value.values.filter { it.zone == zoneName }
    }

    /**
     * Возвращает список всех зон на карте
     */
    fun getAllZones(): List<String> {
        return _rooms.value.values
            .mapNotNull { it.zone }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    /**
     * Устанавливает зону для комнаты вручную
     */
    fun setRoomZone(roomId: String, zoneName: String) {
        val room = _rooms.value[roomId] ?: return
        val updated = room.copy(zone = zoneName)
        addRoom(updated)
    }

    /**
     * Очищает зоны у всех комнат
     */
    fun clearAllZones() {
        _rooms.value = _rooms.value.mapValues { (_, room) ->
            room.copy(zone = "")
        }
        logger.info { "All zones cleared" }
    }

    // === Работа с заметками и именами зон ===

    /**
     * Получает заметки для зоны
     */
    fun getZoneNotes(zoneId: String): String {
        return _zoneNotes.value[zoneId] ?: ""
    }

    /**
     * Устанавливает заметки для зоны
     */
    fun setZoneNotes(zoneId: String, notes: String) {
        if (zoneId.isBlank()) return
        _zoneNotes.value = _zoneNotes.value + (zoneId to notes)
        scope.launch {
            database.saveZone(zoneId, notes = notes)
        }
    }

    /**
     * Получает имя зоны (area name) по zone_id
     */
    fun getZoneName(zoneId: String): String? {
        return _zoneNames.value[zoneId]
    }

    /**
     * Устанавливает имя зоны (вызывается при получении MSDP AREA)
     */
    fun setZoneName(zoneId: String, areaName: String) {
        if (zoneId.isBlank() || areaName.isBlank()) return
        // Не обновляем если уже есть такое же имя
        if (_zoneNames.value[zoneId] == areaName) return
        _zoneNames.value = _zoneNames.value + (zoneId to areaName)
        scope.launch {
            database.saveZone(zoneId, name = areaName)
        }
        logger.debug { "Saved zone name: $zoneId -> $areaName" }
    }

    /**
     * Закрывает соединение с базой данных
     */
    fun closeDatabase() {
        database.close()
    }

    /**
     * Вызывается при закрытии приложения
     */
    fun shutdown() {
        scope.cancel()
        database.close()
    }
}
