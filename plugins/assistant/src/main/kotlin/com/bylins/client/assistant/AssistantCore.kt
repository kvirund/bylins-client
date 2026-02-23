package com.bylins.client.assistant

import com.bylins.client.assistant.perception.*
import com.bylins.client.plugins.PluginAPI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mu.KotlinLogging

/**
 * Состояния ассистента (FSM)
 */
enum class AssistantState {
    /** Простаивает, только мониторит */
    IDLE,
    /** Сбор информации о персонаже */
    INTROSPECTION,
    /** Исследование - делает один шаг к неисследованной комнате */
    EXPLORATION,
    /** В бою (заглушка — логика перехода будет добавлена позже) */
    COMBAT
}

/**
 * Причина неудачного перемещения
 */
enum class MovementFailReason {
    /** Закрыто (дверь/ворота) */
    DOOR_CLOSED,
    /** Заперто */
    DOOR_LOCKED,
    /** Нужен ключ */
    DOOR_NEEDS_KEY,
    /** Невозможно пройти (устал, не можете идти, и т.п.) */
    CANT_MOVE,
    /** В бою — нельзя уйти */
    IN_COMBAT,
    /** Неизвестная причина */
    UNKNOWN
}

/**
 * Текущее recovery-действие при неудачном перемещении
 */
enum class RecoveryAction {
    /** Ждём результат "открыть дверь" */
    OPENING_DOOR,
    /** Ждём результат "отпереть дверь" */
    UNLOCKING_DOOR,
    /** Повторяем движение */
    RETRYING_MOVE
}

/**
 * Информация о двери на выходе. Двери закрываются со временем,
 * поэтому это знание о наличии двери, а не о её текущем состоянии.
 */
data class ExitDoorInfo(
    /** Полное имя двери из "Закрыто (крепкая дверь)." */
    val doorName: String = "дверь",
    val isLocked: Boolean = false
) {
    /**
     * Имя двери для MUD-команды. Многословные имена соединяются точками:
     * "крепкая дверь" → "крепкая.дверь"
     */
    val commandName: String get() = doorName.replace(" ", ".")
}

/**
 * Область исследования
 */
enum class ExplorationScope {
    /** Только текущая зона */
    ZONE,
    /** Весь мир */
    WORLD
}

private val logger = KotlinLogging.logger("AssistantCore")

// Regex для определения промпта пагинации
private val PAGINATION_REGEX = Regex("""Листать\s*:.*\((\d+)/(\d+)\)""")

// Regex для "Минул час." с ANSI-кодами
// Формат: \e[1;31mМинул час.\e[0;37m
private val HOUR_PASSED_REGEX = Regex("""\u001B\[1;31mМинул час\.\u001B\[0;37m""")

/**
 * Ядро ассистента
 *
 * Функционал:
 * - Определение промпта (PromptDetector)
 * - Парсинг статов из промпта через regex
 * - Парсинг команды "сч" (ScoreParser)
 *
 * FSM:
 *   IDLE → decideNext() ─┬→ INTROSPECTION → decideNext()
 *                        └→ EXPLORATION → decideNext()
 */
class AssistantCore(
    private val api: PluginAPI
) {
    // Текущее состояние FSM
    private val _state = MutableStateFlow(AssistantState.IDLE)
    val state: StateFlow<AssistantState> = _state

    // Область исследования
    private val _explorationScope = MutableStateFlow(ExplorationScope.ZONE)
    val explorationScope: StateFlow<ExplorationScope> = _explorationScope

    // Оставаться в границах зоны (не выбирать выходы в другие зоны)
    private val _stayInZone = MutableStateFlow(true)
    val stayInZone: StateFlow<Boolean> = _stayInZone

    // Очередь команд для выполнения
    private val commandQueue = ArrayDeque<String>()
    // Ожидаем ответ на команду
    private var waitingForPrompt = false
    // Интроспекция выполнена в этой сессии
    private var introspectionDone = false
    // Зона исследования (вычислена из VNUM комнаты: vnum/100)
    private var explorationZoneNumber: Int? = null
    // Ожидаем проверку безопасности комнаты
    private var checkingSafety = false
    // Ожидаем пагинацию от команды плагина (уровни и т.п.)
    private var expectingPagination = false

    // --- Отслеживание перемещений ---
    // ID комнаты, из которой отправили команду движения
    private var movementSourceRoomId: String? = null
    // Направление последней команды движения
    private var movementDirection: String? = null
    // Последний текстовый батч (для классификации причины неудачи)
    private var lastTextBatch: String = ""
    // Текущее recovery-действие
    private var recoveryAction: RecoveryAction? = null
    // Счётчик попыток recovery для текущего выхода
    private var recoveryAttempts: Int = 0

    // --- Информация о дверях (сохраняется на сессию) ---
    // "roomId:direction" → ExitDoorInfo
    private val exitDoors = mutableMapOf<String, ExitDoorInfo>()

    // --- Проблемные выходы ---
    // Автоматически помеченные (дверь заперта, cant_move) — сбрасываются при stop/start
    private val autoProblematicExits = mutableSetOf<String>()
    // Помеченные пользователем через #assistant skip — не сбрасываются при start
    private val userSkippedExits = mutableSetOf<String>()

    // LLM сервис
    val llmService by lazy { LlmService(scope) }

    // Парсинг
    val promptParser by lazy { PromptParser() }
    val scoreParser by lazy { ScoreParser() }
    val affectsParser by lazy { AffectsParser() }
    val skillsParser by lazy { SkillsParser() }
    val roomContentParser by lazy { RoomContentParser() }
    val promptDetector by lazy {
        PromptDetector(
            onTextReceived = { batchText -> handleTextReceived(batchText) },
            onPromptReceived = { prompt, parsed -> handlePromptReceived(prompt, parsed) },
            onPatternInvalid = { _, _ -> },
            onRawTextReceived = { rawBatchText -> handleRawTextReceived(rawBatchText) }
        )
    }

    // Состояние аффектов и умений
    private val _affects = MutableStateFlow<CharacterAffects?>(null)
    val affects: StateFlow<CharacterAffects?> = _affects.asStateFlow()

    private val _skills = MutableStateFlow<CharacterSkills?>(null)
    val skills: StateFlow<CharacterSkills?> = _skills.asStateFlow()

    private val _roomContent = MutableStateFlow<RoomContent?>(null)
    val roomContent: StateFlow<RoomContent?> = _roomContent.asStateFlow()

    // Счётчик "Минул час."
    private var hourPassedCount = 0

    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Callback для логирования
    var onLog: ((String) -> Unit)? = null

    // Callback для обработки распарсенного промпта
    var onPromptParsed: ((prompt: String, parsed: Map<String, String>) -> Unit)? = null

    // Callback для обработки блока текста (для ScoreParser и др.)
    var onTextBlock: ((text: String) -> Unit)? = null

    // Callback для события "Минул час."
    var onHourPassed: (() -> Unit)? = null

    // Callback для обработки текста с ANSI-кодами (для RoomContentParser)
    var onRawTextBlock: ((text: String) -> Unit)? = null

    /**
     * Обработать входящую строку от сервера.
     * @param line - текст строки (без ANSI-кодов)
     * @param rawLine - оригинальная строка с ANSI-кодами
     * @param timestamp - время получения
     */
    fun processLine(line: String, rawLine: String, timestamp: Long = System.currentTimeMillis()) {
        // Проверяем "Минул час." в raw строке
        if (HOUR_PASSED_REGEX.containsMatchIn(rawLine)) {
            hourPassedCount++
            log("Минул час. (всего: $hourPassedCount)")
            onHourPassed?.invoke()
        }

        // Передаём обе версии в детектор
        promptDetector.processLine(line, timestamp, rawLine)
    }

    /**
     * Проверить таймаут для определения промпта
     */
    fun checkPromptTimeout(timestamp: Long = System.currentTimeMillis()) {
        promptDetector.checkTimeout(timestamp)
    }

    // ============================================
    // Обработчики PromptDetector
    // ============================================

    private fun handleTextReceived(batchText: String) {
        // Сохраняем текст для классификации неудачных перемещений
        if (_state.value == AssistantState.EXPLORATION && waitingForPrompt) {
            lastTextBatch = batchText
        }

        // Пробуем распарсить как вывод "сч"
        val score = scoreParser.tryParse(batchText)
        if (score != null) {
            log("Score parsed: level=${score.level}, hp=${score.hp}/${score.maxHp}, safe=${score.isSafe}")

            // Если ждали проверку безопасности - ставим свойство на комнату
            if (checkingSafety) {
                checkingSafety = false
                val currentRoom = api.getCurrentRoom()
                val roomId = currentRoom?.get("id") as? String
                if (roomId != null) {
                    val safeValue = if (score.isSafe) "true" else "false"
                    api.setRoomProperty(roomId, "safe", safeValue)
                    log("Комната $roomId помечена как: safe=$safeValue")
                }
            }
        }

        // Пробуем распарсить как вывод "афф"
        val affects = affectsParser.tryParse(batchText)
        if (affects != null) {
            _affects.value = affects
            log("Affects parsed: ${affects.affects.size} affects")
        }

        // Пробуем распарсить как вывод "ум"
        val skills = skillsParser.tryParse(batchText)
        if (skills != null) {
            _skills.value = skills
            log("Skills parsed: ${skills.skills.size} skills")
        }

        // Вызываем callback для внешней обработки
        onTextBlock?.invoke(batchText)
    }

    // Regex для удаления ANSI кодов (для LLM)
    private val ansiStripPattern = Regex("""\u001B\[[0-9;]*m""")

    /**
     * Обработать raw текст батча (с ANSI-кодами).
     * Вызывается из PromptDetector при определении промпта.
     */
    private fun handleRawTextReceived(rawBatchText: String) {
        log("handleRawTextReceived: ${rawBatchText.length} chars, ${rawBatchText.lines().size} lines")

        // Если LLM не подключена - не парсим room content
        if (!llmService.isConnected) {
            log("Room content: LLM not connected, skipping")
            onRawTextBlock?.invoke(rawBatchText)
            return
        }

        // Текст без ANSI для LLM
        val strippedText = ansiStripPattern.replace(rawBatchText, "")

        // Асинхронно спрашиваем LLM
        scope.launch {
            val isRoom = llmService.isRoomDescription(strippedText)
            if (isRoom) {
                // LLM подтвердила - парсим
                val content = roomContentParser.tryParse(rawBatchText)
                if (content != null && !content.isEmpty()) {
                    _roomContent.value = content
                    log("Room content: ${content.mobs.size} mobs, ${content.objects.size} objects")
                } else {
                    log("Room content: LLM confirmed, but no entities found")
                }
            } else {
                log("Room content: LLM says not a room description")
            }
        }

        // Callback для внешней обработки raw текста
        onRawTextBlock?.invoke(rawBatchText)
    }

    private fun handlePromptReceived(prompt: String, parsed: Map<String, String>?) {
        // Проверяем промпт пагинации - но только если МЫ вызвали команду
        val paginationMatch = PAGINATION_REGEX.find(prompt)
        if (paginationMatch != null && expectingPagination) {
            val currentPage = paginationMatch.groupValues[1].toIntOrNull() ?: 1
            val totalPages = paginationMatch.groupValues[2].toIntOrNull() ?: 1
            log("Пагинация: страница $currentPage/$totalPages, отправляю Enter")
            // Отправляем пустую строку для продолжения
            api.send("")
            // Если последняя страница - сбрасываем флаг
            if (currentPage >= totalPages) {
                expectingPagination = false
            }
            return // Не обрабатываем как обычный промпт
        } else if (paginationMatch != null) {
            // Пагинация не от нас - сбрасываем флаг на всякий случай
            expectingPagination = false
        }

        // Логируем промпт (с парсингом или без)
        if (parsed != null && parsed.isNotEmpty()) {
            log("Prompt parsed: ${parsed.entries.joinToString { "${it.key}=${it.value}" }}")
            onPromptParsed?.invoke(prompt, parsed)
        } else {
            log("Prompt: $prompt")
        }

        // Любой не-пагинационный промпт = команда выполнена
        log("handlePromptReceived: state=${_state.value}, waiting=$waitingForPrompt, queueSize=${commandQueue.size}, recovery=$recoveryAction")
        if (waitingForPrompt) {
            waitingForPrompt = false
            when (_state.value) {
                AssistantState.INTROSPECTION -> sendNextCommand()
                AssistantState.EXPLORATION -> {
                    if (recoveryAction != null) {
                        // Обрабатываем результат recovery-действия
                        handleRecoveryResult()
                    } else if (movementSourceRoomId != null) {
                        // Проверяем, переместились ли мы
                        val currentRoom = api.getCurrentRoom()
                        val currentRoomId = currentRoom?.get("id") as? String
                        if (currentRoomId == null) {
                            // Не можем определить комнату — считаем что не переместились
                            log("Перемещение: текущая комната неизвестна, считаю неудачным")
                            handleMovementFailure()
                        } else if (currentRoomId == movementSourceRoomId) {
                            // Не переместились — классифицируем и пытаемся решить
                            log("Перемещение неудачно: остались в комнате $currentRoomId")
                            handleMovementFailure()
                        } else {
                            // Успешно переместились — сброс
                            resetMovementState()
                            decideNext()
                        }
                    } else {
                        decideNext()
                    }
                }
                else -> {}
            }
        }
    }

    // ============================================
    // FSM - управление состоянием
    // ============================================

    /**
     * Команды для сбора информации о персонаже (INTROSPECTION)
     */
    private val introspectionCommands = listOf(
        "счет все",  // Статистика персонажа
        "эк все",    // Экипировка
        "см",        // Осмотреться (текущая комната)
        "уровни",    // Уровни
        "ум",        // Умения
        "спос",      // Способности
        "афф",       // Аффекты
        "зауч",      // Заученные заклинания
        "закл",      // Известные заклинания
        "гдея"       // Позиция в мире
    )

    /**
     * Запустить ассистента.
     * IDLE → decideNext() → INTROSPECTION/EXPLORATION
     */
    fun start() {
        if (_state.value != AssistantState.IDLE) {
            log("Ассистент уже занят (состояние: ${_state.value})")
            return
        }

        decideNext()
    }

    /**
     * Роутер: решает какое состояние следующее.
     * - Если интроспекция не выполнена → INTROSPECTION
     * - Иначе → EXPLORATION
     *
     * Использует launch для избежания StackOverflow при рекурсивных переходах.
     */
    private fun decideNext() {
        log("decideNext: introspectionDone=$introspectionDone")

        // Асинхронный переход чтобы избежать StackOverflow
        scope.launch {
            // TODO: проверка условия боя → COMBAT
            // if (inCombat()) { enterCombat(); return@launch }

            if (!introspectionDone) {
                enterIntrospection()
            } else {
                enterExploration()
            }
        }
    }

    /**
     * INTROSPECTION: сбор информации о персонаже.
     * Последовательно отправляет команды, ожидая промпт после каждой.
     */
    private fun enterIntrospection() {
        _state.value = AssistantState.INTROSPECTION
        log("Состояние: INTROSPECTION - сбор информации о персонаже")

        // Заполняем очередь команд
        commandQueue.clear()
        commandQueue.addAll(introspectionCommands)
        waitingForPrompt = false

        // Отправляем первую команду
        sendNextCommand()
    }

    /**
     * Отправить следующую команду из очереди.
     * Когда очередь пуста - вызываем decideNext() для перехода к следующему этапу.
     */
    private fun sendNextCommand() {
        if (commandQueue.isEmpty()) {
            // Интроспекция завершена
            introspectionDone = true
            // Запоминаем зону из VNUM комнаты
            val room = api.getCurrentRoom()
            val roomId = room?.get("id") as? String
            explorationZoneNumber = roomId?.let { getZoneFromVnum(it) }
            log("Интроспекция завершена, зона: $explorationZoneNumber (VNUM=$roomId)")
            // Переходим к следующему этапу
            decideNext()
            return
        }

        val cmd = commandQueue.removeFirst()
        val remaining = commandQueue.toList()
        log("Отправляю команду: $cmd (осталось: ${remaining.size} - ${remaining.joinToString(", ")})")

        // Команда "уровни" использует пагинацию
        if (cmd == "уровни") {
            expectingPagination = true
        }

        api.send(cmd)
        waitingForPrompt = true
    }

    /**
     * Вычисляет ID зоны из VNUM комнаты.
     * В Былинах: zone_id = vnum / 100
     */
    private fun getZoneFromVnum(vnum: String): Int? = vnum.toIntOrNull()?.div(100)

    /**
     * Переводит направление из формата карты в русское слово для команд (открыть, отпереть).
     * Движение принимает английские направления, но команды с направлением-аргументом — нет.
     */
    private fun directionToRussian(direction: String): String = when (direction.lowercase()) {
        "north", "север", "с" -> "север"
        "south", "юг", "ю" -> "юг"
        "east", "восток", "в" -> "восток"
        "west", "запад", "з" -> "запад"
        "up", "вверх", "вв" -> "вверх"
        "down", "вниз", "вн" -> "вниз"
        else -> direction // fallback — оставляем как есть
    }

    /**
     * Проверяет, является ли выход допустимым для исследования.
     * - Непосещённая комната
     * - В режиме ZONE + stayInZone: комната должна быть в исследуемой зоне
     */
    private fun isExitExplorable(targetRoomId: String, explorationZoneId: Int?): Boolean {
        if (targetRoomId.isEmpty()) {
            return true // Неизвестный выход - исследуем (ручной маппинг)
        }

        // Проверяем зону (если режим ZONE и включён stayInZone)
        if (_explorationScope.value == ExplorationScope.ZONE &&
            _stayInZone.value &&
            explorationZoneId != null) {
            val targetZone = getZoneFromVnum(targetRoomId)
            if (targetZone != null && targetZone != explorationZoneId) {
                return false // Выход ведёт в другую зону - пропускаем
            }
        }

        // Проверяем, посещена ли комната
        val targetRoom = api.getRoom(targetRoomId)
        return targetRoom == null || targetRoom["visited"] != true
    }

    /**
     * EXPLORATION: исследование - делает один шаг к комнате с неисследованными выходами.
     * Неисследованный выход = выход с пустым targetRoomId или ведущий в непосещённую комнату.
     * В режиме ZONE: пропускаются выходы, ведущие в другие зоны.
     */
    private fun enterExploration() {
        _state.value = AssistantState.EXPLORATION
        log("Состояние: EXPLORATION")

        // Получаем текущую комнату
        val currentRoom = api.getCurrentRoom()
        if (currentRoom == null) {
            log("Ошибка: текущая комната неизвестна")
            _state.value = AssistantState.IDLE
            onExplorationComplete?.invoke("неизвестна текущая комната", _explorationScope.value)
            return
        }

        val currentRoomId = currentRoom["id"] as? String
        val currentRoomZone = getZoneFromVnum(currentRoomId ?: "")

        // Определяем зону для исследования (из VNUM, вычислена как vnum/100)
        val explorationZoneId = explorationZoneNumber ?: currentRoomZone

        // Проверка: если режим ZONE и мы в другой зоне - пытаемся вернуться
        if (_explorationScope.value == ExplorationScope.ZONE &&
            explorationZoneId != null &&
            currentRoomZone != null &&
            currentRoomZone != explorationZoneId) {

            log("Обнаружена смена зоны: $currentRoomZone (исследуем $explorationZoneId)")

            // Ищем путь обратно в исследуемую зону
            val pathBackResult = api.findNearestMatching { room ->
                val roomId = room["id"] as? String ?: return@findNearestMatching false
                val roomZone = getZoneFromVnum(roomId)
                roomZone == explorationZoneId
            }

            if (pathBackResult != null) {
                val (targetRoom, path) = pathBackResult
                if (path.isNotEmpty()) {
                    val targetName = targetRoom["name"] as? String ?: "?"
                    val direction = path.first()
                    log("Возвращаюсь в зону $explorationZoneId: $targetName (${path.size} шагов)")
                    movementSourceRoomId = currentRoomId
                    movementDirection = direction
                    lastTextBatch = ""
                    api.send(direction)
                    waitingForPrompt = true
                    return
                }
            } else {
                log("Путь обратно в зону $explorationZoneId не найден, останавливаюсь")
                _state.value = AssistantState.IDLE
                return
            }
        }

        // Проверяем, есть ли у комнаты свойство безопасности
        @Suppress("UNCHECKED_CAST")
        val properties = currentRoom["properties"] as? Map<String, String> ?: emptyMap()
        val hasSafetyTag = "safe" in properties

        // Если нет тега - с вероятностью 1/100 проверяем безопасность
        if (!hasSafetyTag && (1..100).random() == 1) {
            log("Проверяю безопасность комнаты...")
            checkingSafety = true
            api.send("счет")
            waitingForPrompt = true
            return
        }

        // Ищем ближайшую комнату с исследуемыми выходами
        val result = api.findNearestMatching { room ->
            val roomId = room["id"] as? String ?: return@findNearestMatching false
            val roomZone = getZoneFromVnum(roomId)

            // В режиме ZONE: комната должна быть в исследуемой зоне
            if (_explorationScope.value == ExplorationScope.ZONE &&
                explorationZoneId != null &&
                roomZone != explorationZoneId) {
                return@findNearestMatching false
            }

            @Suppress("UNCHECKED_CAST")
            val exits = room["exits"] as? Map<String, String> ?: emptyMap()

            // Есть ли исследуемые выходы? (с учётом зоны и проблемных выходов)
            exits.entries.any { (direction, targetRoomId) ->
                val exitKey = "$roomId:$direction"
                exitKey !in autoProblematicExits &&
                    exitKey !in userSkippedExits &&
                    isExitExplorable(targetRoomId, explorationZoneId)
            }
        }

        if (result == null) {
            val scopeName = if (_explorationScope.value == ExplorationScope.ZONE) "зоне $explorationZoneId" else "мире"
            log("Все выходы в $scopeName исследованы!")
            _state.value = AssistantState.IDLE
            onExplorationComplete?.invoke(scopeName, _explorationScope.value)
            return
        }

        val (targetRoom, path) = result
        val targetName = targetRoom["name"] as? String ?: "?"

        if (path.isEmpty()) {
            // Мы уже в комнате с исследуемыми выходами - идём в первый подходящий
            val thisRoomId = targetRoom["id"] as? String ?: currentRoomId
            @Suppress("UNCHECKED_CAST")
            val exits = targetRoom["exits"] as? Map<String, String> ?: emptyMap()

            // Ищем исследуемый выход (с учётом зоны и проблемных)
            val unexploredEntry = exits.entries.firstOrNull { (dir, targetRoomId) ->
                val exitKey = "$thisRoomId:$dir"
                exitKey !in autoProblematicExits &&
                    exitKey !in userSkippedExits &&
                    isExitExplorable(targetRoomId, explorationZoneId)
            }

            if (unexploredEntry != null) {
                val (direction, targetRoomId) = unexploredEntry
                val exitTargetName = if (targetRoomId.isNotEmpty()) {
                    api.getRoom(targetRoomId)?.get("name") as? String ?: "?"
                } else "???"
                val targetZone = getZoneFromVnum(targetRoomId)
                log("Исследую выход: $direction → $exitTargetName (зона $targetZone)")

                // Предварительная проверка: если известно, что на выходе дверь — сначала открыть
                val exitKey = "$thisRoomId:$direction"
                val doorInfo = exitDoors[exitKey]
                if (doorInfo != null && !doorInfo.isLocked) {
                    val rusDir = directionToRussian(direction)
                    log("Известна дверь '${doorInfo.doorName}' на $direction, открываю заранее")
                    api.send("открыть ${doorInfo.commandName} $rusDir")
                    // Не ждём промпт — отправляем движение сразу после
                }

                movementSourceRoomId = thisRoomId
                movementDirection = direction
                lastTextBatch = ""
                api.send(direction)
                waitingForPrompt = true
            } else {
                log("Ошибка: не найден исследуемый выход")
                decideNext()
            }
            return
        }

        // Делаем один шаг к комнате с исследуемыми выходами
        val direction = path.first()
        log("Иду: $direction → $targetName (${path.size} шагов)")
        movementSourceRoomId = currentRoomId
        movementDirection = direction
        lastTextBatch = ""
        api.send(direction)
        waitingForPrompt = true
    }

    /**
     * Остановить ассистента и вернуться в IDLE
     */
    fun stop() {
        if (_state.value != AssistantState.IDLE) {
            log("Остановка ассистента, возврат в IDLE")
            commandQueue.clear()
            waitingForPrompt = false
            introspectionDone = false
            explorationZoneNumber = null
            checkingSafety = false
            movementSourceRoomId = null
            movementDirection = null
            lastTextBatch = ""
            recoveryAction = null
            recoveryAttempts = 0
            autoProblematicExits.clear()
            // userSkippedExits НЕ сбрасываются — они сохраняются на сессию
            _state.value = AssistantState.IDLE
        }
    }

    /**
     * Ручной переход в указанное состояние (для отладки).
     * @return true если переход выполнен
     */
    fun gotoState(stateName: String): Boolean {
        val targetState = try {
            AssistantState.valueOf(stateName.uppercase())
        } catch (e: IllegalArgumentException) {
            log("Неизвестное состояние: $stateName")
            log("Доступные: ${AssistantState.entries.joinToString { it.name }}")
            return false
        }

        log("Ручной переход: ${_state.value} → $targetState")

        // Сбрасываем флаги
        commandQueue.clear()
        waitingForPrompt = false
        checkingSafety = false

        when (targetState) {
            AssistantState.IDLE -> {
                introspectionDone = false
                explorationZoneNumber = null
                _state.value = AssistantState.IDLE
            }
            AssistantState.INTROSPECTION -> enterIntrospection()
            AssistantState.EXPLORATION -> {
                // Пропускаем интроспекцию при ручном переходе
                introspectionDone = true
                val room = api.getCurrentRoom()
                val roomId = room?.get("id") as? String
                explorationZoneNumber = roomId?.let { getZoneFromVnum(it) }
                enterExploration()
            }
            AssistantState.COMBAT -> {
                _state.value = AssistantState.COMBAT
                log("COMBAT: заглушка, логика будет добавлена позже")
            }
        }
        return true
    }

    // ============================================
    // Настройка режима
    // ============================================

    /**
     * Установить область исследования
     */
    fun setExplorationScope(scope: ExplorationScope) {
        _explorationScope.value = scope
        log("Область исследования: $scope")
    }

    /**
     * Установить режим "оставаться в зоне".
     * Если true - не выбирать выходы, ведущие в другие зоны.
     * Если false - можно переходить в другие зоны (с fallback возвратом).
     */
    fun setStayInZone(enabled: Boolean) {
        _stayInZone.value = enabled
        log("Оставаться в зоне: $enabled")
    }

    // ============================================
    // Утилиты
    // ============================================

    fun log(message: String) {
        onLog?.invoke(message)
    }

    fun getStatus(): Map<String, Any> {
        return mapOf(
            "state" to _state.value.name,
            "explorationScope" to _explorationScope.value.name,
            "stayInZone" to _stayInZone.value,
            "explorationZoneId" to (explorationZoneNumber ?: ""),
            "commandsLeft" to commandQueue.size,
            "waitingForPrompt" to waitingForPrompt,
            "promptDetector" to promptDetector.getStatus(),
            "hourPassedCount" to hourPassedCount,
            "affectsCount" to (_affects.value?.affects?.size ?: 0),
            "skillsCount" to (_skills.value?.skills?.size ?: 0),
            "autoProblematicExits" to autoProblematicExits.toList(),
            "userSkippedExits" to userSkippedExits.toList(),
            "exitDoors" to exitDoors.map { (k, v) -> "$k → ${v.doorName}${if (v.isLocked) " (заперта)" else ""}" },
            "recoveryAction" to (recoveryAction?.name ?: "")
        )
    }

    // ============================================
    // Обработка неудачных перемещений
    // ============================================

    // Regex для классификации причины неудачного перемещения
    private val doorClosedPattern = Regex("""Закрыто\s*\(([^)]+)\)""")
    private val doorLockedPattern = Regex("""[Зз]аперт""")
    private val doorNeedsKeyPattern = Regex("""ключ""")
    private val inCombatPattern = Regex("""[Нн]и за что|сражаетесь за свою жизнь|[Вв]ы сражаетесь""")
    private val cantMovePattern = Regex("""[Нн]е можете идти|слишком устал|[Вв]ы не в состоянии""")

    // Regex для успешного взаимодействия с дверью
    private val doorSuccessPattern = Regex("""Ладушки|\*Щелк\*|[Оо]ткрыл|[Оо]тпер""")

    /**
     * Классифицирует причину неудачного перемещения по тексту от сервера.
     * @return пара (причина, имя двери или null)
     */
    private fun classifyFailure(text: String): Pair<MovementFailReason, String?> {
        doorClosedPattern.find(text)?.let { match ->
            val doorName = match.groupValues[1]
            return MovementFailReason.DOOR_CLOSED to doorName
        }

        if (doorLockedPattern.containsMatchIn(text)) {
            return MovementFailReason.DOOR_LOCKED to null
        }

        if (doorNeedsKeyPattern.containsMatchIn(text)) {
            return MovementFailReason.DOOR_NEEDS_KEY to null
        }

        if (inCombatPattern.containsMatchIn(text)) {
            return MovementFailReason.IN_COMBAT to null
        }

        if (cantMovePattern.containsMatchIn(text)) {
            return MovementFailReason.CANT_MOVE to null
        }

        return MovementFailReason.UNKNOWN to null
    }

    /**
     * Обрабатывает неудачное перемещение: классифицирует причину и начинает recovery.
     */
    private fun handleMovementFailure() {
        val direction = movementDirection ?: return
        val sourceRoomId = movementSourceRoomId ?: return
        val exitKey = "$sourceRoomId:$direction"
        val rusDir = directionToRussian(direction)

        val (reason, doorName) = classifyFailure(lastTextBatch)
        log("Причина неудачи: $reason${if (doorName != null) " (дверь: $doorName)" else ""}")

        when (reason) {
            MovementFailReason.DOOR_CLOSED -> {
                val name = doorName ?: exitDoors[exitKey]?.doorName ?: "дверь"
                val cmdName = name.replace(" ", ".")
                // Сохраняем информацию о двери
                exitDoors[exitKey] = ExitDoorInfo(doorName = name)
                log("Открываю $cmdName $rusDir")
                recoveryAction = RecoveryAction.OPENING_DOOR
                recoveryAttempts++
                api.send("открыть $cmdName $rusDir")
                waitingForPrompt = true
            }

            MovementFailReason.DOOR_LOCKED -> {
                val name = exitDoors[exitKey]?.doorName ?: "дверь"
                val cmdName = name.replace(" ", ".")
                if (recoveryAttempts < 2) {
                    log("Отпираю $cmdName $rusDir")
                    recoveryAction = RecoveryAction.UNLOCKING_DOOR
                    recoveryAttempts++
                    api.send("отпереть $cmdName $rusDir")
                    waitingForPrompt = true
                } else {
                    // Уже пробовали — помечаем как заблокированный
                    log("Не удалось отпереть $cmdName $rusDir, помечаю как проблемный")
                    exitDoors[exitKey] = ExitDoorInfo(doorName = name, isLocked = true)
                    autoProblematicExits.add(exitKey)
                    resetMovementState()
                    decideNext()
                }
            }

            MovementFailReason.DOOR_NEEDS_KEY -> {
                val name = exitDoors[exitKey]?.doorName ?: "дверь"
                log("Для $name нужен ключ, помечаю как проблемный")
                exitDoors[exitKey] = ExitDoorInfo(doorName = name, isLocked = true)
                autoProblematicExits.add(exitKey)
                resetMovementState()
                decideNext()
            }

            MovementFailReason.IN_COMBAT -> {
                log("В бою! Переход в COMBAT")
                resetMovementState()
                _state.value = AssistantState.COMBAT
                onCombatEntered?.invoke()
            }

            MovementFailReason.CANT_MOVE -> {
                log("Невозможно пройти $direction, помечаю как проблемный")
                autoProblematicExits.add(exitKey)
                resetMovementState()
                decideNext()
            }

            MovementFailReason.UNKNOWN -> {
                log("Неизвестная причина неудачи перемещения")
                // Останавливаемся и сообщаем пользователю
                val roomName = api.getCurrentRoom()?.get("name") as? String ?: sourceRoomId
                onMovementFailed?.invoke(direction, roomName)
                resetMovementState()
                _state.value = AssistantState.IDLE
            }
        }
    }

    /**
     * Обрабатывает результат recovery-действия.
     * Вызывается из handlePromptReceived() когда recoveryAction != null.
     */
    private fun handleRecoveryResult() {
        val direction = movementDirection ?: run {
            recoveryAction = null
            decideNext()
            return
        }
        val sourceRoomId = movementSourceRoomId ?: run {
            recoveryAction = null
            decideNext()
            return
        }
        val exitKey = "$sourceRoomId:$direction"
        val rusDir = directionToRussian(direction)
        val action = recoveryAction ?: return

        when (action) {
            RecoveryAction.OPENING_DOOR -> {
                val name = exitDoors[exitKey]?.doorName ?: "дверь"
                val cmdName = name.replace(" ", ".")
                val (reason, _) = classifyFailure(lastTextBatch)

                when {
                    reason == MovementFailReason.DOOR_LOCKED -> {
                        // "Угу, заперто." → пробуем отпереть
                        if (recoveryAttempts < 3) {
                            log("Дверь заперта, пробую отпереть $cmdName $rusDir")
                            recoveryAction = RecoveryAction.UNLOCKING_DOOR
                            recoveryAttempts++
                            api.send("отпереть $cmdName $rusDir")
                            waitingForPrompt = true
                        } else {
                            log("Исчерпаны попытки, помечаю $exitKey как проблемный")
                            exitDoors[exitKey] = ExitDoorInfo(doorName = name, isLocked = true)
                            autoProblematicExits.add(exitKey)
                            resetMovementState()
                            decideNext()
                        }
                    }
                    reason == MovementFailReason.DOOR_CLOSED -> {
                        // Всё ещё закрыто — команда не сработала
                        log("Дверь не открылась ($cmdName $rusDir)")
                        autoProblematicExits.add(exitKey)
                        resetMovementState()
                        decideNext()
                    }
                    doorSuccessPattern.containsMatchIn(lastTextBatch) || lastTextBatch.isBlank() -> {
                        // "Ладушки." или пустой текст → дверь открылась
                        log("Дверь открыта, повторяю движение: $direction")
                        recoveryAction = RecoveryAction.RETRYING_MOVE
                        api.send(direction)
                        waitingForPrompt = true
                    }
                    else -> {
                        // Непонятный ответ — НЕ предполагаем успех, останавливаемся
                        log("Непонятный ответ на 'открыть': ${lastTextBatch.take(100)}")
                        val roomName = api.getCurrentRoom()?.get("name") as? String ?: sourceRoomId
                        onMovementFailed?.invoke(direction, roomName)
                        resetMovementState()
                        _state.value = AssistantState.IDLE
                    }
                }
            }

            RecoveryAction.UNLOCKING_DOOR -> {
                val name = exitDoors[exitKey]?.doorName ?: "дверь"
                val cmdName = name.replace(" ", ".")
                val (reason, _) = classifyFailure(lastTextBatch)

                when {
                    reason == MovementFailReason.DOOR_NEEDS_KEY -> {
                        // Нужен ключ
                        log("Для $cmdName нужен ключ, помечаю как проблемный")
                        exitDoors[exitKey] = ExitDoorInfo(doorName = name, isLocked = true)
                        autoProblematicExits.add(exitKey)
                        resetMovementState()
                        decideNext()
                    }
                    reason == MovementFailReason.DOOR_LOCKED -> {
                        // Всё ещё заперто
                        if (recoveryAttempts < 3) {
                            log("Всё ещё заперто, повторяю отпереть $cmdName $rusDir")
                            recoveryAttempts++
                            api.send("отпереть $cmdName $rusDir")
                            waitingForPrompt = true
                        } else {
                            log("Не удалось отпереть $cmdName, помечаю как проблемный")
                            exitDoors[exitKey] = ExitDoorInfo(doorName = name, isLocked = true)
                            autoProblematicExits.add(exitKey)
                            resetMovementState()
                            decideNext()
                        }
                    }
                    doorSuccessPattern.containsMatchIn(lastTextBatch) || lastTextBatch.isBlank() -> {
                        // "*Щелк*" или пустой текст → отперто, теперь открываем
                        log("Отперто, открываю $cmdName $rusDir")
                        recoveryAction = RecoveryAction.OPENING_DOOR
                        recoveryAttempts++
                        api.send("открыть $cmdName $rusDir")
                        waitingForPrompt = true
                    }
                    else -> {
                        // Непонятный ответ — останавливаемся
                        log("Непонятный ответ на 'отпереть': ${lastTextBatch.take(100)}")
                        val roomName = api.getCurrentRoom()?.get("name") as? String ?: sourceRoomId
                        onMovementFailed?.invoke(direction, roomName)
                        resetMovementState()
                        _state.value = AssistantState.IDLE
                    }
                }
            }

            RecoveryAction.RETRYING_MOVE -> {
                // Проверяем, переместились ли
                val currentRoom = api.getCurrentRoom()
                val currentRoomId = currentRoom?.get("id") as? String
                if (currentRoomId != null && currentRoomId == sourceRoomId) {
                    // Всё ещё не переместились
                    log("Повторное перемещение неудачно")
                    val (reason, _) = classifyFailure(lastTextBatch)
                    if (reason == MovementFailReason.DOOR_CLOSED || reason == MovementFailReason.DOOR_LOCKED) {
                        // Дверь снова закрылась/заперта — пробуем ещё раз если есть попытки
                        if (recoveryAttempts < 4) {
                            handleMovementFailure()
                        } else {
                            log("Исчерпаны попытки recovery, помечаю $exitKey как проблемный")
                            autoProblematicExits.add(exitKey)
                            resetMovementState()
                            decideNext()
                        }
                    } else {
                        // Другая причина — помечаем как проблемный
                        autoProblematicExits.add(exitKey)
                        resetMovementState()
                        decideNext()
                    }
                } else {
                    // Успешно переместились
                    log("Recovery успешен: переместились в $currentRoomId")
                    resetMovementState()
                    decideNext()
                }
            }
        }
    }

    /**
     * Сбрасывает состояние отслеживания перемещения.
     */
    private fun resetMovementState() {
        movementSourceRoomId = null
        movementDirection = null
        lastTextBatch = ""
        recoveryAction = null
        recoveryAttempts = 0
    }

    /**
     * Пометить выход как пропущенный пользователем.
     * @param direction если null — использовать последнее направление движения
     */
    fun skipExit(direction: String? = null) {
        val currentRoom = api.getCurrentRoom()
        val roomId = currentRoom?.get("id") as? String

        if (direction != null && roomId != null) {
            val exitKey = "$roomId:$direction"
            userSkippedExits.add(exitKey)
            log("Пользователь пропустил выход: $exitKey")
            return
        }

        // Без указания направления — пропускаем последний неудачный выход
        val sourceId = movementSourceRoomId ?: roomId
        val dir = movementDirection

        if (sourceId != null && dir != null) {
            val exitKey = "$sourceId:$dir"
            userSkippedExits.add(exitKey)
            log("Пользователь пропустил выход: $exitKey")
        } else {
            log("Не удалось определить выход для пропуска")
        }
    }

    // Callback для уведомления плагина о неудачном перемещении (UNKNOWN)
    var onMovementFailed: ((direction: String, roomName: String) -> Unit)? = null

    // Callback для уведомления о входе в бой
    var onCombatEntered: (() -> Unit)? = null

    // Callback для уведомления что исследование завершено (зона/мир полностью исследованы, или ошибка)
    var onExplorationComplete: ((reason: String, scope: ExplorationScope) -> Unit)? = null

    fun shutdown() {
        commandQueue.clear()
        waitingForPrompt = false
        scope.cancel()
    }
}
