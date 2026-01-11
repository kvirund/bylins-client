package com.bylins.client.mapper

/**
 * Парсит информацию о комнатах из текста MUD
 */
class RoomParser {
    // Паттерны для парсинга
    private val exitsPatterns = listOf(
        Regex("Выходы?:\\s*(.+)", RegexOption.IGNORE_CASE),
        Regex("Exits?:\\s*(.+)", RegexOption.IGNORE_CASE),
        Regex("\\[Exits?:\\s*(.+)\\]", RegexOption.IGNORE_CASE),
        Regex("Obvious exits?:\\s*(.+)", RegexOption.IGNORE_CASE)
    )
    private val roomNamePattern = Regex("^\\s*([А-Яа-яA-Za-z0-9\\s\\-,:;.()]+)\\s*$")

    // Паттерны для определения типа местности
    private val terrainPatterns = mapOf(
        "city" to listOf("город", "улица", "площадь", "дом"),
        "forest" to listOf("лес", "роща", "деревья", "поляна"),
        "water" to listOf("река", "озеро", "море", "вода", "берег"),
        "mountain" to listOf("гора", "скала", "утес", "вершина"),
        "dungeon" to listOf("пещера", "подземелье", "туннель", "коридор"),
        "road" to listOf("дорога", "тракт", "путь", "тропа"),
        "indoor" to listOf("комната", "зал", "палата", "камера")
    )

    /**
     * Парсит выходы из строки
     * Примеры:
     * "Выходы: север, юг, восток"
     * "Exits: north, south, east"
     * "[Exits: n s e w]"
     */
    fun parseExits(text: String): List<Direction> {
        // Пробуем все паттерны
        for (pattern in exitsPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val exitsText = match.groupValues[1]
                return parseDirectionsFromText(exitsText)
            }
        }

        return emptyList()
    }

    /**
     * Извлекает направления из текста
     */
    private fun parseDirectionsFromText(text: String): List<Direction> {
        val directions = mutableListOf<Direction>()

        // Пробуем найти направления в тексте
        for (direction in Direction.values()) {
            for (command in direction.commands) {
                if (text.lowercase().contains(command.lowercase())) {
                    if (!directions.contains(direction)) {
                        directions.add(direction)
                    }
                }
            }
        }

        return directions
    }

    /**
     * Парсит название комнаты
     */
    fun parseRoomName(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // Название комнаты обычно короткое (до 100 символов)
        if (trimmed.length > 100) return null

        // Пропускаем строки, которые явно не являются названиями
        if (trimmed.startsWith("Выход") ||
            trimmed.startsWith("Exit") ||
            trimmed.startsWith("[") ||
            trimmed.contains(":") && trimmed.length < 20) {
            return null
        }

        // Проверяем что это похоже на название
        if (roomNamePattern.matches(trimmed)) {
            return trimmed
        }

        return null
    }

    /**
     * Парсит описание комнаты (обычно несколько строк после названия)
     */
    fun parseRoomDescription(lines: List<String>): String {
        val description = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()

            // Пропускаем пустые строки
            if (trimmed.isEmpty()) continue

            // Останавливаемся на строке с выходами
            if (isExitsLine(trimmed)) break

            // Добавляем строку к описанию
            if (description.isNotEmpty()) {
                description.append(" ")
            }
            description.append(trimmed)
        }

        return description.toString()
    }

    /**
     * Проверяет, является ли строка строкой с выходами
     */
    private fun isExitsLine(text: String): Boolean {
        for (pattern in exitsPatterns) {
            if (pattern.find(text) != null) {
                return true
            }
        }
        return false
    }

    /**
     * Определяет тип местности по названию и описанию комнаты
     */
    fun detectTerrain(roomName: String, description: String = ""): String {
        val text = "$roomName $description".lowercase()

        for ((terrain, keywords) in terrainPatterns) {
            for (keyword in keywords) {
                if (text.contains(keyword)) {
                    return terrain
                }
            }
        }

        return "unknown"
    }

    /**
     * Парсит информацию о комнате из блока текста
     * Предполагается что текст содержит:
     * 1. Название комнаты (первая строка)
     * 2. Описание комнаты (несколько строк)
     * 3. Выходы (строка с "Выходы:")
     */
    fun parseFromText(text: String): RoomInfo? {
        val lines = text.split("\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        // Первая строка - название комнаты
        val roomName = parseRoomName(lines[0]) ?: return null

        // Ищем строку с выходами
        var exitsLine: String? = null
        val descriptionLines = mutableListOf<String>()

        for (i in 1 until lines.size) {
            val line = lines[i]
            if (isExitsLine(line)) {
                exitsLine = line
                break
            } else {
                descriptionLines.add(line)
            }
        }

        // Парсим описание
        val description = parseRoomDescription(descriptionLines)

        // Парсим выходы
        val exits = if (exitsLine != null) {
            parseExits(exitsLine)
        } else {
            emptyList()
        }

        // Определяем тип местности
        val terrain = detectTerrain(roomName, description)

        return RoomInfo(
            name = roomName,
            exits = exits,
            terrain = terrain,
            description = description
        )
    }

    /**
     * Парсит информацию о комнате из MSDP данных
     */
    fun parseFromMSDP(data: Map<String, Any>): RoomInfo? {
        val roomName = data["ROOM_NAME"] as? String ?: return null
        val roomVnum = data["ROOM_VNUM"] as? String

        // Парсим выходы из MSDP
        val exitsData = data["ROOM_EXITS"] as? String ?: ""
        val exits = parseExits("Выходы: $exitsData")

        // Дополнительная информация
        val terrainFromMSDP = data["ROOM_TERRAIN"] as? String ?: ""
        val terrain = if (terrainFromMSDP.isNotEmpty()) {
            terrainFromMSDP
        } else {
            detectTerrain(roomName)
        }
        val zone = data["AREA_NAME"] as? String ?: ""

        return RoomInfo(
            name = roomName,
            vnum = roomVnum,
            exits = exits,
            terrain = terrain,
            zone = zone
        )
    }

    /**
     * Определяет направление из последней команды движения
     */
    fun detectMovementDirection(command: String): Direction? {
        return Direction.fromCommand(command)
    }
}

/**
 * Информация о комнате распарсенная из текста
 */
data class RoomInfo(
    val name: String,
    val vnum: String? = null,
    val exits: List<Direction> = emptyList(),
    val terrain: String = "",
    val zone: String = "",
    val description: String = ""
)
