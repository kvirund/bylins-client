package com.bylins.client.commands

import mu.KotlinLogging
import com.bylins.client.audio.SoundManager
import com.bylins.client.contextcommands.ContextCommandManager
import com.bylins.client.mapper.Direction
import com.bylins.client.mapper.MapManager
import com.bylins.client.mapper.Room
import com.bylins.client.plugins.LoadedPlugin
import com.bylins.client.plugins.PluginManager
import com.bylins.client.plugins.PluginState
import com.bylins.client.scripting.Script
import com.bylins.client.scripting.ScriptManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.coroutineContext

private val logger = KotlinLogging.logger("CommandProcessor")

/**
 * Интерфейс для взаимодействия CommandProcessor с ClientState
 * Позволяет избежать циклических зависимостей
 */
interface CommandContext {
    fun addLocalOutput(text: String)
    fun sendRaw(command: String)
    fun getAllZones(): List<String>
    fun getZoneStatistics(): Map<String, Int>
    fun detectAndAssignZones()
    fun clearAllZones()
}

/**
 * Обработчик специальных команд клиента (#goto, #find, #zone, #script, #plugin и т.д.)
 * Выделен из ClientState для уменьшения размера основного класса
 */
class CommandProcessor(
    private val scope: CoroutineScope,
    private val context: CommandContext,
    private val mapManager: MapManager,
    private val soundManager: SoundManager,
    private val contextCommandManager: ContextCommandManager,
    private val getScriptManager: () -> ScriptManager?,
    private val getPluginManager: () -> PluginManager?
) {
    /**
     * Обрабатывает команды навигации по карте
     * Возвращает true если команда была обработана
     */
    fun processNavigationCommand(command: String): Boolean {
        when {
            command == "#help" -> {
                showHelp()
                return true
            }

            command.startsWith("#sound ") -> {
                val soundType = command.substring(7).trim().lowercase()
                val type = when (soundType) {
                    "tell" -> SoundManager.SoundType.TELL
                    "whisper" -> SoundManager.SoundType.WHISPER
                    "lowhp" -> SoundManager.SoundType.LOW_HP
                    "levelup" -> SoundManager.SoundType.LEVEL_UP
                    "death" -> SoundManager.SoundType.DEATH
                    "combat" -> SoundManager.SoundType.COMBAT
                    "alert" -> SoundManager.SoundType.ALERT
                    "beep" -> {
                        soundManager.playBeep()
                        return true
                    }
                    else -> {
                        context.addLocalOutput("\u001B[1;33m[#sound] Неизвестный тип звука: $soundType\u001B[0m")
                        return true
                    }
                }
                soundManager.playSound(type)
                return true
            }

            command.startsWith("#goto ") -> {
                val roomId = command.substring(6).trim()
                if (roomId.isEmpty()) {
                    context.addLocalOutput("\u001B[1;33m[#goto] Использование: #goto <room_id>\u001B[0m")
                    return true
                }

                // Находим путь к комнате
                val path = mapManager.findPathFromCurrent(roomId)
                if (path == null) {
                    context.addLocalOutput("\u001B[1;31m[#goto] Путь к комнате '$roomId' не найден\u001B[0m")
                    return true
                }

                if (path.isEmpty()) {
                    context.addLocalOutput("\u001B[1;33m[#goto] Вы уже в этой комнате\u001B[0m")
                    return true
                }

                // Запускаем автоматическое перемещение
                val directions = path.joinToString(", ") { it.shortName }
                context.addLocalOutput("\u001B[1;32m[#goto] Путь найден (${path.size} шагов): $directions\u001B[0m")

                scope.launch {
                    walkPath(path)
                }
                return true
            }

            command == "#run" -> {
                // Находим путь к ближайшей непосещенной комнате
                val path = mapManager.findNearestUnvisited()
                if (path == null) {
                    context.addLocalOutput("\u001B[1;33m[#run] Не найдено непосещенных комнат\u001B[0m")
                    return true
                }

                if (path.isEmpty()) {
                    context.addLocalOutput("\u001B[1;33m[#run] Уже в непосещенной комнате\u001B[0m")
                    return true
                }

                // Запускаем автоматическое перемещение
                val directions = path.joinToString(", ") { it.shortName }
                context.addLocalOutput("\u001B[1;32m[#run] Путь к непосещенной комнате (${path.size} шагов): $directions\u001B[0m")

                scope.launch {
                    walkPath(path)
                }
                return true
            }

            command.startsWith("#find ") -> {
                val query = command.substring(6).trim()
                if (query.isEmpty()) {
                    context.addLocalOutput("\u001B[1;33m[#find] Использование: #find <название комнаты>\u001B[0m")
                    return true
                }

                // Ищем комнаты по названию
                val foundRooms = mapManager.searchRooms(query, searchInDescription = false)

                if (foundRooms.isEmpty()) {
                    context.addLocalOutput("\u001B[1;31m[#find] Комнаты с названием '$query' не найдены\u001B[0m")
                    return true
                }

                if (foundRooms.size == 1) {
                    // Если найдена одна комната, сразу идём к ней
                    val room = foundRooms.first()
                    val path = mapManager.findPathFromCurrent(room.id)

                    if (path == null) {
                        context.addLocalOutput("\u001B[1;31m[#find] Путь к комнате '${room.name}' не найден\u001B[0m")
                        return true
                    }

                    if (path.isEmpty()) {
                        context.addLocalOutput("\u001B[1;33m[#find] Вы уже в комнате '${room.name}'\u001B[0m")
                        return true
                    }

                    val directions = path.joinToString(", ") { it.shortName }
                    context.addLocalOutput("\u001B[1;32m[#find] Путь к '${room.name}' (${path.size} шагов): $directions\u001B[0m")

                    scope.launch {
                        walkPath(path)
                    }
                } else {
                    // Если найдено несколько комнат, показываем список
                    val sb = StringBuilder()
                    sb.append("\u001B[1;32m[#find] Найдено комнат: ${foundRooms.size}\u001B[0m\n")

                    // Сортируем по расстоянию и показываем первые 10
                    val sortedRooms = foundRooms.take(10)
                    sortedRooms.forEachIndexed { index, room ->
                        val path = mapManager.findPathFromCurrent(room.id)
                        val distance = path?.size ?: -1
                        val distanceStr = if (distance >= 0) "$distance шагов" else "недоступна"
                        sb.append("\u001B[1;33m${index + 1}.\u001B[0m ${room.name} (ID: ${room.id}, $distanceStr)\n")
                    }

                    if (foundRooms.size > 10) {
                        sb.append("\u001B[1;33m... и ещё ${foundRooms.size - 10} комнат\u001B[0m\n")
                    }

                    sb.append("\u001B[1;33mИспользуйте #goto <room_id> для перехода\u001B[0m")
                    context.addLocalOutput(sb.toString())
                }
                return true
            }

            command.startsWith("#zone") -> {
                val args = command.substring(5).trim()

                when {
                    // #zone - показать текущую зону
                    args.isEmpty() -> {
                        val currentRoom = mapManager.getCurrentRoom()
                        if (currentRoom == null) {
                            context.addLocalOutput("\u001B[1;31m[#zone] Текущая комната не определена\u001B[0m")
                        } else if (currentRoom.zone.isNullOrEmpty()) {
                            context.addLocalOutput("\u001B[1;33m[#zone] Текущая комната не принадлежит ни одной зоне\u001B[0m")
                        } else {
                            context.addLocalOutput("\u001B[1;32m[#zone] Текущая зона: ${currentRoom.zone}\u001B[0m")
                        }
                    }

                    // #zone list - список всех зон
                    args == "list" -> {
                        val zones = context.getAllZones()
                        if (zones.isEmpty()) {
                            context.addLocalOutput("\u001B[1;33m[#zone] Зоны не определены. Используйте #zone detect\u001B[0m")
                        } else {
                            val stats = context.getZoneStatistics()
                            val sb = StringBuilder()
                            sb.append("\u001B[1;32m[#zone] Список зон (${stats.size}):\u001B[0m\n")
                            stats.forEach { (zone, count) ->
                                sb.append("\u001B[1;33m- $zone\u001B[0m ($count комнат)\n")
                            }
                            context.addLocalOutput(sb.toString())
                        }
                    }

                    // #zone detect - автоматическая детекция
                    args == "detect" -> {
                        context.detectAndAssignZones()
                        val stats = context.getZoneStatistics()
                        context.addLocalOutput("\u001B[1;32m[#zone] Детектировано зон: ${stats.size}\u001B[0m")
                    }

                    // #zone clear - очистить все зоны
                    args == "clear" -> {
                        context.clearAllZones()
                        context.addLocalOutput("\u001B[1;32m[#zone] Все зоны очищены\u001B[0m")
                    }

                    else -> {
                        val sb = StringBuilder()
                        sb.append("\u001B[1;33m[#zone] Использование:\u001B[0m\n")
                        sb.append("  #zone - показать текущую зону\n")
                        sb.append("  #zone list - список всех зон\n")
                        sb.append("  #zone detect - автоматическая детекция зон\n")
                        sb.append("  #zone clear - очистить все зоны")
                        context.addLocalOutput(sb.toString())
                    }
                }
                return true
            }

            command.startsWith("#script") -> {
                val args = command.substring(7).trim()
                val parts = args.split(" ", limit = 2)
                val action = parts.getOrNull(0) ?: ""
                val scriptName = parts.getOrNull(1)?.trim() ?: ""

                val scriptManager = getScriptManager()

                when {
                    // #script list - список скриптов
                    action == "list" || args.isEmpty() -> {
                        if (scriptManager == null) {
                            context.addLocalOutput("\u001B[1;31m[#script] ScriptManager не инициализирован\u001B[0m")
                            return true
                        }
                        val scripts = scriptManager.scripts.value
                        if (scripts.isEmpty()) {
                            context.addLocalOutput("\u001B[1;33m[#script] Скрипты не загружены\u001B[0m")
                        } else {
                            val sb = StringBuilder()
                            sb.append("\u001B[1;32m[#script] Загруженные скрипты (${scripts.size}):\u001B[0m\n")
                            scripts.forEach { script ->
                                val status = if (script.enabled) "\u001B[1;32m✓\u001B[0m" else "\u001B[1;31m✗\u001B[0m"
                                sb.append("  $status ${script.name} (${script.engine})\n")
                            }
                            context.addLocalOutput(sb.toString())
                        }
                    }

                    // #script reload <name> - перезагрузить скрипт
                    action == "reload" -> {
                        if (scriptName.isEmpty()) {
                            context.addLocalOutput("\u001B[1;33m[#script] Использование: #script reload <имя>\u001B[0m")
                            return true
                        }
                        if (scriptManager == null) {
                            context.addLocalOutput("\u001B[1;31m[#script] ScriptManager не инициализирован\u001B[0m")
                            return true
                        }
                        // Ищем скрипт по имени (без расширения или с расширением)
                        val scripts = scriptManager.scripts.value
                        val script = scripts.find {
                            it.name.equals(scriptName, ignoreCase = true) ||
                            it.name.substringBeforeLast(".").equals(scriptName, ignoreCase = true)
                        }
                        if (script == null) {
                            context.addLocalOutput("\u001B[1;31m[#script] Скрипт '$scriptName' не найден\u001B[0m")
                            return true
                        }
                        try {
                            scriptManager.reloadScript(script.id)
                            context.addLocalOutput("\u001B[1;32m[#script] Скрипт '${script.name}' перезагружен\u001B[0m")
                        } catch (e: Exception) {
                            context.addLocalOutput("\u001B[1;31m[#script] Ошибка перезагрузки: ${e.message}\u001B[0m")
                        }
                    }

                    // #script unload <name> - выгрузить скрипт
                    action == "unload" -> {
                        if (scriptName.isEmpty()) {
                            context.addLocalOutput("\u001B[1;33m[#script] Использование: #script unload <имя>\u001B[0m")
                            return true
                        }
                        if (scriptManager == null) {
                            context.addLocalOutput("\u001B[1;31m[#script] ScriptManager не инициализирован\u001B[0m")
                            return true
                        }
                        val scripts = scriptManager.scripts.value
                        val script = scripts.find {
                            it.name.equals(scriptName, ignoreCase = true) ||
                            it.name.substringBeforeLast(".").equals(scriptName, ignoreCase = true)
                        }
                        if (script == null) {
                            context.addLocalOutput("\u001B[1;31m[#script] Скрипт '$scriptName' не найден\u001B[0m")
                            return true
                        }
                        try {
                            scriptManager.unloadScript(script.id)
                            context.addLocalOutput("\u001B[1;32m[#script] Скрипт '${script.name}' выгружен\u001B[0m")
                        } catch (e: Exception) {
                            context.addLocalOutput("\u001B[1;31m[#script] Ошибка выгрузки: ${e.message}\u001B[0m")
                        }
                    }

                    else -> {
                        val sb = StringBuilder()
                        sb.append("\u001B[1;33m[#script] Использование:\u001B[0m\n")
                        sb.append("  #script list - список загруженных скриптов\n")
                        sb.append("  #script reload <имя> - перезагрузить скрипт\n")
                        sb.append("  #script unload <имя> - выгрузить скрипт")
                        context.addLocalOutput(sb.toString())
                    }
                }
                return true
            }

            // #context-command N - выполнить N-ю контекстную команду
            command.startsWith("#context-command ") || command.startsWith("#cc ") -> {
                val prefix = if (command.startsWith("#cc ")) "#cc " else "#context-command "
                val indexStr = command.removePrefix(prefix).trim()
                val index = indexStr.toIntOrNull()
                if (index != null && index > 0) {
                    contextCommandManager.executeCommand(index - 1)  // 1-based to 0-based
                } else {
                    context.addLocalOutput("\u001B[1;33m[#context-command] Использование: #context-command N (N = 1-10)\u001B[0m")
                }
                return true
            }

            // #context-clear - очистить очередь контекстных команд
            command == "#context-clear" || command == "#cc-clear" -> {
                contextCommandManager.clearQueue()
                context.addLocalOutput("\u001B[1;32m[#context-command] Очередь контекстных команд очищена\u001B[0m")
                return true
            }

            // #plugin - управление плагинами
            command.startsWith("#plugin") -> {
                processPluginCommand(command)
                return true
            }

            // Speedwalk: распознаём паттерн типа 5n2e3w
            command.matches(Regex("^[0-9]*[nsewud]{1,2}([0-9]+[nsewud]{1,2})*$", RegexOption.IGNORE_CASE)) -> {
                val directions = parseSpeedwalk(command)
                if (directions.isEmpty()) {
                    return false
                }

                context.addLocalOutput("\u001B[1;32m[Speedwalk] ${directions.size} шагов: ${directions.joinToString(", ")}\u001B[0m")

                scope.launch {
                    walkPath(directions)
                }
                return true
            }

            else -> return false
        }
    }

    /**
     * Парсит строку speedwalk (например, "5n2e3w") в список направлений
     */
    fun parseSpeedwalk(text: String): List<Direction> {
        val directions = mutableListOf<Direction>()
        var i = 0

        while (i < text.length) {
            // Читаем число (если есть)
            var numStr = ""
            while (i < text.length && text[i].isDigit()) {
                numStr += text[i]
                i++
            }
            val count = if (numStr.isEmpty()) 1 else numStr.toInt()

            // Читаем направление (1-2 символа)
            if (i >= text.length) break

            var dirStr = text[i].toString()
            i++

            // Проверяем двухбуквенные направления (ne, nw, se, sw)
            if (i < text.length) {
                val twoChar = dirStr + text[i]
                if (twoChar.lowercase() in listOf("ne", "nw", "se", "sw")) {
                    dirStr = twoChar
                    i++
                }
            }

            // Конвертируем в Direction
            val direction = when (dirStr.lowercase()) {
                "n" -> Direction.NORTH
                "s" -> Direction.SOUTH
                "e" -> Direction.EAST
                "w" -> Direction.WEST
                "ne" -> Direction.NORTHEAST
                "nw" -> Direction.NORTHWEST
                "se" -> Direction.SOUTHEAST
                "sw" -> Direction.SOUTHWEST
                "u" -> Direction.UP
                "d" -> Direction.DOWN
                else -> return emptyList() // Неверное направление
            }

            // Добавляем count раз
            repeat(count) {
                directions.add(direction)
            }
        }

        return directions
    }

    /**
     * Показывает справку по доступным командам
     */
    private fun showHelp() {
        val help = """
            |═══════════════════════════════════════════════════════════════
            |  СПРАВКА ПО КОМАНДАМ КЛИЕНТА
            |═══════════════════════════════════════════════════════════════
            |
            |📍 НАВИГАЦИЯ И АВТОМАППЕР:
            |  #goto <room_id>        - Переход к указанной комнате
            |  #run                   - Переход к ближайшей непосещенной комнате
            |  #find <название>       - Поиск комнат по названию
            |  #zone                  - Информация о текущей зоне
            |  #zone list             - Список всех зон на карте
            |  #zone detect           - Автоматическая детекция зон
            |  #zone clear            - Очистить все зоны
            |  Speedwalk: 5n, 3n2e, 10sw - Быстрое перемещение
            |
            |💾 ПЕРЕМЕННЫЕ:
            |  #var <имя> <значение>  - Установить переменную
            |  #var <имя>             - Показать значение переменной
            |  #unvar <имя>           - Удалить переменную
            |  #vars                  - Показать все переменные
            |  Использование: @имя или ${'$'}{имя}
            |
            |🔊 ЗВУКИ:
            |  #sound <тип>           - Воспроизвести звук
            |  Типы: tell, whisper, lowhp, levelup, death, combat, alert, beep
            |
            |🗂️ ВКЛАДКИ:
            |  UI в правой панели для управления вкладками
            |
            |⚡ ТРИГГЕРЫ И АЛИАСЫ:
            |  UI в правой панели для управления
            |
            |⌨️ ГОРЯЧИЕ КЛАВИШИ:
            |  F1-F12, Numpad 0-9, модификаторы Ctrl/Alt/Shift
            |  UI в правой панели для управления
            |
            |🎨 СКРИПТЫ:
            |  #script               - Список загруженных скриптов
            |  #script reload <имя>  - Перезагрузить скрипт
            |  #script unload <имя>  - Выгрузить скрипт
            |  Поддержка JavaScript, Python (Jython), Lua (LuaJ)
            |  Размещайте скрипты в директории: scripts/
            |
            |🔌 ПЛАГИНЫ:
            |  #plugin               - Список плагинов
            |  #plugin reload <id>   - Перезагрузить плагин (hot-reload)
            |  #plugin enable <id>   - Включить плагин
            |  #plugin disable <id>  - Выключить плагин
            |  #plugin info <id>     - Информация о плагине
            |  #plugin help          - Полная справка по плагинам
            |
            |═══════════════════════════════════════════════════════════════
        """.trimMargin()

        context.addLocalOutput(help)
    }

    /**
     * Обрабатывает команды управления плагинами
     */
    private fun processPluginCommand(command: String) {
        val pluginManager = getPluginManager()
        if (pluginManager == null) {
            context.addLocalOutput("\u001B[1;31m[#plugin] PluginManager не инициализирован\u001B[0m")
            return
        }

        val args = command.removePrefix("#plugin").trim()
        val parts = args.split(" ", limit = 2)
        val action = parts.getOrNull(0) ?: ""
        val pluginId = parts.getOrNull(1)?.trim() ?: ""

        when (action) {
            "", "list" -> {
                // Список плагинов
                val plugins = pluginManager.plugins.value
                if (plugins.isEmpty()) {
                    context.addLocalOutput("\u001B[1;33m[#plugin] Плагины не загружены\u001B[0m")
                    context.addLocalOutput("\u001B[1;33m         Поместите JAR файлы в папку: ${pluginManager.pluginsDirectory.absolutePath}\u001B[0m")
                } else {
                    context.addLocalOutput("\u001B[1;36m═══ Загруженные плагины (${plugins.size}) ═══\u001B[0m")
                    plugins.forEach { plugin ->
                        val stateColor = when (plugin.state) {
                            PluginState.ENABLED -> "\u001B[1;32m"
                            PluginState.DISABLED -> "\u001B[1;33m"
                            PluginState.ERROR -> "\u001B[1;31m"
                            else -> "\u001B[0m"
                        }
                        context.addLocalOutput("  ${stateColor}${plugin.metadata.id}\u001B[0m v${plugin.metadata.version} - ${plugin.metadata.name} [${plugin.state}]")
                        if (plugin.errorMessage != null) {
                            context.addLocalOutput("    \u001B[1;31mОшибка: ${plugin.errorMessage}\u001B[0m")
                        }
                    }
                }
            }

            "reload" -> {
                if (pluginId.isEmpty()) {
                    context.addLocalOutput("\u001B[1;33m[#plugin] Использование: #plugin reload <plugin_id>\u001B[0m")
                    return
                }
                context.addLocalOutput("\u001B[1;36m[#plugin] Перезагрузка плагина '$pluginId'...\u001B[0m")
                val success = pluginManager.reloadPlugin(pluginId)
                if (success) {
                    context.addLocalOutput("\u001B[1;32m[#plugin] Плагин '$pluginId' успешно перезагружен\u001B[0m")
                } else {
                    context.addLocalOutput("\u001B[1;31m[#plugin] Не удалось перезагрузить плагин '$pluginId'\u001B[0m")
                }
            }

            "enable" -> {
                if (pluginId.isEmpty()) {
                    context.addLocalOutput("\u001B[1;33m[#plugin] Использование: #plugin enable <plugin_id>\u001B[0m")
                    return
                }
                val success = pluginManager.enablePlugin(pluginId)
                if (success) {
                    context.addLocalOutput("\u001B[1;32m[#plugin] Плагин '$pluginId' включен\u001B[0m")
                } else {
                    context.addLocalOutput("\u001B[1;31m[#plugin] Не удалось включить плагин '$pluginId'\u001B[0m")
                }
            }

            "disable" -> {
                if (pluginId.isEmpty()) {
                    context.addLocalOutput("\u001B[1;33m[#plugin] Использование: #plugin disable <plugin_id>\u001B[0m")
                    return
                }
                val success = pluginManager.disablePlugin(pluginId)
                if (success) {
                    context.addLocalOutput("\u001B[1;32m[#plugin] Плагин '$pluginId' выключен\u001B[0m")
                } else {
                    context.addLocalOutput("\u001B[1;31m[#plugin] Не удалось выключить плагин '$pluginId'\u001B[0m")
                }
            }

            "unload" -> {
                if (pluginId.isEmpty()) {
                    context.addLocalOutput("\u001B[1;33m[#plugin] Использование: #plugin unload <plugin_id>\u001B[0m")
                    return
                }
                val success = pluginManager.unloadPlugin(pluginId)
                if (success) {
                    context.addLocalOutput("\u001B[1;32m[#plugin] Плагин '$pluginId' выгружен\u001B[0m")
                } else {
                    context.addLocalOutput("\u001B[1;31m[#plugin] Не удалось выгрузить плагин '$pluginId'\u001B[0m")
                }
            }

            "load" -> {
                if (pluginId.isEmpty()) {
                    context.addLocalOutput("\u001B[1;33m[#plugin] Использование: #plugin load <filename.jar>\u001B[0m")
                    return
                }
                val jarFile = File(pluginManager.pluginsDirectory, pluginId)
                if (!jarFile.exists()) {
                    context.addLocalOutput("\u001B[1;31m[#plugin] Файл не найден: ${jarFile.absolutePath}\u001B[0m")
                    return
                }
                context.addLocalOutput("\u001B[1;36m[#plugin] Загрузка плагина из '$pluginId'...\u001B[0m")
                val loaded = pluginManager.loadPlugin(jarFile)
                if (loaded != null) {
                    pluginManager.enablePlugin(loaded.metadata.id)
                    context.addLocalOutput("\u001B[1;32m[#plugin] Плагин '${loaded.metadata.id}' загружен и включен\u001B[0m")
                } else {
                    context.addLocalOutput("\u001B[1;31m[#plugin] Не удалось загрузить плагин из '$pluginId'\u001B[0m")
                }
            }

            "info" -> {
                if (pluginId.isEmpty()) {
                    context.addLocalOutput("\u001B[1;33m[#plugin] Использование: #plugin info <plugin_id>\u001B[0m")
                    return
                }
                val plugin = pluginManager.getPlugin(pluginId)
                if (plugin == null) {
                    context.addLocalOutput("\u001B[1;31m[#plugin] Плагин '$pluginId' не найден\u001B[0m")
                    return
                }
                context.addLocalOutput("\u001B[1;36m═══ Информация о плагине ═══\u001B[0m")
                context.addLocalOutput("  ID:          ${plugin.metadata.id}")
                context.addLocalOutput("  Название:    ${plugin.metadata.name}")
                context.addLocalOutput("  Версия:      ${plugin.metadata.version}")
                context.addLocalOutput("  Автор:       ${plugin.metadata.author.ifEmpty { "не указан" }}")
                context.addLocalOutput("  Описание:    ${plugin.metadata.description.ifEmpty { "нет" }}")
                context.addLocalOutput("  Состояние:   ${plugin.state}")
                context.addLocalOutput("  JAR:         ${plugin.jarFile.name}")
                if (plugin.metadata.dependencies.isNotEmpty()) {
                    context.addLocalOutput("  Зависимости: ${plugin.metadata.dependencies.joinToString { it.id }}")
                }
            }

            "help" -> {
                context.addLocalOutput("\u001B[1;36m═══ Команды управления плагинами ═══\u001B[0m")
                context.addLocalOutput("  #plugin                    - Список плагинов")
                context.addLocalOutput("  #plugin list               - Список плагинов")
                context.addLocalOutput("  #plugin info <id>          - Информация о плагине")
                context.addLocalOutput("  #plugin reload <id>        - Перезагрузить плагин")
                context.addLocalOutput("  #plugin enable <id>        - Включить плагин")
                context.addLocalOutput("  #plugin disable <id>       - Выключить плагин")
                context.addLocalOutput("  #plugin load <file.jar>    - Загрузить плагин из файла")
                context.addLocalOutput("  #plugin unload <id>        - Выгрузить плагин")
                context.addLocalOutput("")
                context.addLocalOutput("  Папка плагинов: ${pluginManager.pluginsDirectory.absolutePath}")
            }

            else -> {
                context.addLocalOutput("\u001B[1;31m[#plugin] Неизвестная команда: $action\u001B[0m")
                context.addLocalOutput("\u001B[1;33m         Используйте #plugin help для справки\u001B[0m")
            }
        }
    }

    /**
     * Выполняет автоматическое перемещение по пути
     */
    suspend fun walkPath(path: List<Direction>) {
        for (direction in path) {
            if (!coroutineContext.isActive) break

            // Отправляем команду движения
            context.sendRaw(direction.shortName)

            // Задержка между командами (можно сделать настраиваемой)
            delay(500)
        }
    }
}
