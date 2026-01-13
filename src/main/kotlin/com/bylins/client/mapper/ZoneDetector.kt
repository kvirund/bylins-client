package com.bylins.client.mapper

/**
 * Автоматическая детекция зон на карте
 * Анализирует названия комнат и их связи для определения зон
 */
class ZoneDetector {

    /**
     * Детектирует зоны на основе названий комнат и их связей
     */
    fun detectZones(rooms: Map<String, Room>): Map<String, String> {
        if (rooms.isEmpty()) return emptyMap()

        val roomList = rooms.values.toList()
        val zoneAssignments = mutableMapOf<String, String>()

        // 1. Группируем комнаты по общим словам в названиях
        val roomsByCommonWords = groupByCommonWords(roomList)

        // 2. Применяем кластеризацию по связям (BFS)
        val clusters = graphClustering(rooms, maxDepth = 10)

        // 3. Объединяем результаты
        for ((clusterId, clusterRooms) in clusters.withIndex()) {
            val zoneName = determineZoneName(clusterRooms, roomsByCommonWords, clusterId)
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
     * Кластеризация комнат по графу связей (BFS)
     * Группирует связанные комнаты в кластеры
     */
    private fun graphClustering(rooms: Map<String, Room>, maxDepth: Int): List<List<Room>> {
        val visited = mutableSetOf<String>()
        val clusters = mutableListOf<List<Room>>()

        for (room in rooms.values) {
            if (room.id in visited) continue

            val cluster = mutableListOf<Room>()
            val queue = ArrayDeque<Pair<Room, Int>>() // Room + depth
            queue.add(room to 0)

            while (queue.isNotEmpty()) {
                val (current, depth) = queue.removeFirst()
                if (current.id in visited) continue
                if (depth > maxDepth) continue

                visited.add(current.id)
                cluster.add(current)

                // Находим соседние комнаты через выходы
                for ((_, exit) in current.exits) {
                    if (exit.targetRoomId.isNotEmpty() && exit.targetRoomId !in visited) {
                        val neighbor = rooms[exit.targetRoomId]
                        if (neighbor != null) {
                            queue.add(neighbor to depth + 1)
                        }
                    }
                }
            }

            if (cluster.isNotEmpty()) {
                clusters.add(cluster)
            }
        }

        return clusters
    }

    /**
     * Определяет название зоны на основе комнат в кластере
     */
    private fun determineZoneName(
        clusterRooms: List<Room>,
        wordGroups: Map<Set<String>, List<Room>>,
        clusterId: Int
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
        val threshold = maxOf(1, clusterRooms.size / 3) // Слово должно встречаться хотя бы в 1/3 комнат
        val commonWords = wordFrequency.filter { it.value >= threshold }
            .keys
            .sortedByDescending { wordFrequency[it] }
            .take(2)

        return if (commonWords.isNotEmpty()) {
            commonWords.joinToString(" ").replaceFirstChar { it.uppercase() }
        } else {
            // Если общих слов нет, генерируем имя по ID кластера
            "Зона ${clusterId + 1}"
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
