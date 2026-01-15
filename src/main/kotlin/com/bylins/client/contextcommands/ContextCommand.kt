package com.bylins.client.contextcommands

import java.util.UUID

/**
 * Активная контекстная команда в очереди
 */
data class ContextCommand(
    val id: String = UUID.randomUUID().toString(),
    val command: String,
    val source: ContextCommandSource,
    val addedAt: Long = System.currentTimeMillis(),
    val ttl: ContextCommandTTL,
    val roomIdWhenAdded: String? = null,
    val zoneWhenAdded: String? = null
)

/**
 * Источник контекстной команды
 */
sealed class ContextCommandSource {
    /**
     * Команда добавлена по паттерну из сообщения сервера
     */
    data class Pattern(
        val ruleId: String,
        val matchedText: String
    ) : ContextCommandSource()

    /**
     * Команда добавлена при входе в комнату
     */
    data class RoomBased(
        val ruleId: String,
        val roomId: String
    ) : ContextCommandSource()

    /**
     * Команда добавлена при входе в зону
     */
    data class ZoneBased(
        val ruleId: String,
        val zone: String
    ) : ContextCommandSource()

    /**
     * Команда добавлена вручную (через скрипт или API)
     */
    data class Manual(
        val description: String = ""
    ) : ContextCommandSource()
}

/**
 * Время жизни контекстной команды
 */
sealed class ContextCommandTTL {
    /**
     * Команда исчезает при смене комнаты
     */
    object UntilRoomChange : ContextCommandTTL()

    /**
     * Команда исчезает при смене зоны
     */
    object UntilZoneChange : ContextCommandTTL()

    /**
     * Команда исчезает через N минут
     */
    data class FixedTime(val minutes: Int) : ContextCommandTTL()

    /**
     * Команда не исчезает автоматически (только вручную или при переполнении очереди)
     */
    object Permanent : ContextCommandTTL()

    /**
     * Команда удаляется сразу после выполнения (одноразовая)
     */
    object OneTime : ContextCommandTTL()
}
