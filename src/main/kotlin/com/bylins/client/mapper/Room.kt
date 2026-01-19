package com.bylins.client.mapper

import mu.KotlinLogging
import kotlinx.serialization.Serializable

/**
 * Представляет комнату в MUD
 */
private val logger = KotlinLogging.logger("Room")
@Serializable
data class Room(
    val id: String,
    val name: String,
    val description: String = "",
    val exits: MutableMap<Direction, Exit> = mutableMapOf(),
    val terrain: String? = null,
    val zone: String? = null,
    val notes: String = "",
    val color: String? = null,
    val visited: Boolean = false,
    val tags: Set<String> = emptySet()  // Теги для группировки комнат (магазины, тренеры, квесты и т.д.)
) {
    /**
     * Добавляет выход в указанном направлении
     * Защита от самоссылок - выход на саму себя игнорируется
     */
    fun addExit(direction: Direction, targetRoomId: String) {
        // Не допускаем самоссылающиеся выходы (баг защита)
        if (targetRoomId == id) {
            logger.warn { "WARNING: Ignoring self-referencing exit $direction for room $id" }
            return
        }
        exits[direction] = Exit(targetRoomId)
    }

    /**
     * Добавляет неизведанный выход (знаем что есть, но не знаем куда ведёт)
     */
    fun addUnexploredExit(direction: Direction) {
        val existingExit = exits[direction]
        if (existingExit == null) {
            // Выхода нет - добавляем
            exits[direction] = Exit("")
            logger.info { "Added unexplored exit: $direction to room $id" }
        } else if (existingExit.targetRoomId == id) {
            // Самоссылающийся выход (баг) - исправляем
            exits[direction] = Exit("")
            logger.info { "Fixed self-referencing exit $direction in room $id" }
        } else if (existingExit.targetRoomId.isNotEmpty()) {
            // Нормальный изведанный выход - не трогаем
            logger.info { "Skipped exit $direction - already explored in room $id" }
        }
        // Если targetRoomId пустой - это уже неизведанный выход, ничего не делаем
    }

    /**
     * Проверяет изведан ли выход (знаем ли куда ведёт)
     */
    fun isExitExplored(direction: Direction): Boolean {
        val exit = exits[direction] ?: return false
        return exit.targetRoomId.isNotEmpty()
    }

    /**
     * Удаляет выход в указанном направлении
     */
    fun removeExit(direction: Direction) {
        exits.remove(direction)
    }

    /**
     * Получает выход в указанном направлении
     */
    fun getExit(direction: Direction): Exit? {
        return exits[direction]
    }

    /**
     * Проверяет есть ли выход в указанном направлении
     */
    fun hasExit(direction: Direction): Boolean {
        return exits.containsKey(direction)
    }

    /**
     * Возвращает список доступных направлений
     */
    fun getAvailableDirections(): List<Direction> {
        return exits.keys.toList()
    }

    /**
     * Конвертирует комнату в Map для передачи плагинам
     */
    fun toMap(): Map<String, Any> {
        // Exits as map: {direction_name: targetRoomId}
        val exitsMap = exits.entries.associate { (dir, exit) ->
            dir.name.lowercase() to exit.targetRoomId
        }
        return mapOf(
            "id" to id,
            "name" to name,
            "description" to description,
            "exits" to exitsMap,
            "terrain" to (terrain ?: ""),
            "zone" to (zone ?: ""),
            "notes" to notes,
            "color" to (color ?: ""),
            "visited" to visited,
            "tags" to tags.toList()
        )
    }
}

/**
 * Представляет выход из комнаты
 */
@Serializable
data class Exit(
    val targetRoomId: String,
    val door: String? = null
)
