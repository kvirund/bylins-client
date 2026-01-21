package com.bylins.client.scripting

import com.bylins.client.scripting.engines.JavaScriptEngine
import com.bylins.client.scripting.engines.ScriptContextWrapper

/**
 * Интерфейс для движков скриптов
 */
interface ScriptEngine {
    /**
     * Название движка (python, lua, javascript)
     */
    val name: String

    /**
     * Расширения файлов (.py, .lua, .js)
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
     * @throws RuntimeException при ошибке загрузки
     */
    fun loadScript(scriptPath: String): Script

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
    val enabled: Boolean = true,
    val profileId: String? = null,  // ID профиля, к которому принадлежит скрипт (null = базовый скрипт)
    val loadError: String? = null,  // Ошибка загрузки (null = загружен успешно)
    val context: ScriptContextWrapper? = null  // Контекст для изолированного выполнения (для JavaScript)
) {
    /**
     * Вызывает функцию в скрипте
     */
    fun call(functionName: String, vararg args: Any?): Any? {
        if (!enabled || loadError != null) return null

        // Для JavaScript используем изолированный контекст
        if (context != null && engine is JavaScriptEngine) {
            return engine.callFunctionInContext(context, functionName, *args)
        }

        return engine.callFunction(functionName, *args)
    }

    /**
     * Скрипт загружен с ошибкой
     */
    val hasFailed: Boolean get() = loadError != null
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
    ON_ROOM_ENTER,     // При входе в новую комнату (маппер)

    // Боевые события для AI-бота
    ON_COMBAT_START,      // Начало боя
    ON_COMBAT_END,        // Конец боя (победа/бегство/смерть)
    ON_DAMAGE_DEALT,      // Нанесён урон
    ON_DAMAGE_RECEIVED,   // Получен урон
    ON_MOB_KILLED,        // Убит моб
    ON_PLAYER_DEATH,      // Смерть персонажа
    ON_AFFECT_APPLIED,    // Наложен эффект
    ON_AFFECT_EXPIRED,    // Эффект истёк
    ON_LEVEL_UP,          // Повышение уровня
    ON_EXP_GAIN,          // Получен опыт
    ON_ITEM_PICKED,       // Подобран предмет
    ON_ZONE_CHANGED,      // Смена зоны

    // События для бота
    ON_LOW_HP,            // HP ниже порога (для flee)
    ON_LOW_MANA,          // Мана ниже порога
    ON_SKILL_READY,       // Навык готов к использованию
    ON_TARGET_CHANGED     // Сменилась цель
}
