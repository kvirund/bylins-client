package com.bylins.client.scripting

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Управляет скриптами и их выполнением
 */
class ScriptManager(
    private val api: ScriptAPI
) {
    private val engines = mutableListOf<ScriptEngine>()
    private val _scripts = MutableStateFlow<List<Script>>(emptyList())
    val scripts: StateFlow<List<Script>> = _scripts

    private val scriptsDirectory = File("scripts")

    init {
        // Создаем директорию для скриптов если её нет
        if (!scriptsDirectory.exists()) {
            scriptsDirectory.mkdirs()
        }
    }

    /**
     * Регистрирует движок скриптов
     */
    fun registerEngine(engine: ScriptEngine) {
        if (engine.isAvailable()) {
            engine.initialize(api)
            engines.add(engine)
            println("[ScriptManager] Registered engine: ${engine.name}")
        } else {
            println("[ScriptManager] Engine ${engine.name} is not available")
        }
    }

    /**
     * Загружает скрипт из файла
     */
    fun loadScript(file: File): Script? {
        val extension = file.extension
        val engine = engines.firstOrNull { it.fileExtensions.contains(".$extension") }

        if (engine == null) {
            println("[ScriptManager] No engine found for extension: .$extension")
            return null
        }

        return try {
            val script = engine.loadScript(file.absolutePath)
            if (script != null) {
                _scripts.value = _scripts.value + script

                // Вызываем on_load если функция существует
                try {
                    script.call("on_load", api)
                } catch (e: Exception) {
                    println("[ScriptManager] Error calling on_load for ${script.name}: ${e.message}")
                }

                println("[ScriptManager] Loaded script: ${script.name}")
            }
            script
        } catch (e: Exception) {
            println("[ScriptManager] Error loading script ${file.name}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Выгружает скрипт
     */
    fun unloadScript(scriptId: String) {
        val script = _scripts.value.find { it.id == scriptId } ?: return

        // Вызываем on_unload если функция существует
        try {
            script.call("on_unload")
        } catch (e: Exception) {
            println("[ScriptManager] Error calling on_unload for ${script.name}: ${e.message}")
        }

        _scripts.value = _scripts.value.filter { it.id != scriptId }
        println("[ScriptManager] Unloaded script: ${script.name}")
    }

    /**
     * Включает скрипт
     */
    fun enableScript(scriptId: String) {
        _scripts.value = _scripts.value.map { script ->
            if (script.id == scriptId) script.copy(enabled = true) else script
        }
    }

    /**
     * Выключает скрипт
     */
    fun disableScript(scriptId: String) {
        _scripts.value = _scripts.value.map { script ->
            if (script.id == scriptId) script.copy(enabled = false) else script
        }
    }

    /**
     * Автозагрузка всех скриптов из директории scripts/
     */
    fun autoLoadScripts() {
        if (!scriptsDirectory.exists()) {
            return
        }

        val scriptFiles = scriptsDirectory.listFiles { file ->
            file.isFile && engines.any { engine ->
                engine.fileExtensions.any { ext -> file.name.endsWith(ext) }
            }
        } ?: emptyArray()

        println("[ScriptManager] Auto-loading ${scriptFiles.size} scripts...")

        scriptFiles.forEach { file ->
            loadScript(file)
        }
    }

    /**
     * Перезагружает скрипт
     */
    fun reloadScript(scriptId: String) {
        val script = _scripts.value.find { it.id == scriptId } ?: return
        val file = File(script.path)

        unloadScript(scriptId)
        loadScript(file)
    }

    /**
     * Вызывает событие во всех скриптах
     */
    fun fireEvent(event: ScriptEvent, vararg args: Any?) {
        val functionName = when (event) {
            ScriptEvent.ON_LOAD -> "on_load"
            ScriptEvent.ON_UNLOAD -> "on_unload"
            ScriptEvent.ON_COMMAND -> "on_command"
            ScriptEvent.ON_LINE -> "on_line"
            ScriptEvent.ON_CONNECT -> "on_connect"
            ScriptEvent.ON_DISCONNECT -> "on_disconnect"
            ScriptEvent.ON_MSDP -> "on_msdp"
            ScriptEvent.ON_TRIGGER -> "on_trigger"
            ScriptEvent.ON_ALIAS -> "on_alias"
            ScriptEvent.ON_ROOM_ENTER -> "on_room_enter"
        }

        _scripts.value.filter { it.enabled }.forEach { script ->
            try {
                script.call(functionName, *args)
            } catch (e: Exception) {
                // Игнорируем ошибки если функция не существует
                if (!e.message.toString().contains("not found", ignoreCase = true)) {
                    println("[ScriptManager] Error in ${script.name}.${functionName}: ${e.message}")
                }
            }
        }
    }

    /**
     * Останавливает все движки и выгружает скрипты
     */
    fun shutdown() {
        // Выгружаем все скрипты
        _scripts.value.toList().forEach { script ->
            unloadScript(script.id)
        }

        // Останавливаем движки
        engines.forEach { engine ->
            try {
                engine.shutdown()
            } catch (e: Exception) {
                println("[ScriptManager] Error shutting down ${engine.name}: ${e.message}")
            }
        }

        engines.clear()
    }

    /**
     * Возвращает список доступных движков
     */
    fun getAvailableEngines(): List<String> {
        return engines.map { it.name }
    }

    /**
     * Возвращает путь к директории скриптов
     */
    fun getScriptsDirectory(): String {
        return scriptsDirectory.absolutePath
    }
}
