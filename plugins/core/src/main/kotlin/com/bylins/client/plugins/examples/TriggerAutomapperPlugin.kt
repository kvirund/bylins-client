package com.bylins.client.plugins.examples

import com.bylins.client.plugins.PluginBase
import com.bylins.client.plugins.TriggerResult

/**
 * Плагин автомаппера для Bylins MUD.
 *
 * Парсит:
 * - Название комнаты: "Постоялый двор [5001]"
 * - Промпт с выходами: "239H 150M ... Вых:З^>"
 *
 * Выходы в промпте:
 *   С = север, Ю = юг, З = запад, В = восток, ^ = вверх, v = вниз
 *
 * plugin.yml:
 * ```
 * id: bylins-automapper
 * name: Bylins Automapper
 * version: 1.0.0
 * main: com.bylins.client.plugins.examples.TriggerAutomapperPlugin
 * author: Bylins Client
 * ```
 */
class TriggerAutomapperPlugin : PluginBase() {

    // Состояние парсера
    private var pendingRoomId: String? = null
    private var pendingRoomName: String? = null
    private var pendingExits: List<String>? = null
    private var lastDirection: String? = null
    private var expectingRoom: Boolean = false

    // Маппинг символов выходов в промпте
    private val exitCharMap = mapOf(
        'С' to "north",
        'с' to "north",
        'Ю' to "south",
        'ю' to "south",
        'З' to "west",
        'з' to "west",
        'В' to "east",
        'в' to "east",
        '^' to "up",
        'v' to "down"
    )

    // Маппинг направлений для команд
    private val directionMap = mapOf(
        "север" to "north",
        "юг" to "south",
        "запад" to "west",
        "восток" to "east",
        "вверх" to "up",
        "вниз" to "down"
    )

    override fun onEnable() {
        logger.info("Bylins Automapper включен")

        // Включаем маппинг
        api.setMapEnabled(true)

        // Алиасы для перехвата команд движения
        createMovementAliases()

        // Триггеры для парсинга
        createRoomTriggers()

        api.echo("[Automapper] Плагин активирован. Начните движение для создания карты.")
    }

    override fun onDisable() {
        logger.info("Bylins Automapper выключен")
    }

    /**
     * Создает алиасы для отслеживания команд движения
     */
    private fun createMovementAliases() {
        val shortDirs = mapOf(
            "с" to "north",
            "ю" to "south",
            "з" to "west",
            "в" to "east",
            "вв" to "up",
            "вн" to "down"
        )

        // Полные названия
        for ((ruDir, enDir) in directionMap) {
            api.createAlias(Regex("^$ruDir$", RegexOption.IGNORE_CASE)) { _, _ ->
                onMovementCommand(enDir)
                false
            }
        }

        // Сокращения
        for ((short, enDir) in shortDirs) {
            api.createAlias(Regex("^$short$", RegexOption.IGNORE_CASE)) { _, _ ->
                onMovementCommand(enDir)
                false
            }
        }
    }

    /**
     * Создает триггеры для парсинга комнат
     */
    private fun createRoomTriggers() {
        // Триггер на название комнаты с ID: "Постоялый двор [5001]"
        api.createTrigger(
            Regex("^(.+?)\\s*\\[(\\d+)\\]\\s*$"),
            priority = 100
        ) { _, groups ->
            if (groups.size > 2) {
                val name = groups[1].trim()
                val id = groups[2]

                // Игнорируем строки карты (начинаются с :)
                if (!name.startsWith(":") && !name.startsWith("|")) {
                    pendingRoomName = name
                    pendingRoomId = id
                    logger.info("Automapper: комната '$name' [ID: $id]")
                    tryCreateRoom()
                }
            }
            TriggerResult.CONTINUE
        }

        // Триггер на промпт с выходами: "... Вых:СЮЗv^>"
        api.createTrigger(
            Regex("Вых:([СЮЗВсюзвv^]+)>"),
            priority = 100
        ) { _, groups ->
            if (groups.size > 1) {
                parsePromptExits(groups[1])
            }
            TriggerResult.CONTINUE
        }

        // Триггеры на успешное движение
        val movementVerbs = listOf(
            "пошли", "поплелись", "побрели", "полетели",
            "поползли", "потащились", "поехали", "побежали"
        )

        for (verb in movementVerbs) {
            api.createTrigger(
                Regex("^Вы $verb (на )?(север|юг|запад|восток|вверх|вниз)", RegexOption.IGNORE_CASE),
                priority = 100
            ) { _, groups ->
                val dirText = groups.lastOrNull()?.lowercase()
                if (dirText != null) {
                    val dir = directionMap[dirText]
                    if (dir != null) {
                        lastDirection = dir
                        expectingRoom = true
                        pendingRoomName = null
                        pendingRoomId = null
                        pendingExits = null
                    }
                }
                TriggerResult.CONTINUE
            }
        }

        // Триггеры на ошибки движения
        val errorPatterns = listOf(
            "Вы не можете идти",
            "Там нет выхода",
            "Дверь закрыта",
            "Вы не видите здесь",
            "Вам мешает",
            "Куда?",
            "Что?"
        )
        for (pattern in errorPatterns) {
            api.createTrigger(Regex("^$pattern", RegexOption.IGNORE_CASE)) { _, _ ->
                lastDirection = null
                expectingRoom = false
                TriggerResult.CONTINUE
            }
        }
    }

    /**
     * Вызывается при команде движения
     */
    @Suppress("UNUSED_PARAMETER")
    private fun onMovementCommand(direction: String) {
        // Ничего не делаем - ждём подтверждения от сервера
    }

    /**
     * Парсит выходы из промпта (формат: СЮЗВv^)
     */
    private fun parsePromptExits(exitsStr: String) {
        val exits = mutableListOf<String>()

        for (char in exitsStr) {
            val dir = exitCharMap[char]
            if (dir != null && dir !in exits) {
                exits.add(dir)
            }
        }

        pendingExits = exits
        tryCreateRoom()
    }

    /**
     * Пытается создать комнату если есть все необходимые данные
     */
    private fun tryCreateRoom() {
        val roomId = pendingRoomId ?: return
        val roomName = pendingRoomName ?: return
        val exits = pendingExits ?: return
        val direction = lastDirection

        // Вычисляем зону: ID / 100
        val zoneId = roomId.toIntOrNull()?.div(100)?.toString() ?: "unknown"

        if (direction != null) {
            // Движение - используем handleMovement
            val roomInfo = api.handleMovement(direction, roomName, exits)
            if (roomInfo != null) {
                val createdId = roomInfo["id"] as? String
                if (createdId != null) {
                    api.setRoomZone(createdId, "zone_$zoneId")
                }
                logger.info("Automapper: '$roomName' [$roomId] выходы: $exits")
                api.echo("[Automapper] $roomName [$roomId]")
            }
        } else {
            // Начальная комната или телепорт
            val currentRoom = api.getCurrentRoom()
            val currentId = currentRoom?.get("id") as? String

            if (currentId != roomId) {
                // Проверяем, существует ли комната с таким ID
                val existingRooms = api.searchRooms(roomId)
                if (existingRooms.isEmpty()) {
                    // Создаём новую комнату
                    if (api.createRoom(roomId, roomName)) {
                        api.setRoomZone(roomId, "zone_$zoneId")
                        api.setCurrentRoom(roomId)
                        logger.info("Automapper: создана '$roomName' [$roomId]")
                        api.echo("[Automapper] Новая: $roomName [$roomId]")
                    }
                } else {
                    // Комната существует - переходим в неё
                    api.setCurrentRoom(roomId)
                    logger.info("Automapper: переход в '$roomName' [$roomId]")
                }
            }
        }

        // Сбрасываем состояние
        pendingRoomId = null
        pendingRoomName = null
        pendingExits = null
        lastDirection = null
        expectingRoom = false
    }
}
