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
     * @param profileId ID профиля, к которому принадлежит скрипт (null = базовый)
     */
    fun loadScript(file: File, forceDisabled: Boolean = false, profileId: String? = null): Script? {
        val isDisabled = forceDisabled || file.name.contains(".disabled")

        // Определяем расширение (убираем .disabled если есть)
        val cleanName = file.name.replace(".disabled", "")
        val extension = cleanName.substringAfterLast(".", "")
        val engine = engines.firstOrNull { it.fileExtensions.contains(".$extension") }

        if (engine == null) {
            logger.warn { "No engine found for extension: .$extension" }
            return null
        }

        if (isDisabled) {
            // Для отключённых скриптов - только добавляем в список без выполнения
            val script = Script(
                id = java.util.UUID.randomUUID().toString(),
                name = cleanName.substringBeforeLast("."),
                path = file.absolutePath,
                engine = engine,
                enabled = false,
                profileId = profileId
            )
            _scripts.value = _scripts.value + script
            logger.info { "Added disabled ${engine.name} script: $cleanName${profileId?.let { " (profile: $it)" } ?: ""}" }
            return script
        }

        // Для включённых - полная загрузка с выполнением кода
        return try {
            val script = engine.loadScript(file.absolutePath)
            // Добавляем profileId к скрипту
            val scriptWithProfile = script.copy(profileId = profileId)
            _scripts.value = _scripts.value + scriptWithProfile

            // Вызываем on_load если функция существует
            try {
                scriptWithProfile.call("on_load", api)
            } catch (e: Exception) {
                logger.warn { "Error calling on_load for ${script.name}: ${e.message}" }
            }

            logger.info { "Loaded ${engine.name} script: $cleanName${profileId?.let { " (profile: $it)" } ?: ""}" }
            scriptWithProfile
        } catch (e: Exception) {
            // Ошибка при загрузке - создаём скрипт с ошибкой
            val failedScript = Script(
                id = java.util.UUID.randomUUID().toString(),
                name = cleanName.substringBeforeLast("."),
                path = file.absolutePath,
                engine = engine,
                enabled = true,
                profileId = profileId,
                loadError = e.message ?: "Unknown error"
            )
            _scripts.value = _scripts.value + failedScript
            logger.warn { "Failed to load ${engine.name} script $cleanName: ${e.message}" }
            failedScript
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
        if (script.enabled && !script.hasFailed) return // Уже включён и работает

        val file = File(script.path)
        if (!file.exists()) {
            logger.warn { "Script file not found: ${script.path}" }
            // Помечаем как failed
            val failedScript = script.copy(enabled = true, loadError = "File not found: ${script.path}")
            _scripts.value = currentList.toMutableList().apply {
                set(scriptIndex, failedScript)
            }
            return
        }

        try {
            // Переименовываем файл (убираем .disabled) если нужно
            val newPath = script.path.replace(".disabled", "")
            val newFile = if (script.path != newPath) {
                val targetFile = File(newPath)
                if (!file.renameTo(targetFile)) {
                    logger.error { "Failed to rename file: ${script.path}" }
                    val failedScript = script.copy(enabled = true, loadError = "Failed to rename file")
                    _scripts.value = currentList.toMutableList().apply {
                        set(scriptIndex, failedScript)
                    }
                    return
                }
                targetFile
            } else {
                file
            }

            // Загружаем скрипт заново
            val engine = script.engine
            val newScript = engine.loadScript(newFile.absolutePath)

            // Сохраняем profileId
            val scriptWithProfile = newScript.copy(profileId = script.profileId)

            // Заменяем в списке на том же месте
            _scripts.value = currentList.toMutableList().apply {
                set(scriptIndex, scriptWithProfile)
            }

            // Вызываем on_load если функция существует
            try {
                scriptWithProfile.call("on_load", api)
            } catch (e: Exception) {
                logger.warn { "Error calling on_load for ${scriptWithProfile.name}: ${e.message}" }
            }

            logger.info { "Enabled ${engine.name} script: ${scriptWithProfile.name}" }
        } catch (e: Exception) {
            // Помечаем как failed
            val failedScript = script.copy(enabled = true, loadError = e.message ?: "Unknown error")
            _scripts.value = currentList.toMutableList().apply {
                set(scriptIndex, failedScript)
            }
            logger.warn { "Error enabling script: ${e.message}" }
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
     * Перезагружает скрипт (сохраняя позицию в списке)
     */
    fun reloadScript(scriptId: String) {
        val currentList = _scripts.value
        val scriptIndex = currentList.indexOfFirst { it.id == scriptId }
        if (scriptIndex == -1) return

        val script = currentList[scriptIndex]
        val file = File(script.path)

        // Вызываем on_unload если функция существует (только если скрипт был загружен успешно)
        if (!script.hasFailed) {
            try {
                script.call("on_unload")
            } catch (e: Exception) {
                // Игнорируем ошибки
            }
        }

        // Перезагружаем через движок
        val engine = engines.find { it.name == script.engine.name } ?: return

        try {
            val newScript = engine.loadScript(file.absolutePath)
            // Сохраняем profileId от старого скрипта
            val scriptWithProfile = newScript.copy(profileId = script.profileId)

            // Заменяем в списке на том же месте
            _scripts.value = currentList.toMutableList().apply {
                set(scriptIndex, scriptWithProfile)
            }

            // Вызываем on_load если функция существует
            try {
                scriptWithProfile.call("on_load", api)
            } catch (e: Exception) {
                // Игнорируем ошибки если функция не существует
            }

            logger.info { "Reloaded ${script.engine.name} script: ${script.name}" }
        } catch (e: Exception) {
            // Ошибка при загрузке - помечаем как failed
            val failedScript = script.copy(loadError = e.message ?: "Unknown error")
            _scripts.value = currentList.toMutableList().apply {
                set(scriptIndex, failedScript)
            }
            logger.warn { "Error reloading script ${script.name}: ${e.message}" }
        }
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
            ScriptEvent.ON_MSDP_ENABLED -> "on_msdp_enabled"
            ScriptEvent.ON_GMCP -> "on_gmcp"
            ScriptEvent.ON_TRIGGER -> "on_trigger"
            ScriptEvent.ON_ALIAS -> "on_alias"
            ScriptEvent.ON_ROOM_ENTER -> "on_room_enter"
            // Боевые события для AI-бота
            ScriptEvent.ON_COMBAT_START -> "on_combat_start"
            ScriptEvent.ON_COMBAT_END -> "on_combat_end"
            ScriptEvent.ON_DAMAGE_DEALT -> "on_damage_dealt"
            ScriptEvent.ON_DAMAGE_RECEIVED -> "on_damage_received"
            ScriptEvent.ON_MOB_KILLED -> "on_mob_killed"
            ScriptEvent.ON_PLAYER_DEATH -> "on_player_death"
            ScriptEvent.ON_AFFECT_APPLIED -> "on_affect_applied"
            ScriptEvent.ON_AFFECT_EXPIRED -> "on_affect_expired"
            ScriptEvent.ON_LEVEL_UP -> "on_level_up"
            ScriptEvent.ON_EXP_GAIN -> "on_exp_gain"
            ScriptEvent.ON_ITEM_PICKED -> "on_item_picked"
            ScriptEvent.ON_ZONE_CHANGED -> "on_zone_changed"
            ScriptEvent.ON_LOW_HP -> "on_low_hp"
            ScriptEvent.ON_LOW_MANA -> "on_low_mana"
            ScriptEvent.ON_SKILL_READY -> "on_skill_ready"
            ScriptEvent.ON_TARGET_CHANGED -> "on_target_changed"
        }

        val enabledScripts = _scripts.value.filter { it.enabled }
        enabledScripts.forEach { script ->
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

    // === Методы для работы со скриптами профилей ===

    /**
     * Загружает все скрипты из указанной директории
     * @param directory директория со скриптами
     * @param profileId ID профиля, к которому принадлежат скрипты
     */
    fun loadScriptsFromDirectory(directory: File, profileId: String) {
        if (!directory.exists() || !directory.isDirectory) {
            logger.info { "Scripts directory does not exist for profile $profileId: ${directory.absolutePath}" }
            return
        }

        val allFiles = directory.listFiles { file ->
            file.isFile && engines.any { engine ->
                engine.fileExtensions.any { ext ->
                    file.name.endsWith(ext) || file.name.contains(ext)
                }
            }
        } ?: emptyArray()

        val (disabledFiles, enabledFiles) = allFiles.partition { file ->
            file.name.contains(".disabled")
        }

        logger.info { "Found ${enabledFiles.size} enabled and ${disabledFiles.size} disabled scripts for profile $profileId" }

        // Загружаем включённые скрипты (с выполнением кода)
        enabledFiles.forEach { file ->
            loadScript(file, profileId = profileId)
        }

        // Добавляем отключённые скрипты в список (без выполнения кода)
        disabledFiles.forEach { file ->
            loadScript(file, profileId = profileId)
        }
    }

    /**
     * Выгружает все скрипты, принадлежащие указанному профилю
     * @param profileId ID профиля
     */
    fun unloadScriptsByProfileId(profileId: String) {
        val scriptsToUnload = _scripts.value.filter { it.profileId == profileId }

        scriptsToUnload.forEach { script ->
            // Вызываем on_unload если функция существует
            try {
                script.call("on_unload")
            } catch (e: Exception) {
                // Игнорируем ошибки
            }
        }

        _scripts.value = _scripts.value.filter { it.profileId != profileId }

        logger.info { "Unloaded ${scriptsToUnload.size} scripts for profile $profileId" }
    }
}
