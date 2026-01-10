package com.bylins.client.mapper

/**
 * Парсит информацию о комнатах из текста MUD
 */
class RoomParser {
    // Паттерны для парсинга
    private val exitsPattern = Regex("Выходы?:\\s*(.+)", RegexOption.IGNORE_CASE)
    private val roomNamePattern = Regex("^\\s*([А-Яа-яA-Za-z0-9\\s\\-,:;]+)\\s*$")

    /**
     * Парсит выходы из строки
     * Примеры:
     * "Выходы: север, юг, восток"
     * "Exits: north, south, east"
     */
    fun parseExits(text: String): List<Direction> {
        val match = exitsPattern.find(text) ?: return emptyList()
        val exitsText = match.groupValues[1]

        val directions = mutableListOf<Direction>()

        // Пробуем найти направления в тексте
        for (direction in Direction.values()) {
            for (command in direction.commands) {
                if (exitsText.lowercase().contains(command.lowercase())) {
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

        // Проверяем что это похоже на название
        if (roomNamePattern.matches(trimmed)) {
            return trimmed
        }

        return null
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
        val terrain = data["ROOM_TERRAIN"] as? String ?: ""
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
