package com.bylins.client.plugins.aibot

import com.bylins.client.bot.*
import com.bylins.client.plugins.PluginBase
import com.bylins.client.plugins.events.*
import com.bylins.client.plugins.ui.PluginTab
import com.bylins.client.plugins.ui.PluginUINode
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable

/**
 * AI-бот плагин для Bylins MUD Client
 *
 * Возможности:
 * - Определение промпта по таймауту
 * - Парсинг статов из промпта через regex
 *
 * Команды:
 *   #bot status          - Статус
 *   #bot regex           - Показать текущий regex
 *   #bot regex <pattern> - Установить regex для парсинга промпта
 *   #bot help            - Справка
 */
class AIBotPlugin : PluginBase() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Ядро бота
    private lateinit var botCore: BotCore

    // Конфигурация
    private var config: AIBotConfig = AIBotConfig()

    // UI вкладка
    private var tab: PluginTab? = null

    override fun onLoad() {
        logger.info("AI Bot плагин загружается...")
        loadConfig<AIBotConfig>()?.let { config = it }
    }

    override fun onEnable() {
        logger.info("AI Bot плагин включается...")

        // Инициализируем ядро бота
        botCore = BotCore(api)

        // Настраиваем callbacks
        botCore.onLog = { message -> addLog(message) }

        // Регистрируем команду #bot
        registerBotCommand()

        // Подписываемся на события
        subscribeToEvents()

        // Загружаем сохранённый regex
        loadPromptRegex()

        // Создаём output tab для логов
        scope.launch {
            delay(100)
            api.createOutputTab("logs", "AI Bot")
        }

        // Создаём UI вкладку
        createBotTab()

        // Запускаем цикл определения промпта
        startPromptDetectionLoop()

        addLog("Плагин активирован")
    }

    override fun onDisable() {
        logger.info("AI Bot плагин выключается...")

        tab?.close()
        tab = null
        api.closeOutputTab("logs")
        saveConfig(config)
        scope.cancel()
    }

    override fun onUnload() {
        logger.info("AI Bot плагин выгружается...")
        botCore.shutdown()
    }

    private fun registerBotCommand() {
        api.createAlias(Regex("^#bot\\s*(.*)$")) { _, groups ->
            val args = groups.getOrNull(1)?.trim() ?: ""
            handleBotCommand(args)
            true
        }
    }

    private fun handleBotCommand(args: String) {
        val parts = args.split("\\s+".toRegex(), 2)
        if (parts.isEmpty() || parts[0].isEmpty()) {
            showHelp()
            return
        }

        when (parts[0].lowercase()) {
            "status" -> showStatus()
            "regex" -> {
                if (parts.size > 1) {
                    setPromptRegex(parts[1])
                } else {
                    showPromptRegex()
                }
            }
            "help", "?" -> showHelp()
            else -> {
                api.echo("[BOT] Неизвестная команда: ${parts[0]}")
                showHelp()
            }
        }
    }

    private fun showStatus() {
        val status = botCore.getStatus()

        api.echo("=== AI Bot Status ===")

        @Suppress("UNCHECKED_CAST")
        val promptStatus = status["promptDetector"] as? Map<String, Any>
        if (promptStatus != null) {
            api.echo("Промптов обнаружено: ${promptStatus["promptCount"]}")
            api.echo("Последний промпт: ${promptStatus["lastPrompt"]}")
            api.echo("Regex установлен: ${promptStatus["hasPattern"]}")
        }
    }

    private fun showPromptRegex() {
        api.echo("=== Prompt Regex ===")
        if (config.promptRegex.isBlank()) {
            api.echo("Regex не установлен")
            api.echo("Установить: #bot regex <pattern>")
            api.echo("Пример: #bot regex ^(?<hp>\\d+)H (?<moves>\\d+)M>$")
        } else {
            api.echo("Текущий regex: ${config.promptRegex}")
            api.echo("Именованные группы парсятся в переменные _prompt_<name>")
        }
    }

    private fun setPromptRegex(pattern: String) {
        config.promptRegex = pattern
        saveConfig(config)

        if (pattern.isBlank()) {
            botCore.promptDetector.setPromptPattern(null)
            return
        }

        try {
            val regex = Regex(pattern)
            botCore.promptDetector.setPromptPattern(regex)
            addLog("Regex установлен: $pattern")
        } catch (e: Exception) {
            addLog("Ошибка в regex: ${e.message}")
        }
    }

    private fun loadPromptRegex() {
        if (config.promptRegex.isNotBlank()) {
            try {
                val regex = Regex(config.promptRegex)
                botCore.promptDetector.setPromptPattern(regex)
                addLog("Загружен regex: ${config.promptRegex}")
            } catch (e: Exception) {
                addLog("ERROR: Невалидный regex в конфиге: ${e.message}")
            }
        }
    }

    private fun subscribeToEvents() {
        // Обработка входящих строк
        api.subscribe(LineReceivedEvent::class.java, EventPriority.NORMAL) { event ->
            botCore.processLine(event.rawLine, event.timestamp)
        }
    }

    private fun showHelp() {
        api.echo("""
            === AI Bot Help ===
            #bot status           - Показать статус
            #bot regex            - Показать текущий regex промпта
            #bot regex <pattern>  - Установить regex для парсинга промпта
            #bot help             - Эта справка

            Пример regex: ^(?<hp>\d+)H (?<moves>\d+)M (?<gold>\d+)G (?<exits>\S+)>$
            Именованные группы сохраняются в переменные _prompt_<name>
        """.trimIndent())
    }

    // ============================================
    // UI
    // ============================================

    private fun addLog(message: String) {
        val timestamp = java.time.LocalTime.now().toString().substringBefore('.')
        // Убираем ANSI коды из логов
        val cleanMessage = message.replace(Regex("\u001B\\[[0-9;]*m"), "")
        val formatted = "[$timestamp] $cleanMessage"
        api.appendToOutputTab("logs", formatted)
    }

    private fun createBotTab() {
        tab = api.createTab("main", "AI Bot")
        updateTabUI()
    }

    private fun startPromptDetectionLoop() {
        scope.launch {
            while (isActive) {
                botCore.checkPromptTimeout(System.currentTimeMillis())
                delay(100)
            }
        }
    }

    private fun updateTabUI() {
        val currentTab = tab ?: return
        val recentPrompts = botCore.promptDetector.getRecentPrompts(5)

        currentTab.content.value = PluginUINode.Scrollable(
            child = PluginUINode.Column(
                children = listOf(
                    // Заголовок
                    PluginUINode.Text("AI Bot", PluginUINode.TextStyle.TITLE),
                    PluginUINode.Divider(),

                    // Параметры
                    PluginUINode.Text("Параметры:", PluginUINode.TextStyle.SUBTITLE),
                    PluginUINode.TextField(
                        value = config.llmModel,
                        onValueChange = { model ->
                            config.llmModel = model
                            saveConfig(config)
                        },
                        placeholder = "LLM модель (llama3)"
                    ),
                    PluginUINode.TextField(
                        value = config.promptRegex,
                        onValueChange = { pattern ->
                            setPromptRegex(pattern)
                        },
                        placeholder = "Prompt regex: ^(?<hp>\\d+)H (?<moves>\\d+)M>$"
                    ),
                    PluginUINode.Divider(),

                    // Последние промпты
                    PluginUINode.Text("Последние промпты:", PluginUINode.TextStyle.SUBTITLE),
                    if (recentPrompts.isNotEmpty()) {
                        PluginUINode.Column(
                            children = recentPrompts.map { prompt ->
                                PluginUINode.SelectableText(
                                    text = prompt,
                                    style = PluginUINode.TextStyle.MONOSPACE
                                )
                            },
                            spacing = 2
                        )
                    } else {
                        PluginUINode.Text(
                            "Промпты ещё не обнаружены",
                            PluginUINode.TextStyle.CAPTION
                        )
                    }
                ),
                spacing = 4
            )
        )
    }
}

/**
 * Конфигурация плагина
 */
@Serializable
data class AIBotConfig(
    var verboseLogging: Boolean = false,
    // Regex для парсинга промпта (используй именованные группы: (?<hp>\d+) и т.д.)
    var promptRegex: String = "",
    // LLM настройки
    var llmModel: String = "llama3",
    var llmBaseUrl: String = "http://localhost:11434"
)
