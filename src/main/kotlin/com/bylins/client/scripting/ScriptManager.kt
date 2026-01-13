package com.bylins.client.scripting

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger("ScriptManager")

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
            logger.info { "Registered engine: ${engine.name}" }
        } else {
            logger.warn { "Engine ${engine.name} is not available" }
        }
    }

    /**
     * Загружает скрипт из файла
     * @param file файл скрипта
     * @param forceDisabled принудительно создать как отключённый (не выполнять код)
     */
    fun loadScript(file: File, forceDisabled: Boolean = false): Script? {
        val isDisabled = forceDisabled || file.name.contains(".disabled")

        // Определяем расширение (убираем .disabled если есть)
        val cleanName = file.name.replace(".disabled", "")
        val extension = cleanName.substringAfterLast(".", "")
        val engine = engines.firstOrNull { it.fileExtensions.contains(".$extension") }

        if (engine == null) {
            logger.warn { "No engine found for extension: .$extension" }
            return null
        }

        return try {
            if (isDisabled) {
                // Для отключённых скриптов - только добавляем в список без выполнения
                val script = Script(
                    id = java.util.UUID.randomUUID().toString(),
                    name = cleanName.substringBeforeLast("."),
                    path = file.absolutePath,
                    engine = engine,
                    enabled = false
                )
                _scripts.value = _scripts.value + script
                logger.info { "Added disabled ${engine.name} script: $cleanName" }
                script
            } else {
                // Для включённых - полная загрузка с выполнением кода
                val script = engine.loadScript(file.absolutePath)
                if (script != null) {
                    _scripts.value = _scripts.value + script

                    // Вызываем on_load если функция существует
                    try {
                        script.call("on_load", api)
                    } catch (e: Exception) {
                        logger.warn { "Error calling on_load for ${script.name}: ${e.message}" }
                    }

                    logger.info { "Loaded ${engine.name} script: $cleanName" }
                }
                script
            }
        } catch (e: Exception) {
            logger.error(e) { "Error loading ${engine.name} script $cleanName: ${e.message}" }
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
            logger.error { "Error calling on_unload for ${script.name}: ${e.message}" }
        }

        _scripts.value = _scripts.value.filter { it.id != scriptId }
        logger.info { "Unloaded ${script.engine.name} script: ${script.name}" }
    }

    /**
     * Включает скрипт (переименовывает файл и загружает код)
     */
    fun enableScript(scriptId: String) {
        val currentList = _scripts.value
        val scriptIndex = currentList.indexOfFirst { it.id == scriptId }
        if (scriptIndex == -1) return

        val script = currentList[scriptIndex]
        if (script.enabled) return // Уже включён

        val file = File(script.path)
        if (!file.exists()) {
            logger.warn { "Script file not found: ${script.path}" }
            return
        }

        try {
            // Переименовываем файл (убираем .disabled)
            val newPath = script.path.replace(".disabled", "")
            val newFile = File(newPath)
            if (file.renameTo(newFile)) {
                // Загружаем скрипт заново (уже как включённый), но не добавляем в список
                val engine = script.engine
                val newScript = engine.loadScript(newFile.absolutePath)

                if (newScript != null) {
                    // Заменяем в списке на том же месте
                    _scripts.value = currentList.toMutableList().apply {
                        set(scriptIndex, newScript)
                    }

                    // Вызываем on_load если функция существует
                    try {
                        newScript.call("on_load", api)
                    } catch (e: Exception) {
                        logger.warn { "Error calling on_load for ${newScript.name}: ${e.message}" }
                    }

                    logger.info { "Enabled ${engine.name} script: ${newScript.name}" }
                }
            } else {
                logger.error { "Failed to rename file: ${script.path}" }
            }
        } catch (e: Exception) {
            logger.error { "Error enabling script: ${e.message}" }
        }
    }

    /**
     * Выключает скрипт (выгружает код и переименовывает файл)
     */
    fun disableScript(scriptId: String) {
        val currentList = _scripts.value
        val scriptIndex = currentList.indexOfFirst { it.id == scriptId }
        if (scriptIndex == -1) return

        val script = currentList[scriptIndex]
        if (!script.enabled) return // Уже выключен

        val file = File(script.path)
        if (!file.exists()) {
            logger.warn { "Script file not found: ${script.path}" }
            return
        }

        try {
            // Вызываем on_unload
            try {
                script.call("on_unload")
            } catch (e: Exception) {
                // Игнорируем если функции нет
            }

            // Переименовываем файл (добавляем .disabled перед расширением)
            val ext = file.extension
            val newPath = script.path.replace(".$ext", ".$ext.disabled")
            val newFile = File(newPath)

            if (file.renameTo(newFile)) {
                // Создаём отключённый скрипт на том же месте
                val cleanName = newFile.name.replace(".disabled", "")
                val newScript = Script(
                    id = java.util.UUID.randomUUID().toString(),
                    name = cleanName.substringBeforeLast("."),
                    path = newFile.absolutePath,
                    engine = script.engine,
                    enabled = false
                )

                // Заменяем в списке на том же месте
                _scripts.value = currentList.toMutableList().apply {
                    set(scriptIndex, newScript)
                }

                logger.info { "Disabled ${script.engine.name} script: ${script.name}" }
            } else {
                logger.error { "Failed to rename file: ${script.path}" }
            }
        } catch (e: Exception) {
            logger.error { "Error disabling script: ${e.message}" }
        }
    }

    /**
     * Автозагрузка всех скриптов из директории scripts/
     */
    fun autoLoadScripts() {
        if (!scriptsDirectory.exists()) {
            return
        }

        val allFiles = scriptsDirectory.listFiles { file ->
            file.isFile && engines.any { engine ->
                engine.fileExtensions.any { ext ->
                    file.name.endsWith(ext) || file.name.contains(ext)
                }
            }
        } ?: emptyArray()

        val (disabledFiles, enabledFiles) = allFiles.partition { file ->
            file.name.contains(".disabled")
        }

        logger.info { "Found ${enabledFiles.size} enabled and ${disabledFiles.size} disabled scripts" }

        // Загружаем включённые скрипты (с выполнением кода)
        enabledFiles.forEach { file ->
            loadScript(file)
        }

        // Добавляем отключённые скрипты в список (без выполнения кода)
        disabledFiles.forEach { file ->
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
            ScriptEvent.ON_GMCP -> "on_gmcp"
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
                    logger.error { "Error in ${script.name}.${functionName}: ${e.message}" }
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
                logger.error { "Error shutting down ${engine.name}: ${e.message}" }
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
