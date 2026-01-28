package com.bylins.client.scripting

import com.bylins.client.scripting.engines.JavaScriptEngine
import com.bylins.client.scripting.engines.ScriptContextWrapper
// Re-export ScriptEvent from plugins:core for backwards compatibility
import com.bylins.client.plugins.scripting.ScriptEvent as CoreScriptEvent

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
 * ScriptEvent теперь определён в plugins:core для использования плагинами.
 * Создаём typealias для обратной совместимости.
 */
typealias ScriptEvent = CoreScriptEvent
