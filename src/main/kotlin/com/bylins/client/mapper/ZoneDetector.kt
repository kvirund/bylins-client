package com.bylins.client.mapper

import kotlin.math.sqrt

/**
 * Автоматическая детекция зон на карте
 * Анализирует названия комнат и их расположение для определения зон
 */
class ZoneDetector {

    /**
     * Детектирует зоны на основе названий комнат и их расположения
     */
    fun detectZones(rooms: Map<String, Room>): Map<String, String> {
        if (rooms.isEmpty()) return emptyMap()

        val roomList = rooms.values.toList()
        val zoneAssignments = mutableMapOf<String, String>()

        // 1. Группируем комнаты по общим словам в названиях
        val roomsByCommonWords = groupByCommonWords(roomList)

        // 2. Применяем пространственную кластеризацию
        val clusters = spatialClustering(roomList, maxDistance = 10)

        // 3. Объединяем результаты
        for ((clusterId, clusterRooms) in clusters.withIndex()) {
            val zoneName = determineZoneName(clusterRooms, roomsByCommonWords)
            clusterRooms.forEach { room ->
                zoneAssignments[room.id] = zoneName
            }
        }

        return zoneAssignments
    }

    /**
     * Группирует комнаты по общим словам в названиях
     */
    private fun groupByCommonWords(rooms: List<Room>): Map<Set<String>, List<Room>> {
        val groups = mutableMapOf<Set<String>, MutableList<Room>>()

        for (room in rooms) {
            val words = extractSignificantWords(room.name)
            if (words.isNotEmpty()) {
                val existing = groups.keys.firstOrNull { existingWords ->
                    words.intersect(existingWords).isNotEmpty()
                }

                if (existing != null) {
                    groups[existing]?.add(room)
                } else {
                    groups[words] = mutableListOf(room)
                }
            }
        }

        return groups
    }

    /**
     * Извлекает значимые слова из названия комнаты
     * Игнорирует короткие и служебные слова
     */
    private fun extractSignificantWords(name: String): Set<String> {
        val stopWords = setOf("в", "на", "у", "за", "по", "к", "с", "из", "до", "о", "об", "и", "а")

        return name.lowercase()
            .split(Regex("[\\s,.!?;:()-]+"))
            .filter { it.length > 2 && !stopWords.contains(it) }
            .toSet()
    }

    /**
     * Пространственная кластеризация комнат
     * Группирует близлежащие комнаты
     */
    private fun spatialClustering(rooms: List<Room>, maxDistance: Int): List<List<Room>> {
        val visited = mutableSetOf<String>()
        val clusters = mutableListOf<List<Room>>()

        for (room in rooms) {
            if (room.id in visited) continue

            val cluster = mutableListOf<Room>()
            val queue = mutableListOf(room)

            while (queue.isNotEmpty()) {
                val current = queue.removeAt(0)
                if (current.id in visited) continue

                visited.add(current.id)
                cluster.add(current)

                // Находим соседние комнаты в радиусе maxDistance
                val neighbors = rooms.filter { other ->
                    other.id !in visited &&
                    other.z == current.z &&
                    distance(current, other) <= maxDistance
                }

                queue.addAll(neighbors)
            }

            if (cluster.isNotEmpty()) {
                clusters.add(cluster)
            }
        }

        return clusters
    }

    /**
     * Вычисляет евклидово расстояние между двумя комнатами
     */
    private fun distance(room1: Room, room2: Room): Double {
        val dx = (room1.x - room2.x).toDouble()
        val dy = (room1.y - room2.y).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Определяет название зоны на основе комнат в кластере
     */
    private fun determineZoneName(
        clusterRooms: List<Room>,
        wordGroups: Map<Set<String>, List<Room>>
    ): String {
        // Находим наиболее частые слова в названиях комнат кластера
        val wordFrequency = mutableMapOf<String, Int>()

        for (room in clusterRooms) {
            val words = extractSignificantWords(room.name)
            for (word in words) {
                wordFrequency[word] = wordFrequency.getOrDefault(word, 0) + 1
            }
        }

        // Находим слова, которые встречаются в большинстве комнат
        val threshold = clusterRooms.size / 3 // Слово должно встречаться хотя бы в 1/3 комнат
        val commonWords = wordFrequency.filter { it.value >= threshold }
            .keys
            .sortedByDescending { wordFrequency[it] }
            .take(2)

        return if (commonWords.isNotEmpty()) {
            commonWords.joinToString(" ").replaceFirstChar { it.uppercase() }
        } else {
            // Если общих слов нет, используем координаты центра кластера
            val centerX = clusterRooms.map { it.x }.average().toInt()
            val centerY = clusterRooms.map { it.y }.average().toInt()
            val centerZ = clusterRooms.first().z
            "Зона ($centerX, $centerY, $centerZ)"
        }
    }

    /**
     * Автоматически детектирует и присваивает зоны комнатам
     */
    fun detectAndAssignZones(rooms: Map<String, Room>): Map<String, Room> {
        val zoneAssignments = detectZones(rooms)

        return rooms.mapValues { (id, room) ->
            val zoneName = zoneAssignments[id] ?: ""
            room.copy(zone = zoneName)
        }
    }

    /**
     * Возвращает статистику по зонам
     */
    fun getZoneStatistics(rooms: Map<String, Room>): Map<String, Int> {
        return rooms.values
            .filter { it.zone.isNotEmpty() }
            .groupBy { it.zone }
            .mapValues { it.value.size }
            .toSortedMap()
    }
}
