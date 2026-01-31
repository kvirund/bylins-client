package com.bylins.client.plugins

import com.bylins.client.plugins.events.PluginEvent
import com.bylins.client.plugins.events.EventPriority
import com.bylins.client.plugins.scripting.ScriptEvent
import com.bylins.client.plugins.ui.PluginTab

/**
 * Типизированный API для плагинов.
 *
 * Предоставляет доступ ко всем функциям клиента:
 * - Отправка команд и вывод текста
 * - Создание триггеров и алиасов
 * - Управление таймерами
 * - Доступ к переменным, MSDP, GMCP
 * - Подписка на события
 */
interface PluginAPI {

    // ============================================
    // Отправка и вывод
    // ============================================

    /**
     * Отправляет команду на сервер.
     */
    fun send(command: String)

    /**
     * Отправляет несколько команд на сервер.
     */
    fun send(vararg commands: String)

    /**
     * Выводит текст в окно клиента (не отправляется на сервер).
     */
    fun echo(text: String)

    /**
     * Выводит текст с указанным цветом (ANSI или hex).
     */
    fun echo(text: String, color: String)

    // ============================================
    // Триггеры
    // ============================================

    /**
     * Создает временный триггер (существует пока плагин включен).
     *
     * @param pattern Regex паттерн для срабатывания
     * @param priority Приоритет (выше = раньше обрабатывается)
     * @param handler Обработчик срабатывания
     * @return Хэндл для управления триггером
     */
    fun createTrigger(
        pattern: Regex,
        priority: Int = 0,
        handler: TriggerHandler
    ): TriggerHandle

    /**
     * Создает временный триггер со строковым паттерном.
     */
    fun createTrigger(
        pattern: String,
        priority: Int = 0,
        handler: TriggerHandler
    ): TriggerHandle = createTrigger(Regex(pattern), priority, handler)

    /**
     * Создает триггер с доступом к raw строке (с ANSI кодами).
     */
    fun createRawTrigger(
        pattern: Regex,
        priority: Int = 0,
        handler: RawTriggerHandler
    ): TriggerHandle

    /**
     * Удаляет триггер по хэндлу.
     */
    fun removeTrigger(handle: TriggerHandle)

    // ============================================
    // Алиасы
    // ============================================

    /**
     * Создает временный алиас.
     *
     * @param pattern Regex паттерн для срабатывания
     * @param priority Приоритет
     * @param handler Обработчик
     * @return Хэндл для управления алиасом
     */
    fun createAlias(
        pattern: Regex,
        priority: Int = 0,
        handler: AliasHandler
    ): AliasHandle

    /**
     * Создает временный алиас со строковым паттерном.
     */
    fun createAlias(
        pattern: String,
        priority: Int = 0,
        handler: AliasHandler
    ): AliasHandle = createAlias(Regex(pattern), priority, handler)

    /**
     * Удаляет алиас.
     */
    fun removeAlias(handle: AliasHandle)

    // ============================================
    // Таймеры
    // ============================================

    /**
     * Создает одноразовый таймер.
     *
     * @param delayMs Задержка в миллисекундах
     * @param callback Функция для вызова
     * @return Хэндл для отмены таймера
     */
    fun setTimeout(delayMs: Long, callback: () -> Unit): TimerHandle

    /**
     * Создает периодический таймер.
     *
     * @param intervalMs Интервал в миллисекундах
     * @param callback Функция для вызова
     * @return Хэндл для отмены таймера
     */
    fun setInterval(intervalMs: Long, callback: () -> Unit): TimerHandle

    /**
     * Отменяет таймер.
     */
    fun cancelTimer(handle: TimerHandle)

    // ============================================
    // Переменные
    // ============================================

    /**
     * Получает значение переменной.
     */
    fun getVariable(name: String): String?

    /**
     * Устанавливает переменную.
     */
    fun setVariable(name: String, value: String)

    /**
     * Удаляет переменную.
     */
    fun deleteVariable(name: String)

    /**
     * Получает все переменные.
     */
    fun getAllVariables(): Map<String, String>

    // ============================================
    // MSDP
    // ============================================

    /**
     * Получает MSDP значение.
     */
    fun getMsdpValue(key: String): Any?

    /**
     * Получает MSDP значение с типизацией.
     */
    fun <T> getMsdpValue(key: String, type: Class<T>): T?

    /**
     * Получает все MSDP данные.
     */
    fun getAllMsdpData(): Map<String, Any>

    // ============================================
    // GMCP
    // ============================================

    /**
     * Получает GMCP пакет.
     */
    fun getGmcpPackage(packageName: String): String?

    /**
     * Получает все GMCP данные.
     */
    fun getAllGmcpData(): Map<String, String>

    /**
     * Отправляет GMCP данные серверу.
     */
    fun sendGmcp(packageName: String, data: String)

    // ============================================
    // Маппер - чтение
    // ============================================

    /**
     * Получает информацию о текущей комнате.
     */
    fun getCurrentRoom(): Map<String, Any>?

    /**
     * Получает комнату по ID.
     */
    fun getRoom(roomId: String): Map<String, Any>?

    /**
     * Ищет комнаты по названию.
     */
    fun searchRooms(query: String): List<Map<String, Any>>

    /**
     * Находит путь от текущей комнаты до указанной.
     * @return Список направлений (север, юг, ...) или null если путь не найден
     */
    fun findPath(targetRoomId: String): List<String>?

    // ============================================
    // Маппер - модификация
    // ============================================

    /**
     * Устанавливает заметку для комнаты.
     */
    fun setRoomNote(roomId: String, note: String)

    /**
     * Устанавливает цвет комнаты.
     */
    fun setRoomColor(roomId: String, color: String?)

    /**
     * Устанавливает зону комнаты.
     */
    fun setRoomZone(roomId: String, zone: String)

    /**
     * Устанавливает теги комнаты.
     */
    fun setRoomTags(roomId: String, tags: List<String>)

    // ============================================
    // Маппер - создание
    // ============================================

    /**
     * Создаёт новую комнату.
     * @return true если комната создана, false если уже существует
     */
    fun createRoom(id: String, name: String): Boolean

    /**
     * Создаёт новую комнату с выходами.
     * @param exits Карта направлений к ID целевых комнат (например, mapOf("север" to "room_2"))
     */
    fun createRoomWithExits(id: String, name: String, exits: Map<String, String>): Boolean

    /**
     * Связывает две комнаты выходом.
     * Создаёт двусторонний выход.
     */
    fun linkRooms(fromRoomId: String, direction: String, toRoomId: String)

    /**
     * Обрабатывает движение персонажа.
     * Создаёт или обновляет комнату на основе направления движения.
     * @param direction Направление движения (север, юг, ...)
     * @param roomName Название новой комнаты
     * @param exits Список выходов из новой комнаты
     * @return Информация о комнате или null при ошибке
     */
    fun handleMovement(direction: String, roomName: String, exits: List<String>): Map<String, Any>?

    // ============================================
    // Маппер - управление
    // ============================================

    /**
     * Включает/выключает автоматический маппинг.
     */
    fun setMapEnabled(enabled: Boolean)

    /**
     * Проверяет, включён ли маппинг.
     */
    fun isMapEnabled(): Boolean

    /**
     * Очищает карту.
     */
    fun clearMap()

    /**
     * Устанавливает текущую комнату по ID.
     */
    fun setCurrentRoom(roomId: String)

    // ============================================
    // UI - вкладки
    // ============================================

    /**
     * Создаёт вкладку плагина в интерфейсе клиента.
     * Плагин может обновлять содержимое вкладки через PluginTab.content.
     *
     * @param id Уникальный идентификатор вкладки (рекомендуется pluginId + суффикс)
     * @param title Заголовок вкладки для отображения в UI
     * @return PluginTab для управления содержимым вкладки
     */
    fun createTab(id: String, title: String): PluginTab

    /**
     * Закрывает вкладку по ID.
     */
    fun closeTab(id: String)

    /**
     * Получает существующую вкладку по ID (если есть).
     */
    fun getTab(id: String): PluginTab?

    // ============================================
    // Вкладки вывода (текстовые, как главный вывод)
    // ============================================

    /**
     * Создаёт текстовую вкладку вывода на панели "Вывод".
     * В отличие от createTab(), это простая текстовая вкладка без UI элементов.
     *
     * @param id Уникальный идентификатор вкладки
     * @param title Заголовок вкладки
     * @return true если вкладка создана, false если уже существует
     */
    fun createOutputTab(id: String, title: String): Boolean

    /**
     * Добавляет текст в вкладку вывода.
     *
     * @param id Идентификатор вкладки
     * @param text Текст для добавления
     */
    fun appendToOutputTab(id: String, text: String)

    /**
     * Закрывает вкладку вывода.
     *
     * @param id Идентификатор вкладки
     */
    fun closeOutputTab(id: String)

    // ============================================
    // Маппер - поиск с callback
    // ============================================

    /**
     * Ищет комнаты с произвольным фильтром.
     *
     * @param predicate Функция-фильтр для комнат
     * @param maxResults Максимальное количество результатов
     * @return Список комнат, удовлетворяющих условию
     */
    fun findRoomsMatching(
        predicate: (Map<String, Any>) -> Boolean,
        maxResults: Int = 100
    ): List<Map<String, Any>>

    /**
     * Находит ближайшую комнату, удовлетворяющую условию.
     *
     * @param predicate Функция-фильтр для комнат
     * @return Пара (комната, путь) или null если не найдено
     */
    fun findNearestMatching(
        predicate: (Map<String, Any>) -> Boolean
    ): Pair<Map<String, Any>, List<String>>?

    // ============================================
    // События
    // ============================================

    /**
     * Подписывается на событие.
     *
     * @param eventClass Класс события
     * @param priority Приоритет обработки
     * @param handler Обработчик события
     * @return Подписка для отписки
     */
    fun <T : PluginEvent> subscribe(
        eventClass: Class<T>,
        priority: EventPriority = EventPriority.NORMAL,
        handler: EventHandler<T>
    ): EventSubscription

    /**
     * Отписывается от события.
     */
    fun unsubscribe(subscription: EventSubscription)

    // ============================================
    // Конфигурация плагина
    // ============================================

    /**
     * Сохраняет конфигурацию плагина.
     */
    fun saveConfig(config: Any)

    /**
     * Загружает конфигурацию плагина.
     */
    fun <T> loadConfig(type: Class<T>): T?

    // ============================================
    // Взаимодействие с другими плагинами
    // ============================================

    /**
     * Получает API другого плагина (если он экспортирует публичный API).
     */
    fun <T> getPluginApi(pluginId: String, apiClass: Class<T>): T?

    /**
     * Проверяет загружен ли плагин.
     */
    fun isPluginLoaded(pluginId: String): Boolean

    // ============================================
    // События скриптов
    // ============================================

    /**
     * Отправить событие в систему скриптов клиента.
     * Позволяет плагинам (например, боту) уведомлять скрипты о событиях.
     *
     * @param event Тип события
     * @param data Данные события (любой объект, будет передан обработчику)
     */
    fun fireScriptEvent(event: ScriptEvent, data: Any?)

    // ============================================
    // Панель статуса
    // ============================================

    /**
     * Добавляет прогресс-бар на панель статуса.
     */
    fun addStatusBar(
        id: String,
        label: String,
        value: Int,
        max: Int,
        color: String = "green",
        showText: Boolean = true,
        showMax: Boolean = true,
        order: Int = 0
    )

    /**
     * Добавляет текстовый элемент на панель статуса.
     */
    fun addStatusText(
        id: String,
        label: String,
        value: String? = null,
        color: String? = null,
        bold: Boolean = false,
        background: String? = null,
        order: Int = 0
    )

    /**
     * Добавляет значение с модификатором на панель статуса.
     * Пример: "Сила: 18 (15+3)" где value=18, base=15, modifier=3
     */
    fun addStatusModifiedValue(
        id: String,
        label: String,
        value: Int,
        base: Int? = null,
        modifier: Int? = null,
        color: String? = null,
        order: Int = 0
    )

    /**
     * Добавляет группу элементов на панель статуса.
     */
    fun addStatusGroup(
        id: String,
        label: String,
        collapsed: Boolean = false,
        order: Int = 0,
        builder: StatusGroupBuilder.() -> Unit
    )

    /**
     * Удаляет элемент с панели статуса.
     */
    fun removeStatus(id: String)

    /**
     * Очищает панель статуса.
     */
    fun clearStatus()
}

// ============================================
// Вспомогательные типы
// ============================================

/**
 * Контекст срабатывания триггера.
 * Содержит информацию о строке, вызвавшей срабатывание.
 */
data class TriggerContext(
    /** Очищенная строка (без ANSI кодов) */
    val line: String,
    /** Оригинальная строка с ANSI кодами */
    val rawLine: String,
    /** Группы из regex (группа 0 = вся строка) */
    val groups: List<String>
)

/**
 * Обработчик триггера.
 */
fun interface TriggerHandler {
    /**
     * @param line Строка, которая вызвала срабатывание (без ANSI)
     * @param groups Группы из regex (группа 0 = вся строка)
     * @return Результат обработки
     */
    fun handle(line: String, groups: List<String>): TriggerResult
}

/**
 * Расширенный обработчик триггера с доступом к raw строке.
 */
fun interface RawTriggerHandler {
    /**
     * @param context Контекст с обеими версиями строки и группами
     * @return Результат обработки
     */
    fun handle(context: TriggerContext): TriggerResult
}

/**
 * Результат обработки триггера.
 */
enum class TriggerResult {
    /** Продолжить обработку другими триггерами */
    CONTINUE,

    /** Остановить обработку (другие триггеры не сработают) */
    STOP,

    /** Скрыть строку (gag) и остановить обработку */
    GAG
}

/**
 * Обработчик алиаса.
 */
fun interface AliasHandler {
    /**
     * @param input Введённая команда
     * @param groups Группы из regex
     * @return true если алиас обработан (команда не отправляется на сервер)
     */
    fun handle(input: String, groups: List<String>): Boolean
}

/**
 * Обработчик события.
 */
fun interface EventHandler<T : PluginEvent> {
    fun handle(event: T)
}

// ============================================
// Хэндлы для управления созданными объектами
// ============================================

/** Хэндл триггера для управления им */
class TriggerHandle internal constructor(internal val id: String)

/** Хэндл алиаса для управления им */
class AliasHandle internal constructor(internal val id: String)

/** Хэндл таймера для отмены */
class TimerHandle internal constructor(internal val id: String)

/** Подписка на событие для отписки */
class EventSubscription internal constructor(internal val id: String)

// ============================================
// Панель статуса - билдеры
// ============================================

/**
 * Билдер для группы элементов статуса.
 */
interface StatusGroupBuilder {
    /**
     * Добавляет прогресс-бар в группу.
     */
    fun bar(
        id: String,
        label: String,
        value: Int,
        max: Int,
        color: String = "green",
        showText: Boolean = true,
        showMax: Boolean = true,
        order: Int = 0
    )

    /**
     * Добавляет текстовый элемент в группу.
     */
    fun text(
        id: String,
        label: String,
        value: String? = null,
        color: String? = null,
        bold: Boolean = false,
        order: Int = 0
    )

    /**
     * Добавляет значение с модификатором в группу.
     */
    fun modifiedValue(
        id: String,
        label: String,
        value: Int,
        base: Int? = null,
        modifier: Int? = null,
        color: String? = null,
        order: Int = 0
    )
}
