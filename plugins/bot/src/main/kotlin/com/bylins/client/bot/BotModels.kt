package com.bylins.client.bot

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Модели данных для бота
 */

/**
 * Позиция существа (для будущего использования)
 */
@Serializable
enum class Position {
    STANDING,
    SITTING,
    RESTING,
    SLEEPING,
    FIGHTING,
    STUNNED,
    DEAD,
    UNKNOWN
}

/**
 * Состояние персонажа (извлечённое из промпта)
 */
@Serializable
data class CharacterState(
    val hp: Int = 0,
    val maxHp: Int = 0,
    val move: Int = 0,
    val maxMove: Int = 0,
    val gold: Int = 0,
    val exits: String = ""
) {
    val hpPercent: Int get() = if (maxHp > 0) (hp * 100) / maxHp else 0
    val movePercent: Int get() = if (maxMove > 0) (move * 100) / maxMove else 0

    fun toJson(): String = Json.encodeToString(serializer(), this)

    companion object {
        fun fromJson(json: String): CharacterState = Json.decodeFromString(serializer(), json)
    }
}
