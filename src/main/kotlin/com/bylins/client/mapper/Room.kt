package com.bylins.client.mapper

import kotlinx.serialization.Serializable

/**
 * Представляет комнату в MUD
 */
@Serializable
data class Room(
    val id: String,
    val name: String,
    val description: String = "",
    val x: Int,
    val y: Int,
    val z: Int = 0,
    val exits: MutableMap<Direction, Exit> = mutableMapOf(),
    val terrain: String = "",
    val zone: String = "",
    val notes: String = "",
    val color: String? = null,
    val visited: Boolean = false,
    val tags: Set<String> = emptySet()  // Теги для группировки комнат (магазины, тренеры, квесты и т.д.)
) {
    /**
     * Добавляет выход в указанном направлении
     */
    fun addExit(direction: Direction, targetRoomId: String) {
        exits[direction] = Exit(targetRoomId)
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
}

/**
 * Представляет выход из комнаты
 */
@Serializable
data class Exit(
    val targetRoomId: String,
    val door: String? = null,
    val locked: Boolean = false,
    val hidden: Boolean = false,
    val oneWay: Boolean = false
)
