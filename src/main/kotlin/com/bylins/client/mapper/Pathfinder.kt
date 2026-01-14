package com.bylins.client.mapper

import java.util.*
import kotlin.math.abs

/**
 * Находит путь между комнатами используя BFS и A* алгоритмы
 */
class Pathfinder {
    /**
     * Находит кратчайший путь от текущей комнаты до целевой
     *
     * @param rooms Карта всех комнат
     * @param startRoomId ID начальной комнаты
     * @param endRoomId ID конечной комнаты
     * @return Список направлений для перемещения или null если путь не найден
     */
    fun findPath(
        rooms: Map<String, Room>,
        startRoomId: String,
        endRoomId: String
    ): List<Direction>? {
        val startRoom = rooms[startRoomId] ?: return null
        val endRoom = rooms[endRoomId] ?: return null

        // Проверяем что комнаты существуют
        if (startRoom == endRoom) {
            return emptyList() // Уже в нужной комнате
        }

        // BFS для поиска кратчайшего пути
        val queue: Queue<PathNode> = LinkedList()
        val visited = mutableSetOf<String>()

        queue.offer(PathNode(startRoomId, emptyList()))
        visited.add(startRoomId)

        while (queue.isNotEmpty()) {
            val current = queue.poll()
            val currentRoom = rooms[current.roomId] ?: continue

            // Проверяем все выходы из текущей комнаты
            for ((direction, exit) in currentRoom.exits) {
                val nextRoomId = exit.targetRoomId

                // Пропускаем уже посещённые комнаты
                if (nextRoomId in visited) continue

                val newPath = current.path + direction

                // Если достигли цели, возвращаем путь
                if (nextRoomId == endRoomId) {
                    return newPath
                }

                // Добавляем в очередь для дальнейшего поиска
                queue.offer(PathNode(nextRoomId, newPath))
                visited.add(nextRoomId)
            }
        }

        // Путь не найден
        return null
    }

    /**
     * Находит путь к ближайшей непосещённой комнате
     */
    fun findNearestUnvisited(
        rooms: Map<String, Room>,
        startRoomId: String
    ): List<Direction>? {
        rooms[startRoomId] ?: return null

        // BFS для поиска ближайшей непосещённой комнаты
        val queue: Queue<PathNode> = LinkedList()
        val visited = mutableSetOf<String>()

        queue.offer(PathNode(startRoomId, emptyList()))
        visited.add(startRoomId)

        while (queue.isNotEmpty()) {
            val current = queue.poll()
            val currentRoom = rooms[current.roomId] ?: continue

            // Проверяем все выходы
            for ((direction, exit) in currentRoom.exits) {
                val nextRoomId = exit.targetRoomId
                val nextRoom = rooms[nextRoomId]

                if (nextRoomId in visited) continue

                val newPath = current.path + direction

                // Если нашли непосещённую комнату
                if (nextRoom != null && !nextRoom.visited) {
                    return newPath
                }

                queue.offer(PathNode(nextRoomId, newPath))
                visited.add(nextRoomId)
            }
        }

        // Нет непосещённых комнат
        return null
    }

    /**
     * Находит все комнаты в заданном радиусе
     */
    fun findRoomsInRadius(
        rooms: Map<String, Room>,
        startRoomId: String,
        maxSteps: Int
    ): Map<String, Int> {
        rooms[startRoomId] ?: return emptyMap()

        val distances = mutableMapOf<String, Int>()
        val queue: Queue<Pair<String, Int>> = LinkedList()

        queue.offer(Pair(startRoomId, 0))
        distances[startRoomId] = 0

        while (queue.isNotEmpty()) {
            val (currentRoomId, distance) = queue.poll()
            val currentRoom = rooms[currentRoomId] ?: continue

            // Не идём дальше максимального расстояния
            if (distance >= maxSteps) continue

            for ((_, exit) in currentRoom.exits) {
                val nextRoomId = exit.targetRoomId

                // Если уже посетили эту комнату, пропускаем
                if (nextRoomId in distances) continue

                val newDistance = distance + 1
                distances[nextRoomId] = newDistance
                queue.offer(Pair(nextRoomId, newDistance))
            }
        }

        return distances
    }

    /**
     * Находит кратчайший путь используя A* алгоритм (более эффективный чем BFS)
     *
     * @param rooms Карта всех комнат
     * @param startRoomId ID начальной комнаты
     * @param endRoomId ID конечной комнаты
     * @return Список направлений для перемещения или null если путь не найден
     */
    fun findPathAStar(
        rooms: Map<String, Room>,
        startRoomId: String,
        endRoomId: String
    ): List<Direction>? {
        val startRoom = rooms[startRoomId] ?: return null
        val endRoom = rooms[endRoomId] ?: return null

        if (startRoom == endRoom) {
            return emptyList() // Уже в нужной комнате
        }

        // Эвристика: без координат возвращаем 0 (превращает A* в Dijkstra)
        // Координаты теперь вычисляются динамически при рендеринге
        @Suppress("UNUSED_PARAMETER")
        fun heuristic(room: Room): Int {
            return 0
        }

        // Priority queue для A* (сортируется по f = g + h)
        val openSet = PriorityQueue<AStarNode>(compareBy { it.f })
        val closedSet = mutableSetOf<String>()
        val gScore = mutableMapOf<String, Int>() // Стоимость пути от старта до комнаты
        val cameFrom = mutableMapOf<String, Pair<String, Direction>>() // Откуда пришли и каким направлением

        gScore[startRoomId] = 0
        openSet.offer(AStarNode(startRoomId, 0, heuristic(startRoom), emptyList()))

        while (openSet.isNotEmpty()) {
            val current = openSet.poll()

            // Если достигли цели
            if (current.roomId == endRoomId) {
                return current.path
            }

            // Добавляем в закрытый список
            if (current.roomId in closedSet) continue
            closedSet.add(current.roomId)

            val currentRoom = rooms[current.roomId] ?: continue
            val currentG = gScore[current.roomId] ?: Int.MAX_VALUE

            // Проверяем все выходы
            for ((direction, exit) in currentRoom.exits) {
                val nextRoomId = exit.targetRoomId
                if (nextRoomId in closedSet) continue

                val nextRoom = rooms[nextRoomId] ?: continue
                val tentativeG = currentG + 1 // Стоимость перехода = 1

                // Если нашли более короткий путь
                if (tentativeG < (gScore[nextRoomId] ?: Int.MAX_VALUE)) {
                    gScore[nextRoomId] = tentativeG
                    val newPath = current.path + direction
                    val h = heuristic(nextRoom)
                    val f = tentativeG + h

                    cameFrom[nextRoomId] = Pair(current.roomId, direction)
                    openSet.offer(AStarNode(nextRoomId, tentativeG, f, newPath))
                }
            }
        }

        // Путь не найден
        return null
    }

    /**
     * Ищет комнаты по имени или описанию
     *
     * @param rooms Карта всех комнат
     * @param query Поисковый запрос (регистронезависимый)
     * @param searchInDescription Искать также в описании комнат
     * @return Список найденных комнат
     */
    fun searchRooms(
        rooms: Map<String, Room>,
        query: String,
        searchInDescription: Boolean = false
    ): List<Room> {
        if (query.isBlank()) return emptyList()

        val queryLower = query.lowercase()
        return rooms.values.filter { room ->
            // Поиск по ID (точное совпадение)
            val idMatch = room.id == query
            val nameMatch = room.name.lowercase().contains(queryLower)
            val descMatch = searchInDescription && room.description.lowercase().contains(queryLower)
            val notesMatch = room.notes.lowercase().contains(queryLower)
            idMatch || nameMatch || descMatch || notesMatch
        }
    }

    /**
     * Находит ближайшую комнату из списка
     *
     * @param rooms Карта всех комнат
     * @param startRoomId ID начальной комнаты
     * @param targetRooms Список комнат для поиска
     * @return Пара (Room, путь к ней) или null если не найдено
     */
    fun findNearestRoom(
        rooms: Map<String, Room>,
        startRoomId: String,
        targetRooms: List<Room>
    ): Pair<Room, List<Direction>>? {
        if (targetRooms.isEmpty()) return null

        var bestRoom: Room? = null
        var bestPath: List<Direction>? = null
        var bestDistance = Int.MAX_VALUE

        for (targetRoom in targetRooms) {
            val path = findPathAStar(rooms, startRoomId, targetRoom.id)
            if (path != null && path.size < bestDistance) {
                bestDistance = path.size
                bestRoom = targetRoom
                bestPath = path
            }
        }

        return if (bestRoom != null && bestPath != null) {
            Pair(bestRoom, bestPath)
        } else {
            null
        }
    }

    /**
     * Вспомогательный класс для хранения узла пути (BFS)
     */
    private data class PathNode(
        val roomId: String,
        val path: List<Direction>
    )

    /**
     * Вспомогательный класс для A* алгоритма
     */
    private data class AStarNode(
        val roomId: String,
        val g: Int,  // Стоимость пути от старта
        val f: Int,  // g + h (общая оценка)
        val path: List<Direction>
    )
}
