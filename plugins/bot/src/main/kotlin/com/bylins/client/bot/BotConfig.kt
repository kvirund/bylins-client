package com.bylins.client.bot

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Конфигурация AI-бота
 */
@Serializable
data class BotConfig(
    var verboseLogging: Boolean = false
) {
    fun toJson(): String = Json.encodeToString(serializer(), this)

    companion object {
        fun fromJson(json: String): BotConfig = Json.decodeFromString(serializer(), json)
    }
}
