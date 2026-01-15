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
) {
    // Для обратной совместимости - старое поле type
    @Deprecated("Use triggerType and scope instead", ReplaceWith("triggerType"))
    val type: ContextRuleType
        get() = when (triggerType) {
            is ContextTriggerType.Pattern -> ContextRuleType.Pattern(triggerType.regex)
            is ContextTriggerType.Permanent -> when (scope) {
                is ContextScope.Room -> ContextRuleType.Room(scope.roomIds)
                is ContextScope.Zone -> ContextRuleType.Zone(scope.zones)
                is ContextScope.World -> ContextRuleType.Pattern(".*".toRegex()) // Fallback
            }
        }
}

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
     * Действует только в указанных комнатах
     */
    data class Room(val roomIds: Set<String>) : ContextScope()

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
 * Старый тип правила (deprecated, для обратной совместимости)
 */
@Deprecated("Use ContextTriggerType and ContextScope instead")
sealed class ContextRuleType {
    data class Pattern(val regex: Regex) : ContextRuleType()
    data class Room(val roomIds: Set<String>) : ContextRuleType()
    data class Zone(val zones: Set<String>) : ContextRuleType()
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
