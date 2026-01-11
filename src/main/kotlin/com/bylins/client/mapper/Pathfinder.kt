package com.bylins.client.mapper

import java.util.*

/**
 * Находит путь между комнатами используя BFS (Breadth-First Search)
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
        val startRoom = rooms[startRoomId] ?: return null

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
        val startRoom = rooms[startRoomId] ?: return emptyMap()

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
     * Вспомогательный класс для хранения узла пути
     */
    private data class PathNode(
        val roomId: String,
        val path: List<Direction>
    )
}
