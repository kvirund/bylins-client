package com.bylins.client.plugins

/**
 * Слой управления клиентом: то же, что пользователь делает руками в UI —
 * профили подключения, соединение, триггеры/алиасы/хоткеи, вкладки вывода.
 *
 * Доступен через [PluginAPI.client]. Каждый вызов требует выданного разрешения
 * ([PluginPermission.CLIENT_CONTROL], для соединения — [PluginPermission.CONNECTION_CONTROL]),
 * иначе бросается [PluginPermissionDeniedException].
 *
 * Сущности отдаются как Map, чтобы не тащить внутренние типы клиента в плагины
 * (как уже сделано для комнат карты).
 */
interface ClientControl {

    // --- Соединение (требует CONNECTION_CONTROL) ---

    /** Подключиться: к профилю [profileId] или к текущему выбранному, если null. */
    fun connect(profileId: String? = null)

    /** Отключиться от сервера. */
    fun disconnect()

    /** Подключён ли клиент сейчас. */
    fun isConnected(): Boolean

    // --- Профили подключения (сервера) ---

    /** Список профилей подключения: id, name, host, port, encoding, mapFile, autoReconnect. */
    fun listConnectionProfiles(): List<Map<String, Any?>>

    /** Текущий профиль подключения (null, если не выбран). */
    fun getCurrentConnectionProfile(): Map<String, Any?>?

    /** Создаёт профиль подключения, возвращает его id. */
    fun createConnectionProfile(
        name: String,
        host: String,
        port: Int,
        encoding: String = "UTF-8",
        mapFile: String = "maps.db",
        autoReconnect: Boolean = false
    ): String

    /** Обновляет поля профиля (передавайте только изменяемые ключи). */
    fun updateConnectionProfile(id: String, changes: Map<String, Any?>): Boolean

    /** Удаляет профиль подключения. */
    fun deleteConnectionProfile(id: String): Boolean

    /** Делает профиль текущим (как выбор в выпадающем списке). */
    fun selectConnectionProfile(id: String): Boolean

    // --- Пользовательские триггеры (персистентные, видны в UI) ---

    /** Список триггеров: id, name, pattern, commands, enabled, gag, priority. */
    fun listTriggers(): List<Map<String, Any?>>

    /**
     * Создаёт пользовательский триггер, возвращает id.
     *
     * @param profileId если задан — триггер кладётся в профиль персонажа
     *   (активен, только когда профиль в стеке), иначе в базовый набор.
     * @param scope область действия: null или {"type":"world"} — везде,
     *   {"type":"zone","zones":[...]} или
     *   {"type":"room","roomIds":[...],"roomPropertyKeys":[...]} — только там.
     */
    fun createTrigger(
        name: String,
        pattern: String,
        commands: List<String>,
        enabled: Boolean = true,
        gag: Boolean = false,
        priority: Int = 0,
        profileId: String? = null,
        scope: Map<String, Any?>? = null
    ): String

    /** Обновляет триггер по id. */
    fun updateTrigger(id: String, changes: Map<String, Any?>): Boolean

    /** Удаляет триггер по id. */
    fun deleteTrigger(id: String): Boolean

    // --- Пользовательские алиасы ---

    /** Список алиасов: id, name, pattern, commands, enabled. */
    fun listAliases(): List<Map<String, Any?>>

    /**
     * Создаёт алиас, возвращает id.
     * @param profileId если задан — алиас кладётся в профиль персонажа.
     */
    fun createAlias(
        name: String,
        pattern: String,
        commands: List<String>,
        enabled: Boolean = true,
        profileId: String? = null
    ): String

    fun updateAlias(id: String, changes: Map<String, Any?>): Boolean
    fun deleteAlias(id: String): Boolean

    // --- Хоткеи ---

    /** Список хоткеев: id, name, key, modifiers, commands, enabled. */
    fun listHotkeys(): List<Map<String, Any?>>

    /**
     * Создаёт хоткей (key — например "F5", "Num8"), возвращает id.
     * @param profileId если задан — хоткей кладётся в профиль персонажа.
     */
    fun createHotkey(
        name: String,
        key: String,
        commands: List<String>,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        enabled: Boolean = true,
        profileId: String? = null,
        /** Область действия — формат тот же, что у [createTrigger]. */
        scope: Map<String, Any?>? = null
    ): String

    fun updateHotkey(id: String, changes: Map<String, Any?>): Boolean
    fun deleteHotkey(id: String): Boolean

    // --- Вкладки вывода (пользовательские) ---

    /** Список вкладок вывода: id, name, filters, captureMode, profileTab, profileLog. */
    fun listTabs(): List<Map<String, Any?>>

    /** Создаёт вкладку вывода с regex-фильтрами, возвращает id. */
    fun createTab(
        name: String,
        patterns: List<String> = emptyList(),
        captureMode: String = "COPY",
        profileTab: Boolean = false,
        profileLog: Boolean = false,
        persistContent: Boolean = false,
        /** Помечать пойманные строки временем — в выводе сервера его нет. */
        timestamps: Boolean = false
    ): String

    fun deleteTab(id: String): Boolean

    // --- Контекстные команды ---

    /**
     * Правила контекстных команд: они не выполняются сами, а предлагают команду
     * в панели, пока игрок находится в нужном месте.
     *
     * Возвращает: id, enabled, pattern (или permanent), scope, command, ttl, priority.
     */
    fun listContextRules(): List<Map<String, Any?>>

    /**
     * Создаёт правило контекстной команды.
     *
     * @param pattern regex по строке вывода; null — правило постоянное
     *   (команда доступна всё время, пока игрок в заданной области).
     * @param scope область действия (см. [createTrigger]).
     * @param ttl "room_change" | "zone_change" | "fixed_time" | "permanent" | "one_time".
     */
    fun createContextRule(
        command: String,
        pattern: String? = null,
        scope: Map<String, Any?>? = null,
        ttl: String = "room_change",
        ttlMinutes: Int? = null,
        priority: Int = 0,
        enabled: Boolean = true,
        /** Если задан — правило живёт в профиле персонажа, как триггеры и хоткеи. */
        profileId: String? = null
    ): String

    /**
     * Правит контекстное правило: command, pattern, scope, ttl, ttlMinutes,
     * priority, enabled, profileId.
     *
     * profileId переносит правило между профилями персонажей (и в базовый
     * набор по null), сохраняя id — иначе массовый перенос сводился к
     * «удалить и создать заново».
     *
     * @return false, если правила с таким id нет
     */
    fun updateContextRule(id: String, changes: Map<String, Any?>): Boolean

    fun deleteContextRule(id: String): Boolean

    /** Команды, предложенные игроку прямо сейчас (очередь в панели). */
    fun listContextQueue(): List<Map<String, Any?>>

    // --- MSDP ---

    /**
     * Снимок MSDP: значения, время последнего обновления и список переменных,
     * которые сервер вообще умеет присылать.
     *
     * Данные приходят структурой (например, ROOM с VNUM, NAME, ZONE, EXITS),
     * поэтому отдаются как есть — разбирать их за потребителя было бы гаданием.
     *
     * @param vars если задан — только эти переменные.
     */
    fun getMsdp(vars: List<String>? = null): Map<String, Any?>

    // --- Зоны карты ---

    /**
     * Метаданные зоны: имя, заметка, свойства, число известных комнат.
     * У комнат есть и заметка, и свойства — у зон раньше только свойства.
     */
    fun getZone(zoneId: String): Map<String, Any?>

    /** Список зон карты: id, имя, число комнат. */
    fun listZones(): List<Map<String, Any?>>

    /** Комнаты зоны — получить их поиском по названию было нельзя. */
    fun listZoneRooms(zoneId: String): List<Map<String, Any?>>

    /** Заметка зоны (тот же markdown, что в панели карты). */
    fun setZoneNote(zoneId: String, note: String)

    /**
     * Где игрок сейчас: комната и зона (с готовой подписью «Название (53)»).
     * Зону не нужно вычислять из id комнаты — клиент её и так знает.
     */
    fun getLocation(): Map<String, Any?>

    // --- Настройки клиента ---

    /**
     * Текущие настройки: тема, шрифт, кодировка, размеры панелей, логирование.
     * Ключи совпадают с теми, что принимает [updateSettings].
     */
    fun getSettings(): Map<String, Any?>

    /**
     * Меняет настройки. Неизвестные ключи игнорируются.
     * @return применённые значения (что реально изменилось).
     */
    fun updateSettings(changes: Map<String, Any?>): Map<String, Any?>

    /**
     * Состояние логирования: включено ли, текущий файл, каталог, число файлов,
     * сохраняются ли ANSI-цвета.
     */
    fun getLogInfo(): Map<String, Any?>

    /** Включает или выключает запись лога. */
    fun setLogging(enabled: Boolean)

    // --- Профили персонажей (стек) ---

    /** Список профилей персонажей: id, name, active, triggers/aliases/hotkeys (количества). */
    fun listCharacterProfiles(): List<Map<String, Any?>>

    /**
     * Создаёт профиль персонажа, возвращает его id.
     *
     * @param requires id профилей, которые должны быть активны раньше этого
     *   (цепочка вида «Былины ← Кузнец ← Творемир»).
     */
    fun createCharacterProfile(
        name: String,
        description: String = "",
        requires: List<String> = emptyList()
    ): String

    /** Задаёт зависимости существующего профиля персонажа. */
    fun setCharacterProfileDependencies(id: String, requires: List<String>): Boolean

    /** Активирует профиль персонажа (добавляет в стек). */
    fun pushCharacterProfile(id: String): Boolean

    /** Деактивирует профиль персонажа (убирает из стека). */
    fun popCharacterProfile(id: String): Boolean
}
