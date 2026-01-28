package com.bylins.client.plugins.aibot

import com.bylins.client.bot.*
import com.bylins.client.bot.llm.LLMParser
import com.bylins.client.plugins.PluginBase
import com.bylins.client.plugins.TriggerResult
import com.bylins.client.plugins.events.*
import com.bylins.client.plugins.ui.PluginTab
import com.bylins.client.plugins.ui.PluginUINode
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.Duration

/**
 * AI-бот плагин для Bylins MUD Client
 *
 * Возможности:
 * - Автобой с выбором целей
 * - Автолут
 * - Навигация по карте
 * - Автобаффы
 * - LLM парсинг текста (Ollama)
 * - Сбор данных для ML
 *
 * Команды:
 *   #bot start [mode]  - Запуск (LEVELING, FARMING, EXPLORING)
 *   #bot stop          - Остановка
 *   #bot status        - Статус
 *   #bot config [key] [value] - Конфигурация
 *   #bot llm init      - Инициализация LLM
 */
class AIBotPlugin : PluginBase() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Ядро бота
    private lateinit var botCore: BotCore

    // База данных бота
    private lateinit var database: BotDatabase

    // LLM парсер
    private var llmParser: LLMParser? = null

    // Конфигурация (сохраняется между сессиями)
    private var config: AIBotConfig = AIBotConfig()

    // UI вкладка
    private var tab: PluginTab? = null

    // Логи для отображения в UI
    private val recentLogs = mutableListOf<String>()
    private val maxLogs = 100

    // Время старта сессии
    private var sessionStartTime: Instant? = null

    override fun onLoad() {
        logger.info("AI Bot плагин загружается...")

        // Загружаем сохранённую конфигурацию
        loadConfig<AIBotConfig>()?.let { config = it }

        // Инициализируем базу данных
        database = BotDatabase()
    }

    override fun onEnable() {
        logger.info("AI Bot плагин включается...")

        // Инициализируем ядро бота с PluginAPI
        botCore = BotCore(api)

        // Настраиваем callbacks
        botCore.onLog = { message ->
            addLog(message)
            if (config.verboseLogging) {
                api.echo("[BOT] $message")
            }
        }
        botCore.onError = { error ->
            addLog("ERROR: $error")
            api.echo("[BOT ERROR] $error")
        }

        // Регистрируем команду #bot
        registerBotCommand()

        // Подписываемся на события
        subscribeToEvents()

        // Инициализируем LLM если включено
        if (config.autoInitLLM) {
            initializeLLM()
        }

        // Создаём UI вкладку
        createBotTab()

        // Запускаем цикл обновления UI
        startUIUpdateLoop()

        api.echo("[AI Bot] Плагин активирован. Используйте #bot help для справки.")
    }

    override fun onDisable() {
        logger.info("AI Bot плагин выключается...")

        // Останавливаем бота если он запущен
        if (botCore.stateMachine.isActive()) {
            botCore.stop()
        }

        // Закрываем вкладку
        tab?.close()
        tab = null

        // Сохраняем конфигурацию
        saveConfig(config)

        // Отменяем корутины
        scope.cancel()
    }

    override fun onUnload() {
        logger.info("AI Bot плагин выгружается...")

        // Закрываем базу данных
        database.close()
    }

    /**
     * Регистрация команды #bot
     */
    private fun registerBotCommand() {
        api.createAlias(Regex("^#bot\\s*(.*)$")) { _, groups ->
            val args = groups.getOrNull(1)?.trim() ?: ""
            handleBotCommand(args)
            true // Команда обработана, не отправлять на сервер
        }
    }

    /**
     * Обработка команды #bot
     */
    private fun handleBotCommand(args: String) {
        val parts = args.split("\\s+".toRegex())
        if (parts.isEmpty() || parts[0].isEmpty()) {
            showHelp()
            return
        }

        when (parts[0].lowercase()) {
            "start" -> {
                val mode = if (parts.size > 1) {
                    try {
                        BotMode.valueOf(parts[1].uppercase())
                    } catch (e: Exception) {
                        api.echo("[BOT] Неизвестный режим: ${parts[1]}")
                        api.echo("[BOT] Доступные режимы: LEVELING, FARMING, EXPLORING, GATHERING, TRADING")
                        return
                    }
                } else {
                    BotMode.LEVELING
                }
                startBot(mode)
            }
            "stop" -> stopBot()
            "status" -> showStatus()
            "config" -> {
                if (parts.size > 2) {
                    setConfigValue(parts[1], parts.drop(2).joinToString(" "))
                } else if (parts.size > 1) {
                    showConfigValue(parts[1])
                } else {
                    showConfig()
                }
            }
            "llm" -> handleLLMCommand(parts.drop(1))
            "db" -> handleDBCommand(parts.drop(1))
            "help", "?" -> showHelp()
            else -> {
                api.echo("[BOT] Неизвестная команда: ${parts[0]}")
                showHelp()
            }
        }
    }

    /**
     * Запуск бота
     */
    private fun startBot(mode: BotMode) {
        if (botCore.stateMachine.isActive()) {
            api.echo("[BOT] Бот уже запущен. Используйте #bot stop для остановки.")
            return
        }

        // Применяем конфигурацию
        botCore.config.value = BotConfig(
            enabled = true,
            mode = mode,
            fleeHpPercent = config.fleeHpPercent,
            restHpPercent = config.restHpPercent,
            autoAttack = config.autoAttack,
            autoLoot = config.autoLoot,
            autoLootGold = config.autoLootGold,
            autoBuffs = config.autoBuffs,
            buffList = config.buffList,
            useLLMParsing = config.useLLMParsing && llmParser?.isAvailable() == true,
            verboseLogging = config.verboseLogging
        )

        sessionStartTime = Instant.now()
        addLog("Бот запущен в режиме $mode")
        botCore.start(mode)
        updateTabUI() // Обновить UI сразу
    }

    /**
     * Остановка бота
     */
    private fun stopBot() {
        if (!botCore.stateMachine.isActive()) {
            api.echo("[BOT] Бот не запущен.")
            return
        }
        botCore.stop()
        addLog("Бот остановлен")
        sessionStartTime = null
        updateTabUI() // Обновить UI сразу
    }

    /**
     * Показать статус бота
     */
    private fun showStatus() {
        val status = botCore.getStatus()
        val state = botCore.stateMachine.getStatus()

        api.echo("=== AI Bot Status ===")
        api.echo("Активен: ${status["running"]}")
        api.echo("Состояние: ${status["state"]}")
        api.echo("Режим: ${status["mode"]}")
        api.echo("В бою: ${state["isInCombat"]}")
        api.echo("Отдых: ${state["isResting"]}")
        api.echo("Время в состоянии: ${state["timeInState"]}ms")

        @Suppress("UNCHECKED_CAST")
        val session = status["session"] as? Map<String, Any>
        if (session?.isNotEmpty() == true) {
            api.echo("--- Сессия ---")
            api.echo("Убийств: ${session["kills"]}")
            api.echo("Смертей: ${session["deaths"]}")
            api.echo("Опыт: ${session["exp"]}")
        }

        llmParser?.let { parser ->
            api.echo("--- LLM ---")
            api.echo("Инициализирован: ${parser.isAvailable()}")
        }
    }

    /**
     * Показать конфигурацию
     */
    private fun showConfig() {
        api.echo("=== AI Bot Config ===")
        api.echo("fleeHpPercent: ${config.fleeHpPercent}")
        api.echo("restHpPercent: ${config.restHpPercent}")
        api.echo("autoAttack: ${config.autoAttack}")
        api.echo("autoLoot: ${config.autoLoot}")
        api.echo("autoLootGold: ${config.autoLootGold}")
        api.echo("autoBuffs: ${config.autoBuffs}")
        api.echo("buffList: ${config.buffList}")
        api.echo("useLLMParsing: ${config.useLLMParsing}")
        api.echo("llmModel: ${config.llmModel}")
        api.echo("verboseLogging: ${config.verboseLogging}")
        api.echo("")
        api.echo("Используйте: #bot config <key> <value> для изменения")
    }

    /**
     * Показать значение конфига
     */
    private fun showConfigValue(key: String) {
        val value = when (key.lowercase()) {
            "fleehppercent", "fleehp" -> config.fleeHpPercent
            "resthppercent", "resthp" -> config.restHpPercent
            "autoattack" -> config.autoAttack
            "autoloot" -> config.autoLoot
            "autolootgold" -> config.autoLootGold
            "autobuffs" -> config.autoBuffs
            "bufflist" -> config.buffList.joinToString(", ")
            "usellmparsing", "llm" -> config.useLLMParsing
            "llmmodel" -> config.llmModel
            "llmurl" -> config.llmUrl
            "verboselogging", "verbose" -> config.verboseLogging
            else -> {
                api.echo("[BOT] Неизвестный ключ: $key")
                return
            }
        }
        api.echo("[BOT] $key = $value")
    }

    /**
     * Установить значение конфига
     */
    private fun setConfigValue(key: String, value: String) {
        when (key.lowercase()) {
            "fleehppercent", "fleehp" -> config.fleeHpPercent = value.toIntOrNull() ?: config.fleeHpPercent
            "resthppercent", "resthp" -> config.restHpPercent = value.toIntOrNull() ?: config.restHpPercent
            "autoattack" -> config.autoAttack = value.toBooleanStrictOrNull() ?: config.autoAttack
            "autoloot" -> config.autoLoot = value.toBooleanStrictOrNull() ?: config.autoLoot
            "autolootgold" -> config.autoLootGold = value.toBooleanStrictOrNull() ?: config.autoLootGold
            "autobuffs" -> config.autoBuffs = value.toBooleanStrictOrNull() ?: config.autoBuffs
            "bufflist" -> config.buffList = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            "usellmparsing", "llm" -> config.useLLMParsing = value.toBooleanStrictOrNull() ?: config.useLLMParsing
            "llmmodel" -> config.llmModel = value
            "llmurl" -> config.llmUrl = value
            "verboselogging", "verbose" -> config.verboseLogging = value.toBooleanStrictOrNull() ?: config.verboseLogging
            else -> {
                api.echo("[BOT] Неизвестный ключ: $key")
                return
            }
        }
        api.echo("[BOT] $key = $value")
        saveConfig(config)
    }

    /**
     * Обработка команд LLM
     */
    private fun handleLLMCommand(args: List<String>) {
        when (args.getOrNull(0)?.lowercase()) {
            "init" -> {
                val url = args.getOrNull(1) ?: config.llmUrl
                val model = args.getOrNull(2) ?: config.llmModel
                config.llmUrl = url
                config.llmModel = model
                initializeLLM()
            }
            "status" -> {
                val status = llmParser?.getStatus() ?: mapOf("initialized" to false)
                api.echo("[BOT LLM] Статус: $status")
            }
            "test" -> {
                if (llmParser?.isAvailable() != true) {
                    api.echo("[BOT LLM] LLM не инициализирован. Используйте #bot llm init")
                    return
                }
                val testText = args.drop(1).joinToString(" ").ifEmpty { "Злобный орк стоит здесь." }
                scope.launch {
                    val result = llmParser?.parseRoomDescription(testText)
                    api.echo("[BOT LLM] Результат парсинга: $result")
                }
            }
            else -> {
                api.echo("[BOT LLM] Команды:")
                api.echo("  #bot llm init [url] [model] - инициализация")
                api.echo("  #bot llm status - статус")
                api.echo("  #bot llm test [текст] - тест парсинга")
            }
        }
    }

    /**
     * Обработка команд базы данных
     */
    private fun handleDBCommand(args: List<String>) {
        when (args.getOrNull(0)?.lowercase()) {
            "mobs" -> {
                val pattern = args.getOrNull(1) ?: ""
                if (pattern.isEmpty()) {
                    api.echo("[BOT DB] Использование: #bot db mobs <паттерн>")
                    return
                }
                val mobs = database.findMobsByName(pattern)
                if (mobs.isEmpty()) {
                    api.echo("[BOT DB] Мобы не найдены: $pattern")
                } else {
                    api.echo("[BOT DB] Найдено мобов: ${mobs.size}")
                    mobs.take(10).forEach { mob ->
                        api.echo("  ${mob.name} (lvl:${mob.level ?: "?"}, kills:${mob.killCount})")
                    }
                }
            }
            "zones" -> {
                val level = args.getOrNull(1)?.toIntOrNull() ?: 0
                if (level <= 0) {
                    api.echo("[BOT DB] Использование: #bot db zones <уровень>")
                    return
                }
                val zones = database.getZonesForLevel(level)
                if (zones.isEmpty()) {
                    api.echo("[BOT DB] Зоны для уровня $level не найдены")
                } else {
                    api.echo("[BOT DB] Зоны для уровня $level:")
                    zones.forEach { zone ->
                        api.echo("  ${zone.zoneId} (${zone.levelMin}-${zone.levelMax}, exp/h:${zone.avgExpPerHour?.toInt() ?: "?"})")
                    }
                }
            }
            else -> {
                api.echo("[BOT DB] Команды:")
                api.echo("  #bot db mobs <паттерн> - поиск мобов")
                api.echo("  #bot db zones <уровень> - зоны для уровня")
            }
        }
    }

    /**
     * Инициализация LLM
     */
    private fun initializeLLM() {
        api.echo("[BOT] Инициализация LLM (${config.llmModel} @ ${config.llmUrl})...")

        scope.launch {
            llmParser = LLMParser(config.llmUrl, config.llmModel)
            if (llmParser?.initialize() == true) {
                api.echo("[BOT] LLM инициализирован успешно")
            } else {
                api.echo("[BOT] Не удалось инициализировать LLM. Проверьте что Ollama запущен.")
            }
        }
    }

    /**
     * Подписка на события
     */
    private fun subscribeToEvents() {
        // Обработка входящих строк
        api.subscribe(LineReceivedEvent::class.java, EventPriority.NORMAL) { event ->
            if (botCore.stateMachine.isActive()) {
                botCore.processLine(event.line)
            }
        }

        // Обработка входа в комнату
        api.subscribe(RoomEnterEvent::class.java, EventPriority.NORMAL) { event ->
            if (botCore.stateMachine.isActive()) {
                val room = api.getRoom(event.roomId) ?: return@subscribe
                botCore.onRoomEnter(room)
            }
        }

        // Обработка MSDP данных (для обновления состояния персонажа)
        api.subscribe(MsdpEvent::class.java, EventPriority.NORMAL) { _ ->
            // MSDP данные автоматически обновляются, бот их читает через getMsdpValue
        }
    }

    /**
     * Показать справку
     */
    private fun showHelp() {
        api.echo("""
            === AI Bot Help ===
            #bot start [mode]  - Запуск бота
               Режимы: LEVELING, FARMING, EXPLORING, GATHERING, TRADING
            #bot stop          - Остановка бота
            #bot status        - Показать статус
            #bot config        - Показать конфигурацию
            #bot config <key> <value> - Изменить настройку
            #bot llm init [url] [model] - Инициализировать LLM
            #bot llm status    - Статус LLM
            #bot db mobs <name> - Поиск мобов в базе
            #bot db zones <lvl> - Зоны для уровня
            #bot help          - Эта справка
        """.trimIndent())
    }

    // ============================================
    // UI вкладка
    // ============================================

    /**
     * Добавить запись в логи
     */
    private fun addLog(message: String) {
        val timestamp = java.time.LocalTime.now().toString().substringBefore('.')
        synchronized(recentLogs) {
            recentLogs.add("[$timestamp] $message")
            while (recentLogs.size > maxLogs) {
                recentLogs.removeAt(0)
            }
        }
    }

    /**
     * Создать UI вкладку бота
     */
    private fun createBotTab() {
        tab = api.createTab("main", "AI Bot")
        updateTabUI()
    }

    /**
     * Запустить цикл обновления UI
     */
    private fun startUIUpdateLoop() {
        scope.launch {
            while (isActive) {
                updateTabUI()
                delay(1000) // Обновление раз в секунду
            }
        }
    }

    /**
     * Обновить содержимое вкладки
     */
    private fun updateTabUI() {
        val currentTab = tab ?: return
        val isRunning = botCore.stateMachine.isActive()
        val status = botCore.getStatus()
        val state = botCore.stateMachine.getStatus()

        @Suppress("UNCHECKED_CAST")
        val session = status["session"] as? Map<String, Any>
        val sessionDuration = sessionStartTime?.let {
            Duration.between(it, Instant.now())
        }

        val logsList = synchronized(recentLogs) { recentLogs.toList() }

        currentTab.content.value = PluginUINode.Column(
            children = listOf(
                // Заголовок и статус
                PluginUINode.Row(
                    children = listOf(
                        PluginUINode.Text(
                            "AI Bot",
                            PluginUINode.TextStyle.TITLE
                        ),
                        PluginUINode.Text(
                            if (isRunning) "[АКТИВЕН]" else "[ОСТАНОВЛЕН]",
                            PluginUINode.TextStyle.SUBTITLE
                        )
                    ),
                    spacing = 16
                ),
                PluginUINode.Divider(),

                // Кнопки управления
                PluginUINode.Row(
                    children = listOf(
                        PluginUINode.Button(
                            text = if (isRunning) "Стоп" else "Старт",
                            onClick = {
                                // Проверяем текущее состояние в момент клика, а не захваченное
                                if (botCore.stateMachine.isActive()) {
                                    stopBot()
                                } else {
                                    startBot(botCore.config.value.mode)
                                }
                            }
                        ),
                        PluginUINode.Dropdown(
                            selectedIndex = BotMode.entries.indexOf(botCore.config.value.mode).coerceAtLeast(0),
                            options = BotMode.entries.map { it.name },
                            onSelect = { index ->
                                val mode = BotMode.entries.getOrNull(index) ?: BotMode.LEVELING
                                // Проверяем в момент клика
                                if (!botCore.stateMachine.isActive()) {
                                    startBot(mode)
                                } else {
                                    // Если бот уже запущен, просто сохраняем выбранный режим
                                    botCore.config.value = botCore.config.value.copy(mode = mode)
                                    addLog("Режим изменён на $mode (применится после перезапуска)")
                                }
                            },
                            label = "Режим"
                        )
                    ),
                    spacing = 8
                ),
                PluginUINode.Spacer(8),

                // Информация о состоянии
                PluginUINode.Text("Состояние: ${status["state"]}", PluginUINode.TextStyle.BODY),
                PluginUINode.Text("Режим: ${status["mode"]}", PluginUINode.TextStyle.BODY),
                if (state["isInCombat"] == true) {
                    PluginUINode.Text("В БОЮ", PluginUINode.TextStyle.SUBTITLE)
                } else {
                    PluginUINode.Empty
                },
                PluginUINode.Spacer(8),

                // Статистика сессии
                if (session != null) {
                    PluginUINode.Column(
                        children = listOf(
                            PluginUINode.Text("Сессия:", PluginUINode.TextStyle.SUBTITLE),
                            PluginUINode.Text("  Время: ${formatDuration(sessionDuration)}", PluginUINode.TextStyle.CAPTION),
                            PluginUINode.Text("  Убийств: ${session["kills"] ?: 0}", PluginUINode.TextStyle.CAPTION),
                            PluginUINode.Text("  Смертей: ${session["deaths"] ?: 0}", PluginUINode.TextStyle.CAPTION),
                            PluginUINode.Text("  Опыт: ${session["exp"] ?: 0}", PluginUINode.TextStyle.CAPTION)
                        ),
                        spacing = 2
                    )
                } else {
                    PluginUINode.Empty
                },
                PluginUINode.Divider(),

                // Настройки
                PluginUINode.Text("Настройки:", PluginUINode.TextStyle.SUBTITLE),
                PluginUINode.Slider(
                    value = config.fleeHpPercent / 100f,
                    onValueChange = { v ->
                        config.fleeHpPercent = (v * 100).toInt()
                        saveConfig(config)
                    },
                    range = 0f..1f,
                    label = "Flee HP"
                ),
                PluginUINode.Checkbox(
                    checked = config.autoLoot,
                    onCheckedChange = { checked ->
                        config.autoLoot = checked
                        saveConfig(config)
                    },
                    label = "Автолут"
                ),
                PluginUINode.Checkbox(
                    checked = config.autoBuffs,
                    onCheckedChange = { checked ->
                        config.autoBuffs = checked
                        saveConfig(config)
                    },
                    label = "Автобаффы"
                ),
                PluginUINode.Checkbox(
                    checked = config.verboseLogging,
                    onCheckedChange = { checked ->
                        config.verboseLogging = checked
                        saveConfig(config)
                    },
                    label = "Подробные логи"
                ),
                PluginUINode.Divider(),

                // Логи
                PluginUINode.Text("Логи:", PluginUINode.TextStyle.SUBTITLE),
                PluginUINode.Scrollable(
                    child = PluginUINode.Column(
                        children = logsList.takeLast(20).reversed().map { log ->
                            PluginUINode.Text(log, PluginUINode.TextStyle.MONOSPACE)
                        },
                        spacing = 2
                    ),
                    maxHeight = 200
                )
            ),
            spacing = 4
        )
    }

    /**
     * Форматировать продолжительность
     */
    private fun formatDuration(duration: Duration?): String {
        if (duration == null) return "0:00"
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}

/**
 * Конфигурация плагина (сохраняется в JSON)
 */
@Serializable
data class AIBotConfig(
    var fleeHpPercent: Int = 20,
    var restHpPercent: Int = 70,
    var autoAttack: Boolean = true,
    var autoLoot: Boolean = true,
    var autoLootGold: Boolean = true,
    var autoBuffs: Boolean = true,
    var buffList: List<String> = emptyList(),
    var useLLMParsing: Boolean = false,
    var llmModel: String = "llama3",
    var llmUrl: String = "http://localhost:11434",
    var autoInitLLM: Boolean = false,
    var verboseLogging: Boolean = false
)
