package com.bylins.client.plugins.aibot

import com.bylins.client.bot.*
import com.bylins.client.bot.perception.CharacterScore
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
 * - Автоматическая работа с картой через MSDP
 *
 * Команды:
 *   #bot status          - Статус
 *   #bot regex           - Показать текущий regex
 *   #bot regex <pattern> - Установить regex для парсинга промпта
 *   #bot help            - Справка
 */
class AIBotPlugin : PluginBase() {

    companion object {
        // Дефолтный regex для Былин
        // Примеры промптов:
        //   478H 258M 13179529o Зауч:0 ОЗ:0 28L 346G Вых:v>
        //   520H 220M э4400 ??? ОЗ:0 34L 0G Вых:СВЮv>
        //   478H 258M 28L 346G Вых:v>  (без опыта)
        //   478H 258M Вых:v>  (только HP и Move)
        // Все части опциональны, матчим что есть
        // [oо] - и Latin o и Cyrillic о
        // (?:(?!\d+[LG]\b)[^\s>]+\s*)* - любые токены кроме level/gold, не захватывая >
        const val DEFAULT_PROMPT_REGEX = """(?:(?<hp>\d+)H\s*)?(?:(?<move>\d+)M\s*)?(?:э?(?<exp>\d+)[oо]?\s*)?(?:(?!\d+[LG]\b)[^\s>]+\s*)*(?:(?<level>\d+)L\s*)?(?:(?<gold>\d+)G\s*)?.*>"""
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Ядро бота
    private lateinit var botCore: BotCore

    // Конфигурация
    private var config: AIBotConfig = AIBotConfig()

    // UI вкладка
    private var tab: PluginTab? = null

    // MSDP состояние
    private var msdpInitialized = false
    private val msdpVariables = listOf("ROOM", "STATE", "MAX_HIT", "MAX_MOVE", "LEVEL", "EXPERIENCE", "GOLD")

    // Статус-бары инициализированы (чтобы не создавать дважды)
    private var statusBarsInitialized = false

    // Кэш последних значений для статуса
    private var lastLevel: Int = 0
    private var lastMaxHp: Int = 100
    private var lastMaxMove: Int = 100

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
        botCore.onPromptParsed = { _, parsed -> handlePromptParsed(parsed) }
        botCore.scoreParser.onScoreParsed = { score -> handleScoreParsed(score) }
        botCore.onTextBlock = { text -> tryParseLevelTable(text) }

        // Регистрируем команду #bot
        registerBotCommand()

        // Подписываемся на события
        subscribeToEvents()

        // Создаём output tab для логов (синхронно)
        val tabCreated = api.createOutputTab("logs", "Ассистент")
        logger.info("Output tab created: $tabCreated")

        // Загружаем сохранённый regex (после создания вкладки логов)
        loadPromptRegex()

        // Создаём UI вкладку
        createBotTab()

        // Запускаем цикл определения промпта
        startPromptDetectionLoop()

        // Проверяем, включён ли уже MSDP (перезагрузка плагина)
        if (api.isMsdpEnabled()) {
            initializeMsdp()
        }

        addLog("Плагин активирован")
    }

    override fun onDisable() {
        logger.info("AI Bot плагин выключается...")

        tab?.close()
        tab = null
        api.closeOutputTab("logs")
        saveConfig(config)
        scope.cancel()

        // Очищаем статус-элементы плагина
        cleanupStatusElements()
        statusBarsInitialized = false

        // MSDP отписки происходят автоматически в cleanup()
    }

    /**
     * Удаляет все статус-элементы созданные этим плагином.
     */
    private fun cleanupStatusElements() {
        // Удаляем группы
        api.removeStatus("vitals")
        api.removeStatus("wealth")
        api.removeStatus("character")
        api.removeStatus("stats")
        api.removeStatus("size")
        api.removeStatus("combat")
        api.removeStatus("magic")
        api.removeStatus("saves")
        api.removeStatus("resists")
        api.removeStatus("minimap")
    }

    override fun onUnload() {
        logger.info("AI Bot плагин выгружается...")
        botCore.shutdown()
    }

    // ============================================
    // MSDP
    // ============================================

    private fun initializeMsdp() {
        if (msdpInitialized) return

        addLog("MSDP включён, запрашиваем список переменных...")
        api.requestMsdpReportableVariables()
    }

    private fun onMsdpReportableVariables(variables: List<String>) {
        if (msdpInitialized) return
        msdpInitialized = true

        val available = variables.toSet()
        val toSubscribe = msdpVariables.filter { it in available }

        addLog("Доступно ${variables.size} MSDP переменных, подписываемся на ${toSubscribe.size}")

        // Подписываемся на нужные переменные
        for (varName in toSubscribe) {
            api.subscribeMsdp(varName)
        }

        // Если есть ROOM - включаем маппер и добавляем миникарту
        if ("ROOM" in available) {
            api.setMapEnabled(true)
            api.addMiniMap("minimap", null, true, 0)  // order=0 - миникарта вверху
            addLog("Маппер и миникарта включены")
        }

        // Создаём элементы статус-панели (MSDP инициализирует их)
        ensureStatusBarsInitialized()

        // Запрашиваем текущие значения
        scope.launch {
            delay(500)
            for (varName in toSubscribe) {
                api.sendMsdpRequest(varName)
                delay(100)
            }
        }
    }

    private fun onMsdpData(variable: String, value: Any) {
        when (variable) {
            "ROOM" -> handleRoomData(value)
            "STATE" -> handleStateData(value)
            "LEVEL" -> handleLevelData(value)
            "EXPERIENCE" -> handleExperienceData(value)
            "GOLD" -> handleGoldData(value)
            "MAX_HIT" -> handleMaxHitData(value)
            "MAX_MOVE" -> handleMaxMoveData(value)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleRoomData(value: Any) {
        if (value !is Map<*, *>) return
        val roomData = value as Map<String, Any>

        val vnum = roomData["VNUM"]?.toString() ?: return
        val name = roomData["NAME"]?.toString() ?: "Unknown"
        val zone = roomData["ZONE"]?.toString()
        val area = roomData["AREA"]?.toString()
        val terrain = roomData["TERRAIN"]?.toString()
        val exitsRaw = roomData["EXITS"]

        val exits = mutableMapOf<String, String>()
        if (exitsRaw is Map<*, *>) {
            for ((dir, target) in exitsRaw) {
                exits[dir.toString().lowercase()] = target.toString()
            }
        }

        // Отправляем данные в маппер
        api.handleRoomFromMsdp(
            vnum = vnum,
            name = name,
            zone = zone,
            area = area,
            terrain = terrain,
            exits = exits
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleStateData(value: Any) {
        if (value !is Map<*, *>) return
        val state = value as Map<String, Any>

        state["CURRENT_HP"]?.toString()?.toIntOrNull()?.let { hp ->
            api.updateStatusBar("hp", hp)
        }
        state["CURRENT_MOVE"]?.toString()?.toIntOrNull()?.let { move ->
            api.updateStatusBar("move", move)
        }
    }

    private fun handleMaxHitData(value: Any) {
        value.toString().toIntOrNull()?.let { maxHp ->
            lastMaxHp = maxHp
            api.updateStatusBarMax("hp", maxHp)
        }
    }

    private fun handleMaxMoveData(value: Any) {
        value.toString().toIntOrNull()?.let { maxMove ->
            lastMaxMove = maxMove
            api.updateStatusBarMax("move", maxMove)
        }
    }

    private fun handleLevelData(value: Any) {
        val level = value.toString().toIntOrNull() ?: return
        if (level != lastLevel && level > 0) {
            lastLevel = level
            updateLevelStatus(level)
        }
    }

    private fun handleExperienceData(value: Any) {
        val exp = value.toString().toLongOrNull() ?: return
        val expInfo = getExpToLevel(exp)
        api.updateStatusBar("exp", expInfo.current.toInt(), expInfo.max.toInt())

        // Обновляем уровень если изменился (из опыта можно вычислить уровень)
        if (expInfo.level != lastLevel && expInfo.level > 0) {
            lastLevel = expInfo.level
            updateLevelStatus(expInfo.level)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleGoldData(value: Any) {
        if (value !is Map<*, *>) return
        val gold = value as Map<String, Any>
        val pocket = gold["POCKET"]?.toString()?.toIntOrNull() ?: 0
        val bank = gold["BANK"]?.toString()?.toIntOrNull() ?: 0
        api.updateStatusText("gold", "в кармане: ${formatNumber(pocket)} / в банке: ${formatNumber(bank)}")
    }

    // ============================================
    // Experience table (динамическая, парсится из вывода игры)
    // ============================================

    private data class ExpInfo(val remain: Long, val current: Long, val max: Long, val level: Int, val immortal: Boolean)

    // Таблица уровней: level -> (minExp, maxExpPerMob)
    private data class LevelData(val minExp: Long, val maxExpPerMob: Int)

    // Дефолтные таблицы для разных ремортов
    private val defaultLevelTables: Map<Int, Map<Int, LevelData>> = mapOf(
        // Реморт 0
        0 to mapOf(
            1 to LevelData(1L, 149),
            2 to LevelData(1_500L, 150),
            3 to LevelData(3_000L, 300),
            4 to LevelData(6_000L, 600),
            5 to LevelData(12_000L, 1_000),
            6 to LevelData(22_000L, 2_000),
            7 to LevelData(42_000L, 3_500),
            8 to LevelData(77_000L, 5_000),
            9 to LevelData(127_000L, 7_000),
            10 to LevelData(197_000L, 9_000),
            11 to LevelData(287_000L, 12_000),
            12 to LevelData(407_000L, 15_000),
            13 to LevelData(557_000L, 21_000),
            14 to LevelData(767_000L, 24_000),
            15 to LevelData(1_007_000L, 27_300),
            16 to LevelData(1_280_000L, 29_000),
            17 to LevelData(1_570_000L, 34_000),
            18 to LevelData(1_910_000L, 39_000),
            19 to LevelData(2_300_000L, 49_000),
            20 to LevelData(2_790_000L, 99_000),
            21 to LevelData(3_780_000L, 129_000),
            22 to LevelData(5_070_000L, 149_000),
            23 to LevelData(6_560_000L, 169_000),
            24 to LevelData(8_250_000L, 199_000),
            25 to LevelData(10_240_000L, 276_000),
            26 to LevelData(13_000_000L, 700_000),
            27 to LevelData(20_000_000L, 1_000_000),
            28 to LevelData(30_000_000L, 1_300_000),
            29 to LevelData(43_000_000L, 1_600_000),
            30 to LevelData(59_000_000L, 2_000_000),
            31 to LevelData(80_000_000L, 0)
        ),
        // Реморт 1
        1 to mapOf(
            1 to LevelData(1L, 151),
            2 to LevelData(1_666L, 151),
            3 to LevelData(3_333L, 303),
            4 to LevelData(6_666L, 606),
            5 to LevelData(13_333L, 1_010),
            6 to LevelData(24_444L, 2_020),
            7 to LevelData(46_666L, 3_535),
            8 to LevelData(85_555L, 5_050),
            9 to LevelData(141_111L, 7_070),
            10 to LevelData(218_888L, 9_090),
            11 to LevelData(318_888L, 12_121),
            12 to LevelData(452_222L, 15_151),
            13 to LevelData(618_888L, 21_212),
            14 to LevelData(852_222L, 24_242),
            15 to LevelData(1_118_889L, 27_575),
            16 to LevelData(1_422_222L, 29_292),
            17 to LevelData(1_744_444L, 34_343),
            18 to LevelData(2_122_222L, 39_393),
            19 to LevelData(2_555_555L, 49_495),
            20 to LevelData(3_100_000L, 100_000),
            21 to LevelData(4_200_000L, 130_303),
            22 to LevelData(5_633_333L, 150_505),
            23 to LevelData(7_288_889L, 170_707),
            24 to LevelData(9_166_667L, 201_010),
            25 to LevelData(11_377_778L, 278_787),
            26 to LevelData(14_444_445L, 707_070),
            27 to LevelData(22_222_224L, 1_010_100),
            28 to LevelData(33_333_334L, 1_313_131),
            29 to LevelData(47_777_780L, 1_616_161),
            30 to LevelData(65_555_560L, 2_020_202),
            31 to LevelData(87_777_784L, 0)
        )
    )

    // Текущая таблица уровней (может быть перезаписана из парсинга)
    private var levelTable = mutableMapOf<Int, LevelData>().apply {
        putAll(defaultLevelTables[0]!!)
    }

    // Текущий реморт персонажа (для выбора дефолтной таблицы)
    private var currentRemort: Int = 0

    // Regex для парсинга таблицы уровней из вывода "таблица уровней"
    // Формат: [ 1]             1-1,499         149
    // или:    [10]       197,000-286,999       9,000
    private val levelTablePattern = Regex(
        """\[\s*(\d+)\]\s+([0-9,]+)-([0-9,]+)\s+([0-9,]+)"""
    )

    // Флаг: таблица была спарсена из игры
    private var levelTableParsed = false

    /**
     * Выбирает дефолтную таблицу для указанного реморта.
     * Если точного совпадения нет - берёт ближайший.
     */
    private fun selectDefaultTableForRemort(remort: Int) {
        if (levelTableParsed) {
            // Таблица уже спарсена из игры - не перезаписываем
            return
        }

        currentRemort = remort

        // Ищем точное совпадение или ближайший реморт
        val availableRemorts = defaultLevelTables.keys.sorted()
        val selectedRemort = availableRemorts.minByOrNull { kotlin.math.abs(it - remort) } ?: 0

        val table = defaultLevelTables[selectedRemort]
        if (table != null) {
            levelTable.clear()
            levelTable.putAll(table)
            if (selectedRemort != remort) {
                addLog("Реморт $remort: используется таблица для реморта $selectedRemort (ближайший)")
            } else {
                addLog("Реморт $remort: загружена дефолтная таблица")
            }
        }
    }

    private fun getExpToLevel(curExp: Long): ExpInfo {
        val sortedLevels = levelTable.entries.sortedByDescending { it.value.minExp }

        for (entry in sortedLevels) {
            val level = entry.key
            val data = entry.value

            if (curExp >= data.minExp) {
                if (level == 31) {
                    return ExpInfo(0, 1, 1, level, true)
                }

                val nextLevelData = levelTable[level + 1] ?: return ExpInfo(0, 1, 1, level, true)
                val expInLevel = curExp - data.minExp
                val expNeeded = nextLevelData.minExp - data.minExp
                val remain = nextLevelData.minExp - curExp

                return ExpInfo(remain, expInLevel, expNeeded, level, false)
            }
        }

        val firstLevelData = levelTable[1]!!
        return ExpInfo(firstLevelData.minExp - curExp, curExp, firstLevelData.minExp, 0, false)
    }

    /**
     * Парсит таблицу уровней из вывода игры
     * Формат: [ 1]             1-1,499         149
     */
    private fun tryParseLevelTable(text: String): Boolean {
        // Ищем заголовок таблицы
        if (!text.contains("Уровень") || !text.contains("Опыт")) {
            return false
        }

        val matches = levelTablePattern.findAll(text).toList()
        if (matches.isEmpty()) {
            return false
        }

        // Парсим строки таблицы
        val newTable = mutableMapOf<Int, LevelData>()
        for (match in matches) {
            val level = match.groupValues[1].toIntOrNull() ?: continue
            val minExp = match.groupValues[2].replace(",", "").toLongOrNull() ?: continue
            // groupValues[3] - maxExp для уровня, пропускаем
            val maxPerMob = match.groupValues[4].replace(",", "").toIntOrNull() ?: continue

            newTable[level] = LevelData(minExp, maxPerMob)
        }

        if (newTable.size >= 10) {
            // Достаточно данных - обновляем таблицу
            levelTable.clear()
            levelTable.putAll(newTable)
            levelTableParsed = true
            addLog("Таблица уровней обновлена: ${newTable.size} уровней (реморт-зависимая)")
            return true
        }

        return false
    }

    private fun getMaxExpPerMob(level: Int): Int {
        return levelTable[level]?.maxExpPerMob ?: 0
    }

    /**
     * Получить количество опыта, необходимое для перехода с указанного уровня на следующий
     */
    private fun getExpNeededForLevel(level: Int): Long {
        val currentLevelData = levelTable[level] ?: return 0
        val nextLevelData = levelTable[level + 1] ?: return 0
        return nextLevelData.minExp - currentLevelData.minExp
    }

    private fun formatNumber(num: Int): String = formatNumber(num.toLong())

    private fun formatNumber(num: Long): String {
        return when {
            num >= 1_000_000 -> String.format("%.1fM", num / 1_000_000.0)
            num >= 1_000 -> String.format("%.1fK", num / 1_000.0)
            else -> num.toString()
        }
    }

    // ============================================
    // Commands
    // ============================================

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
            "msdp" -> showMsdpStatus()
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
        api.echo("MSDP: ${if (api.isMsdpEnabled()) "включён" else "выключен"}")
        api.echo("MSDP инициализирован: $msdpInitialized")

        @Suppress("UNCHECKED_CAST")
        val promptStatus = status["promptDetector"] as? Map<String, Any>
        if (promptStatus != null) {
            api.echo("Промптов обнаружено: ${promptStatus["promptCount"]}")
            api.echo("Последний промпт: ${promptStatus["lastPrompt"]}")
            api.echo("Regex установлен: ${promptStatus["hasPattern"]}")
        }
    }

    private fun showMsdpStatus() {
        api.echo("=== MSDP Status ===")
        api.echo("Протокол: ${if (api.isMsdpEnabled()) "включён" else "выключен"}")

        val reportable = api.getMsdpReportableVariables()
        if (reportable.isNotEmpty()) {
            api.echo("Доступные переменные (${reportable.size}):")
            api.echo(reportable.joinToString(", "))
        }

        api.echo("Подписки плагина: ${msdpVariables.joinToString(", ")}")
    }

    private fun showPromptRegex() {
        api.echo("=== Prompt Regex ===")
        if (config.promptRegex.isBlank()) {
            api.echo("Используется дефолтный regex для Былин:")
            api.echo("  $DEFAULT_PROMPT_REGEX")
        } else {
            api.echo("Текущий regex: ${config.promptRegex}")
        }
        api.echo("")
        api.echo("Именованные группы: hp, move, exp, level, gold, exits")
        api.echo("Сбросить на дефолтный: #bot regex")
    }

    private fun setPromptRegex(pattern: String) {
        config.promptRegex = pattern
        saveConfig(config)

        // Используем дефолтный если пустой
        val effectivePattern = pattern.ifBlank { DEFAULT_PROMPT_REGEX }

        try {
            val regex = Regex(effectivePattern)
            botCore.promptDetector.setPromptPattern(regex)
            if (pattern.isBlank()) {
                addLog("Regex сброшен на дефолтный")
            } else {
                addLog("Regex установлен: $pattern")
            }
        } catch (e: Exception) {
            addLog("Ошибка в regex: ${e.message}")
            // Пробуем дефолтный
            try {
                botCore.promptDetector.setPromptPattern(Regex(DEFAULT_PROMPT_REGEX))
                addLog("Используется дефолтный regex")
            } catch (e2: Exception) {
                addLog("ERROR: ${e2.message}")
            }
        }
    }

    private fun loadPromptRegex() {
        // Используем сохранённый regex или дефолтный
        val pattern = config.promptRegex.ifBlank { DEFAULT_PROMPT_REGEX }
        addLog("Regex pattern: $pattern")

        // Тест на примере (с пробелом в конце как в реальном промпте)
        val testPrompt = "478H 258M 13179529o Зауч:0 ОЗ:0 28L 346G Вых:v> "

        try {
            val regex = Regex(pattern)
            botCore.promptDetector.setPromptPattern(regex)

            // Тестируем
            val testMatch = regex.find(testPrompt)
            if (testMatch != null) {
                addLog("Тест OK: hp=${testMatch.groups["hp"]?.value}, exp=${testMatch.groups["exp"]?.value}, level=${testMatch.groups["level"]?.value}, gold=${testMatch.groups["gold"]?.value}")
            } else {
                addLog("WARN: Regex не матчит тестовый промпт!")
                addLog("Pattern: $pattern")
            }

            if (config.promptRegex.isBlank()) {
                addLog("Используется дефолтный regex")
            }
        } catch (e: Exception) {
            addLog("ERROR: Невалидный regex: ${e.message}")
            // Пробуем дефолтный
            try {
                botCore.promptDetector.setPromptPattern(Regex(DEFAULT_PROMPT_REGEX))
                addLog("Используется дефолтный regex (пользовательский невалиден)")
            } catch (e2: Exception) {
                addLog("ERROR: ${e2.message}")
            }
        }
    }

    /**
     * Получить эффективный regex (сохранённый или дефолтный)
     */
    private fun getEffectiveRegex(): String {
        return config.promptRegex.ifBlank { DEFAULT_PROMPT_REGEX }
    }

    // ============================================
    // Парсинг промпта
    // ============================================

    private fun handlePromptParsed(parsed: Map<String, String>) {
        // Инициализируем статус-бары если ещё не созданы
        ensureStatusBarsInitialized()

        // HP
        parsed["hp"]?.toIntOrNull()?.let { hp ->
            api.updateStatusBar("hp", hp)
        }

        // Move
        parsed["move"]?.toIntOrNull()?.let { move ->
            api.updateStatusBar("move", move)
        }

        // Level (прямое значение из промпта) - обрабатываем ДО опыта
        parsed["level"]?.toIntOrNull()?.let { level ->
            if (level != lastLevel && level > 0) {
                lastLevel = level
                updateLevelStatus(level)
            }
        }

        // Experience - в промпте это ОСТАТОК до следующего уровня!
        val expStr = parsed["exp"]
        if (expStr != null) {
            val remaining = expStr.toLongOrNull()
            if (remaining != null && lastLevel > 0) {
                val expNeeded = getExpNeededForLevel(lastLevel)
                if (expNeeded > 0) {
                    val done = (expNeeded - remaining).coerceAtLeast(0)
                    addLog("Exp: осталось $remaining, сделано $done/$expNeeded")
                    api.updateStatusBar("exp", done.toInt(), expNeeded.toInt())
                }
            } else if (remaining != null) {
                // Уровень ещё не известен - просто показываем остаток
                addLog("Exp: осталось $remaining (уровень неизвестен)")
            }
        }

        // Gold
        parsed["gold"]?.toIntOrNull()?.let { gold ->
            api.updateStatusText("gold", formatNumber(gold))
        }
    }

    private fun updateLevelStatus(level: Int) {
        val maxExp = getMaxExpPerMob(level)
        val hint = if (maxExp > 0) "макс с моба: ${formatNumber(maxExp)}" else "БЕССМЕРТИЕ"
        // Обновляем элемент внутри группы vitals
        api.updateStatusText("level", "$level ($hint)")
    }

    private fun ensureStatusBarsInitialized() {
        if (statusBarsInitialized) return
        statusBarsInitialized = true

        // Порядок: minimap=0, vitals=10, wealth=15, character=20, stats=30, combat=40, etc.

        // Группа жизненных показателей
        api.addStatusGroup("vitals", "Жизненные показатели", collapsed = false, order = 10) {
            text("level", "Уровень", null, null, bold = true, order = 0)
            bar("exp", "Опыт", 0, 100, "yellow", showText = true, showMax = true, order = 1)
            bar("hp", "Жизнь", 0, lastMaxHp, "red", showText = true, showMax = true, order = 2)
            text("hpRegen", "Восстановление жизни", null, null, bold = false, order = 3)
            bar("move", "Выносливость", 0, lastMaxMove, "green", showText = true, showMax = true, order = 4)
            text("moveRegen", "Восстановление сил", null, null, bold = false, order = 5)
        }

        // Группа богатства
        api.addStatusGroup("wealth", "Богатство", collapsed = false, order = 15) {
            text("gold", "В кармане", null, null, bold = false, order = 0)
            text("bank", "В банке", null, null, bold = false, order = 1)
        }

        addLog("Статус-бары инициализированы")
    }

    // ============================================
    // Парсинг счёта (сч / сч все)
    // ============================================

    private fun handleScoreParsed(score: CharacterScore) {
        addLog("Счёт распарсен: ${score.name}, уровень ${score.level}")

        // Если известен реморт - выбираем соответствующую дефолтную таблицу
        score.remorts?.let { remort ->
            if (remort != currentRemort) {
                selectDefaultTableForRemort(remort)
            }
        }

        // Если комната безопасная - добавляем тег на текущую комнату
        if (score.isSafe) {
            val currentRoom = api.getCurrentRoom()
            val roomId = currentRoom?.get("id")?.toString()
            if (roomId != null) {
                // Получаем текущие теги и добавляем "safe" если его нет
                @Suppress("UNCHECKED_CAST")
                val existingTags = (currentRoom["tags"] as? List<String>) ?: emptyList()
                if ("safe" !in existingTags) {
                    api.setRoomTags(roomId, existingTags + "safe")
                    addLog("Комната $roomId помечена как безопасная")
                }
            }
        }

        // Обновляем максимумы HP/Move
        score.maxHp?.let { maxHp ->
            lastMaxHp = maxHp
            api.updateStatusBarMax("hp", maxHp)
        }
        score.maxMove?.let { maxMove ->
            lastMaxMove = maxMove
            api.updateStatusBarMax("move", maxMove)
        }

        // Восстановление жизни/сил - формат "базовое (+процент%)"
        if (score.hpRegen != null || score.hpRegenBase != null) {
            val regenText = formatRegen(score.hpRegenBase, score.hpRegen)
            api.updateStatusText("hpRegen", regenText)
        }
        if (score.moveRegen != null || score.moveRegenBase != null) {
            val regenText = formatRegen(score.moveRegenBase, score.moveRegen)
            api.updateStatusText("moveRegen", regenText)
        }

        // Обновляем уровень
        score.level?.let { level ->
            if (level != lastLevel && level > 0) {
                lastLevel = level
                updateLevelStatus(level)
            }
        }

        // Опыт из счёта (тотальный или остаток)
        score.expToLevel?.let { remaining ->
            if (lastLevel > 0) {
                val expNeeded = getExpNeededForLevel(lastLevel)
                if (expNeeded > 0) {
                    val done = (expNeeded - remaining).coerceAtLeast(0)
                    api.updateStatusBar("exp", done.toInt(), expNeeded.toInt())
                }
            }
        }

        // Золото - отдельно карман и банк
        score.gold?.let { gold ->
            api.updateStatusText("gold", formatNumber(gold))
        }
        score.bank?.let { bank ->
            api.updateStatusText("bank", formatNumber(bank))
        }

        // Информация о персонаже
        updateCharacterInfo(score)

        // Характеристики
        updateStatsGroup(score)

        // Размеры
        updateSizeGroup(score)

        // Боевые характеристики
        updateCombatGroup(score)

        // Магия
        updateMagicGroup(score)

        // Спас-броски
        updateSavesGroup(score)

        // Сопротивления
        updateResistsGroup(score)
    }

    /**
     * Форматирует значение с дельтой: "25 (23 + 2)" или "17 (18 - 1)"
     * При нулевой дельте - просто число без скобок.
     * Примечание: цвет применяется на уровне UI компонента, не через ANSI коды.
     */
    private fun formatWithDelta(current: Int, base: Int?): String {
        if (base == null || current == base) {
            return current.toString()
        }
        val delta = current - base
        val sign = if (delta > 0) "+" else "-"
        val absDelta = kotlin.math.abs(delta)
        return "$current ($base $sign $absDelta)"
    }

    private fun updateCharacterInfo(score: CharacterScore) {
        val hasInfo = score.name != null || score.className != null || score.tribe != null

        if (!hasInfo) return

        api.addStatusGroup("character", "Персонаж", collapsed = false, order = 5) {
            score.name?.let { text("name", "Имя", it, null, true, 0) }
            score.className?.let { text("class", "Класс", it, null, false, 1) }
            score.tribe?.let { text("tribe", "Племя", it, null, false, 2) }
            score.religion?.let { text("religion", "Вера", it, null, false, 3) }
            score.age?.let { text("age", "Возраст", "$it лет", null, false, 4) }
            score.remorts?.let { text("remorts", "Перевоплощений", it.toString(), null, false, 5) }
            score.glory?.let { text("glory", "Слава", it.toString(), null, false, 6) }
        }
    }

    private fun updateStatsGroup(score: CharacterScore) {
        val hasStats = score.str != null || score.dex != null || score.con != null ||
                       score.wis != null || score.int != null || score.cha != null

        if (!hasStats) return

        api.addStatusGroup("stats", "Характеристики", collapsed = false, order = 20) {
            score.str?.let { modifiedValue("str", "Сила", it, score.strBase, calcModifier(it, score.strBase), null, 0,
                """СИЛА — физическая мощь персонажа.

Бонус к урону = STR - 14 (при STR ≥ 15)
  Пример: STR 25 → +11 к урону каждого удара

Бонус к попаданию = (STR - 10) / 4
Переносимый вес = STR × 50

Типичные значения: 20-30 воин, 12-18 маг""") }
            score.dex?.let { modifiedValue("dex", "Ловкость", it, score.dexBase, calcModifier(it, score.dexBase), null, 1,
                """ЛОВКОСТЬ — скорость реакции и координация.

Бонус к попаданию = DEX - 10
  Пример: DEX 25 → +15 к попаданию

Бонус к AC = (DEX - 15) × 10
  Пример: DEX 25 → -100 к AC (это хорошо!)

Для игроков: AC от max(DEX, INT × 0.75)
Вместимость инвентаря = 5 + DEX/2 + Уровень/2""") }
            score.con?.let { modifiedValue("con", "Телосложение", it, score.conBase, calcModifier(it, score.conBase), null, 2,
                """ТЕЛОСЛОЖЕНИЕ — выносливость и здоровье.

Регенерация HP = max(10, CON × 1.5) за тик
  Пример: CON 20 → 30 HP за тик

Регенерация движения = CON / 2 за тик
Влияет на максимум HP (формула зависит от класса)
Определяет спасброски Стойкость и Здоровье""") }
            score.wis?.let { modifiedValue("wis", "Мудрость", it, score.wisBase, calcModifier(it, score.wisBase), null, 3,
                """МУДРОСТЬ — духовная сила и интуиция.

Успех заклинаний: бонус = WIS × 4 - 84
  Пример: WIS 25 → +16% к успеху

Максимум маны определяется таблицей (до WIS 50)
Макс. умений = 15 + (WIS - 15) / 2 при WIS > 15
Определяет спасбросок Воля""") }
            score.int?.let { modifiedValue("int", "Интеллект", it, score.intBase, calcModifier(it, score.intBase), null, 4,
                """ИНТЕЛЛЕКТ — умственные способности и память.

Слоты запоминания: по таблице int_app
Регенерация маны: 20 (INT 5) ... 200 (INT 50) за тик
Шанс изучения заклинаний: 22% ... 100%
Для кастеров: бонус попадания = (INT - 13) / Уровень

Бдительность: бонус к обнаружению скрытых""") }
            score.cha?.let { modifiedValue("cha", "Обаяние", it, score.chaBase, calcModifier(it, score.chaBase), null, 5,
                """ОБАЯНИЕ — сила личности.

Лидерство: от -4 (CHA 5) до +35 (CHA 50)
Влияет на цены у торговцев (таблица cha_app)
Бонус illusive к грязным приёмам в бою

Важен для лидеров групп и чармеров""") }
        }
    }

    /**
     * Вычисляет модификатор (дельту) между текущим и базовым значением.
     */
    private fun calcModifier(current: Int, base: Int?): Int? {
        if (base == null || current == base) return null
        return current - base
    }

    /**
     * Форматирует восстановление: "85 (+60%)" - базовое значение и процент бонуса.
     */
    private fun formatRegen(base: Int?, percent: Int?): String {
        return when {
            base != null && percent != null -> "$base (+$percent%)"
            base != null -> "$base"
            percent != null -> "+$percent%"
            else -> "?"
        }
    }

    private fun updateSizeGroup(score: CharacterScore) {
        val hasSize = score.height != null || score.weight != null || score.size != null

        if (!hasSize) return

        api.addStatusGroup("size", "Размеры", collapsed = true, order = 25) {
            score.height?.let { modifiedValue("height", "Рост", it, score.heightBase, calcModifier(it, score.heightBase), null, 0,
                """РОСТ — высота персонажа в сантиметрах.

• Влияет на досягаемость в ближнем бою
• Высокий персонаж легче попадает по низким
• Низкий персонаж сложнее для высоких врагов""") }
            score.weight?.let { modifiedValue("weight", "Вес", it, score.weightBase, calcModifier(it, score.weightBase), null, 1,
                """ВЕС — масса персонажа в фунтах.

• Влияет на устойчивость к сбиванию с ног
• Тяжёлый персонаж сложнее опрокинуть
• Может влиять на некоторые умения""") }
            score.size?.let { modifiedValue("size", "Размер", it, score.sizeBase, calcModifier(it, score.sizeBase), null, 2,
                """РАЗМЕР — общий размер персонажа (0-100).

• Бонус к AC: size × 10
• Влияет на инициативу
• Маленький размер = сложнее попасть
• Большой размер = легче попасть, но больше урона""") }
        }
    }

    private fun updateCombatGroup(score: CharacterScore) {
        val hasCombat = score.ac != null || score.armor != null || score.hitroll != null ||
                        score.damroll != null || score.initiative != null || score.luck != null

        if (!hasCombat) return

        api.addStatusGroup("combat", "Бой", collapsed = false, order = 30) {
            score.hitroll?.let { text("hitroll", "Попадание", it.toString(), null, false, 0,
                """HITROLL — суммарный бонус к попаданию.

Шанс попадания = d20 + hitroll + skill_bonus - AC_врага

Бонус от скилла оружия:
  skill ≤ 80:  skill / 20
  skill ≤ 160: 4 + (skill - 80) / 10
  skill > 160: 12 + (skill - 160) / 5

Типичные значения: 20-50 для боевых классов.""") }
            score.damroll?.let { text("damroll", "Повреждение", it.toString(), null, false, 1,
                """DAMROLL — бонус к урону каждой атаки.

Урон = кубики_оружия + damroll + STR_bonus

Концентрация силы (способность):
  множитель = 1 + (STR×0.4 + Уровень×0.2) / 10
  Максимум: ×2.6 к урону

Типичные значения: 15-40 для воинов.""") }
            score.ac?.let { text("ac", "Защита (AC)", it.toString(), null, false, 2,
                """AC (Armor Class) — защита от попаданий.

ЧЕМ НИЖЕ — тем лучше!
• AC -100 — отличная защита, враги часто мажут
• AC 0 — средняя защита
• AC +100 — плохая защита, попадают почти всегда

Складывается из брони экипировки и бонуса от DEX.
Каждый класс имеет свой предел (паладин до -270, маг до -170).""") }
            score.armor?.let { text("armor", "Броня", it.toString(), null, false, 3,
                """БРОНЯ — снижает физический урон на указанный %.

Пример: броня 40 = получаете только 60% урона.
Максимум: 50% (75% со способностью «Непробиваемый»).

Не путать с AC:
• AC — шанс попадания (ниже = реже попадают)
• Броня — снижение урона (выше = меньше урона)""") }
            score.absorb?.let { text("absorb", "Поглощение", it.toString(), null, false, 4,
                """ПОГЛОЩЕНИЕ — прямое вычитание из урона.

Физический урон:
• Шанс срабатывания: 10% + реморт/3
• При срабатывании: урон -= absorb/2
• Со способностью «Непробиваемый»: +5% к шансу

Магический урон:
• Снижение: 1% за каждые 2 absorb (максимум 25%)

Absorb=50 может поглотить до 25 физ. урона.""") }
            score.initiative?.let { text("init", "Инициатива", it.toString(), null, false, 5,
                """ИНИЦИАТИВА — скорость реакции в бою.

• Влияет на порядок действий в раунде
• Бонусы от экипировки и размера
• Обнуляется при начале нового боя

Высокая инициатива = атакуете раньше врага.""") }
            score.luck?.let { text("luck", "Удача", it.toString(), null, false, 6,
                """УДАЧА — влияет на случайные события.

• Шанс критического удара
• Шанс редких событий (лут, etc.)
• Бонус от экипировки и аффектов

Важна для всех классов, но особенно для воров.""") }
        }
    }

    private fun updateMagicGroup(score: CharacterScore) {
        val hasMagic = score.castLevel != null || score.memorySlots != null ||
                       score.spellPower != null || score.physDamage != null

        if (!hasMagic) return

        api.addStatusGroup("magic", "Магия", collapsed = true, order = 35) {
            score.castLevel?.let { text("cast", "Колдовство", it.toString(), null, false, 0,
                """КОЛДОВСТВО — бонус к уровню заклинаний.

• Увеличивает урон заклинаний
• Увеличивает длительность эффектов
• Складывается с бонусами экипировки

Пример: +5 колдовство = заклинания как у персонажа на 5 уровней выше.""") }
            score.memorySlots?.let { text("mem", "Запоминание", it.toString(), null, false, 1,
                """ЗАПОМИНАНИЕ — бонус к слотам памяти.

• Базовое количество слотов зависит от INT
• Этот бонус добавляется сверху
• Больше слотов = больше заклинаний можно выучить

Важно для всех магических классов.""") }
            score.spellPower?.let { text("spellpow", "Сила заклинаний %", it.toString(), null, false, 2,
                """СИЛА ЗАКЛИНАНИЙ — увеличивает урон магии на указанный %.

Пример: +50% = заклинание на 100 урона нанесёт 150.

Основной показатель эффективности магов.""") }
            score.physDamage?.let { text("physdmg", "Физический урон %", it.toString(), null, false, 3,
                """ФИЗИЧЕСКИЙ УРОН — увеличивает физ. урон на указанный %.

Пример: +30% = удар на 100 урона нанесёт 130.

Важен для воинов и других боевых классов.""") }
        }
    }

    private fun updateSavesGroup(score: CharacterScore) {
        val hasSaves = score.saveWill != null || score.saveHealth != null ||
                       score.saveStability != null || score.saveReflex != null

        if (!hasSaves) return

        api.addStatusGroup("saves", "Спасброски", collapsed = true, order = 40) {
            score.saveWill?.let { text("will", "Воля", it.toString(), null, false, 0,
                """ВОЛЯ — защита от ментальных воздействий.

Защищает от:
• Страха, паники
• Очарования, контроля разума
• Ментальных заклинаний

Зависит от WIS. Чем выше значение — тем лучше.
Проверка: спасбросок vs случайное число (-200..+200).""") }
            score.saveHealth?.let { text("health", "Здоровье", it.toString(), null, false, 1,
                """ЗДОРОВЬЕ — защита от физических недугов.

Защищает от:
• Ядов и отравлений
• Болезней
• Ослабляющих эффектов

Зависит от CON. Чем выше значение — тем лучше.
Отравление снижает регенерацию HP в 4 раза!""") }
            score.saveStability?.let { text("stab", "Стойкость", it.toString(), null, false, 2,
                """СТОЙКОСТЬ — устойчивость к физическим воздействиям.

Защищает от:
• Оглушения
• Сбивания с ног
• Опрокидывания

Зависит от CON. Верхом даёт бонус -20 (легче устоять).
Тяжёлые персонажи устойчивее.""") }
            score.saveReflex?.let { text("reflex", "Реакция", it.toString(), null, false, 3,
                """РЕАКЦИЯ — скорость уклонения.

Защищает от:
• Массовых заклинаний (AOE)
• Ловушек
• Эффектов, требующих быстрой реакции

Зависит от DEX. Верхом даёт штраф +20 (сложнее увернуться).
Важно для воров и лёгких классов.""") }
        }
    }

    private fun updateResistsGroup(score: CharacterScore) {
        val hasResists = score.resistFire != null || score.resistWater != null ||
                         score.resistEarth != null || score.resistAir != null ||
                         score.resistDark != null || score.resistMind != null ||
                         score.resistDamage != null || score.resistSpells != null ||
                         score.resistWounds != null || score.resistPoison != null

        if (!hasResists) return

        api.addStatusGroup("resists", "Сопротивления", collapsed = true, order = 50) {
            score.resistDamage?.let { text("rdmg", "Урону", it.toString(), null, false, 0,
                """СОПР. УРОНУ — снижает физический урон на указанный %.

Пример: сопр. 30 = получаете только 70% урона.

Диапазон: -100 (уязвимость, ×2 урона) до +100 (иммунитет).""") }
            score.resistSpells?.let { text("rspl", "Заклинаниям", it.toString(), null, false, 1,
                """СОПР. ЗАКЛИНАНИЯМ — снижает магический урон на указанный %.

Пример: сопр. 25 = получаете только 75% маг. урона.

Магическое зеркало дополнительно отражает часть урона.""") }
            score.resistFire?.let { text("rfire", "Магии огня", it.toString(), null, false, 2,
                """Снижает урон от огненной магии на указанный %.

Огненный щит дополнительно снижает урон на 30-50%
и отражает 15-25% урона атакующему.""") }
            score.resistWater?.let { text("rwater", "Магии воды", it.toString(), null, false, 3,
                """Снижает урон от водной/ледяной магии на указанный %.

Ледяной щит даёт 94% шанс отменить критический удар.""") }
            score.resistEarth?.let { text("rearth", "Магии земли", it.toString(), null, false, 4,
                """Снижает урон от земляной магии на указанный %.""") }
            score.resistAir?.let { text("rair", "Магии воздуха", it.toString(), null, false, 5,
                """Снижает урон от воздушной магии и молний на указанный %.

Воздушный щит дополнительно снижает урон на 30-50%.""") }
            score.resistDark?.let { text("rdark", "Магии тьмы", it.toString(), null, false, 6,
                """Снижает урон от тёмной магии на указанный %.

Защищает от некромантии.""") }
            score.resistMind?.let { text("rmind", "Магии разума", it.toString(), null, false, 7,
                """Снижает эффективность ментальных атак.

Защищает от страха, очарования, контроля разума.
Работает вместе со спасброском «Воля».""") }
            score.resistWounds?.let { text("rwound", "Тяжёлым ранам", it.toString(), null, false, 8,
                """Снижает урон от критических ударов.

Важно против воров и крит-классов.""") }
            score.resistPoison?.let { text("rpoison", "Ядам и болезням", it.toString(), null, false, 9,
                """Защита от отравлений и болезней.

ВАЖНО: Отравление снижает регенерацию HP в 4 раза!""") }
        }
    }

    private fun subscribeToEvents() {
        // Обработка входящих строк (используем line без ANSI-кодов)
        api.subscribe(LineReceivedEvent::class.java, EventPriority.NORMAL) { event ->
            botCore.processLine(event.line, event.timestamp)
        }

        // Подключение к серверу - проверяем MSDP с задержкой (после telnet-переговоров)
        api.subscribe(ConnectEvent::class.java, EventPriority.NORMAL) { _ ->
            scope.launch {
                // Ждём завершения telnet-переговоров
                delay(2000)
                if (api.isMsdpEnabled() && !msdpInitialized) {
                    addLog("MSDP уже включён после подключения")
                    initializeMsdp()
                }
            }
        }

        // MSDP включён
        api.subscribe(MsdpEnabledEvent::class.java, EventPriority.NORMAL) {
            addLog("Получено событие MsdpEnabledEvent")
            initializeMsdp()
        }

        // Список reportable переменных получен
        api.subscribe(MsdpReportableVariablesEvent::class.java, EventPriority.NORMAL) { event ->
            onMsdpReportableVariables(event.variables)
        }

        // Данные MSDP
        api.subscribe(MsdpEvent::class.java, EventPriority.NORMAL) { event ->
            onMsdpData(event.variable, event.value)
        }
    }

    private fun showHelp() {
        api.echo("""
            === AI Bot Help ===
            #bot status           - Показать статус
            #bot msdp             - Показать MSDP статус
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
        tab = api.createTab("main", "Ассистент")
        logger.info("Plugin tab created: ${tab?.id}")
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
                    PluginUINode.Text("Ассистент", PluginUINode.TextStyle.TITLE),
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
                        placeholder = "Дефолт: $DEFAULT_PROMPT_REGEX"
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
