package com.bylins.client.scripting

/**
 * Интерфейс для движков скриптов
 */
interface ScriptEngine {
    /**
     * Название движка (python, lua, javascript, perl)
     */
    val name: String

    /**
     * Расширения файлов (.py, .lua, .js, .pl)
     */
    val fileExtensions: List<String>

    /**
     * Проверяет доступность движка
     */
    fun isAvailable(): Boolean

    /**
     * Инициализирует движок
     */
    fun initialize(api: ScriptAPI)

    /**
     * Загружает скрипт из файла
     */
    fun loadScript(scriptPath: String): Script?

    /**
     * Выполняет код
     */
    fun execute(code: String)

    /**
     * Вызывает функцию в скрипте
     */
    fun callFunction(functionName: String, vararg args: Any?): Any?

    /**
     * Останавливает движок и освобождает ресурсы
     */
    fun shutdown()
}

/**
 * Представляет загруженный скрипт
 */
data class Script(
    val id: String,
    val name: String,
    val path: String,
    val engine: ScriptEngine,
    val enabled: Boolean = true
) {
    /**
     * Вызывает функцию в скрипте
     */
    fun call(functionName: String, vararg args: Any?): Any? {
        if (!enabled) return null
        return engine.callFunction(functionName, *args)
    }
}

/**
 * События для скриптов
 */
enum class ScriptEvent {
    ON_LOAD,           // При загрузке скрипта
    ON_UNLOAD,         // При выгрузке скрипта
    ON_COMMAND,        // При отправке команды
    ON_LINE,           // При получении строки от сервера
    ON_CONNECT,        // При подключении
    ON_DISCONNECT,     // При отключении
    ON_MSDP,           // При получении MSDP данных
    ON_MSDP_ENABLED,   // При включении MSDP сервером
    ON_GMCP,           // При получении GMCP данных
    ON_TRIGGER,        // При срабатывании триггера
    ON_ALIAS,          // При срабатывании алиаса
    ON_ROOM_ENTER      // При входе в новую комнату (маппер)
}
