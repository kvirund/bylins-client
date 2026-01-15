package com.bylins.client.contextcommands

import java.util.UUID

/**
 * Правило для автоматического добавления контекстных команд
 *
 * Структура:
 * - triggerType: как правило срабатывает (Pattern = по тексту, Permanent = всегда в области)
 * - scope: где правило действует (Room/Zone/World)
 * - ttl: время жизни команды (только для Pattern правил)
 */
data class ContextCommandRule(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val triggerType: ContextTriggerType,
    val scope: ContextScope = ContextScope.World,
    val command: String,  // Поддерживает $1, $2 для capture groups паттерна
    val ttl: ContextCommandTTL = ContextCommandTTL.UntilRoomChange,  // Только для Pattern
    val priority: Int = 0  // Выше = добавляется раньше (будет свежее)
)

/**
 * Тип триггера: как правило срабатывает
 */
sealed class ContextTriggerType {
    /**
     * Срабатывает при совпадении паттерна в сообщении от сервера
     */
    data class Pattern(val regex: Regex) : ContextTriggerType()

    /**
     * Всегда активно в указанной области (Room/Zone)
     * Команда добавляется при входе, удаляется при выходе
     */
    object Permanent : ContextTriggerType()
}

/**
 * Область действия правила
 */
sealed class ContextScope {
    /**
     * Действует только в указанных комнатах (по ID или тегам)
     */
    data class Room(
        val roomIds: Set<String> = emptySet(),
        val roomTags: Set<String> = emptySet()
    ) : ContextScope() {
        /**
         * Проверяет, соответствует ли комната этому scope
         */
        fun matches(roomId: String, tags: Collection<String>): Boolean {
            if (roomIds.contains(roomId)) return true
            if (roomTags.isNotEmpty() && tags.any { roomTags.contains(it) }) return true
            return false
        }
    }

    /**
     * Действует только в указанных зонах
     */
    data class Zone(val zones: Set<String>) : ContextScope()

    /**
     * Действует везде (глобально)
     */
    object World : ContextScope()
}

/**
 * Применяет capture groups к шаблону команды
 * $0 - полное совпадение, $1, $2, ... - группы
 */
fun ContextCommandRule.applyGroups(matchResult: MatchResult): String {
    var result = command
    // Заменяем $0 на полное совпадение
    result = result.replace("\$0", matchResult.value)
    // Заменяем $1, $2, ... на соответствующие группы
    matchResult.groupValues.forEachIndexed { index, value ->
        if (index > 0) {
            result = result.replace("\$$index", value)
        }
    }
    return result
}
