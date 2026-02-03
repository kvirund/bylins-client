package com.bylins.client.plugins.events

/**
 * Базовый интерфейс для всех событий плагинов.
 */
sealed interface PluginEvent

/**
 * Приоритет обработки события.
 * Обработчики вызываются в порядке от LOWEST до HIGHEST.
 * MONITOR вызывается последним и не может отменять события.
 */
enum class EventPriority {
    LOWEST,
    LOW,
    NORMAL,
    HIGH,
    HIGHEST,
    /** Только для наблюдения, нельзя отменять события */
    MONITOR
}

/**
 * Событие, которое можно отменить.
 */
interface CancellableEvent {
    /** Флаг отмены события */
    var isCancelled: Boolean
}

// ============================================
// События подключения
// ============================================

/**
 * Событие подключения к серверу.
 */
data class ConnectEvent(
    val host: String,
    val port: Int
) : PluginEvent

/**
 * Событие отключения от сервера.
 */
data class DisconnectEvent(
    val reason: DisconnectReason
) : PluginEvent

/**
 * Причина отключения.
 */
enum class DisconnectReason {
    /** Пользователь запросил отключение */
    USER_REQUEST,
    /** Сервер закрыл соединение */
    SERVER_CLOSED,
    /** Ошибка сети */
    ERROR,
    /** Таймаут соединения */
    TIMEOUT
}

// ============================================
// События ввода/вывода
// ============================================

/**
 * Событие получения строки от сервера.
 * Можно отменить (строка не будет показана) или модифицировать.
 */
data class LineReceivedEvent(
    /** Очищенная строка (без ANSI кодов) */
    val line: String,
    /** Оригинальная строка с ANSI кодами */
    val rawLine: String,
    /** Timestamp получения строки (для определения промпта по интервалам) */
    val timestamp: Long = System.currentTimeMillis()
) : PluginEvent, CancellableEvent {
    override var isCancelled: Boolean = false

    /** Изменённая строка (если null - используется оригинал) */
    var modifiedLine: String? = null
}

/**
 * Событие отправки команды.
 * Можно отменить (команда не будет отправлена) или модифицировать.
 */
data class CommandSendEvent(
    val command: String
) : PluginEvent, CancellableEvent {
    override var isCancelled: Boolean = false

    /** Изменённая команда (если null - используется оригинал) */
    var modifiedCommand: String? = null
}

// ============================================
// События протоколов
// ============================================

/**
 * Событие включения MSDP протокола.
 * Вызывается после согласования протокола с сервером.
 */
object MsdpEnabledEvent : PluginEvent

/**
 * Событие получения MSDP данных.
 */
data class MsdpEvent(
    /** Имя переменной */
    val variable: String,
    /** Значение (может быть String, Map или List) */
    val value: Any
) : PluginEvent

/**
 * Событие получения списка reportable переменных MSDP.
 */
data class MsdpReportableVariablesEvent(
    /** Список доступных переменных */
    val variables: List<String>
) : PluginEvent

/**
 * Событие получения GMCP данных.
 */
data class GmcpEvent(
    /** Имя пакета (например, "Char.Vitals") */
    val packageName: String,
    /** JSON данные */
    val data: String
) : PluginEvent

// ============================================
// События маппера
// ============================================

/**
 * Событие входа в комнату.
 */
data class RoomEnterEvent(
    /** ID комнаты */
    val roomId: String,
    /** Название комнаты */
    val roomName: String,
    /** Направление, откуда пришли (null если телепорт) */
    val fromDirection: String?
) : PluginEvent

// ============================================
// События автоматизации
// ============================================

/**
 * Событие срабатывания триггера.
 */
data class TriggerFiredEvent(
    /** ID триггера */
    val triggerId: String,
    /** Название триггера */
    val triggerName: String,
    /** Строка, вызвавшая срабатывание */
    val line: String,
    /** Группы из regex */
    val groups: List<String>
) : PluginEvent

/**
 * Событие срабатывания алиаса.
 */
data class AliasFiredEvent(
    /** ID алиаса */
    val aliasId: String,
    /** Название алиаса */
    val aliasName: String,
    /** Введённая команда */
    val input: String,
    /** Группы из regex */
    val groups: List<String>
) : PluginEvent
