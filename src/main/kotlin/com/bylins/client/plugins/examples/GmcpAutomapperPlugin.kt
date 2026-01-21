package com.bylins.client.plugins.examples

import com.bylins.client.plugins.PluginBase
import com.bylins.client.plugins.TriggerResult
import com.bylins.client.plugins.events.EventPriority
import com.bylins.client.plugins.events.GmcpEvent
import kotlinx.serialization.json.*

/**
 * Пример плагина автомаппера, использующего GMCP данные.
 *
 * Этот плагин демонстрирует как использовать GMCP для автоматического маппинга.
 * Он слушает пакеты Room.Info и Room.Exits для создания карты.
 *
 * ВАЖНО: Разные MUD серверы используют разные GMCP пакеты.
 * Этот пример использует стандартные пакеты IRE:
 * - Room.Info: информация о текущей комнате
 * - Room.Exits: выходы из комнаты
 *
 * Для Bylins MUD может потребоваться адаптация под специфичные пакеты.
 *
 * plugin.yml:
 * ```
 * id: gmcp-automapper
 * name: GMCP Automapper
 * version: 1.0.0
 * main: com.bylins.client.plugins.examples.GmcpAutomapperPlugin
 * author: Bylins Client
 * ```
 */
class GmcpAutomapperPlugin : PluginBase() {

    private val json = Json { ignoreUnknownKeys = true }

    // Последние данные о комнате
    private var lastRoomVnum: String? = null
    private var lastRoomName: String? = null
    private var lastExits: List<String> = emptyList()
    private var lastDirection: String? = null

    override fun onEnable() {
        logger.info("GMCP Automapper включен")

        // Подписываемся на GMCP события
        api.subscribe(GmcpEvent::class.java, EventPriority.NORMAL) { event ->
            handleGmcpEvent(event)
        }

        // Создаем триггер для отслеживания направления движения
        createMovementTriggers()

        api.echo("[Automapper] Плагин активирован. Ожидание GMCP данных...")
    }

    override fun onDisable() {
        logger.info("GMCP Automapper выключен")
    }

    /**
     * Обрабатывает GMCP события
     */
    private fun handleGmcpEvent(event: GmcpEvent) {
        when {
            // Стандартный IRE формат Room.Info
            event.packageName == "Room.Info" -> handleRoomInfo(event.data)

            // Bylins может использовать другой формат
            event.packageName.startsWith("room.") -> handleBylinsRoom(event.packageName, event.data)

            // MSDP-style через GMCP
            event.packageName == "MSDP.ROOM" -> handleMsdpRoom(event.data)
        }
    }

    /**
     * Обрабатывает стандартный IRE Room.Info пакет
     *
     * Формат: {"num": 1234, "name": "Название комнаты", "zone": "Зона", "exits": {"n": 1235, "s": 1233}}
     */
    private fun handleRoomInfo(data: String) {
        try {
            val roomData = json.parseToJsonElement(data).jsonObject

            val vnum = roomData["num"]?.jsonPrimitive?.content
                ?: roomData["vnum"]?.jsonPrimitive?.content
                ?: return

            val name = roomData["name"]?.jsonPrimitive?.content ?: "Неизвестно"
            val zone = roomData["zone"]?.jsonPrimitive?.content ?: roomData["area"]?.jsonPrimitive?.content

            // Парсим выходы
            val exits = mutableListOf<String>()
            roomData["exits"]?.jsonObject?.keys?.forEach { dir ->
                exits.add(dir)
            }

            processRoomData(vnum, name, zone, exits)

        } catch (e: Exception) {
            logger.warn("Ошибка парсинга Room.Info: ${e.message}")
        }
    }

    /**
     * Обрабатывает Bylins-специфичные GMCP пакеты
     */
    private fun handleBylinsRoom(packageName: String, data: String) {
        try {
            when (packageName) {
                "room.info" -> {
                    val roomData = json.parseToJsonElement(data).jsonObject
                    val vnum = roomData["vnum"]?.jsonPrimitive?.content ?: return
                    val name = roomData["name"]?.jsonPrimitive?.content ?: "Неизвестно"
                    val zone = roomData["zone"]?.jsonPrimitive?.content

                    lastRoomVnum = vnum
                    lastRoomName = name

                    // Если уже есть выходы - создаем комнату
                    if (lastExits.isNotEmpty()) {
                        processRoomData(vnum, name, zone, lastExits)
                    }
                }

                "room.exits" -> {
                    val exitsData = json.parseToJsonElement(data)
                    lastExits = when (exitsData) {
                        is JsonArray -> exitsData.map { it.jsonPrimitive.content }
                        is JsonObject -> exitsData.keys.toList()
                        else -> emptyList()
                    }

                    // Если уже есть данные о комнате - создаем
                    val vnum = lastRoomVnum
                    val name = lastRoomName
                    if (vnum != null && name != null) {
                        processRoomData(vnum, name, null, lastExits)
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Ошибка парсинга Bylins GMCP: ${e.message}")
        }
    }

    /**
     * Обрабатывает MSDP данные комнаты через GMCP
     */
    private fun handleMsdpRoom(data: String) {
        try {
            val roomData = json.parseToJsonElement(data).jsonObject

            val vnum = roomData["VNUM"]?.jsonPrimitive?.content
                ?: roomData["ROOM_VNUM"]?.jsonPrimitive?.content
                ?: return

            val name = roomData["NAME"]?.jsonPrimitive?.content
                ?: roomData["ROOM_NAME"]?.jsonPrimitive?.content
                ?: "Неизвестно"

            val zone = roomData["AREA_NAME"]?.jsonPrimitive?.content

            val exits = roomData["EXITS"]?.let { exitsElement ->
                when (exitsElement) {
                    is JsonArray -> exitsElement.map { it.jsonPrimitive.content }
                    is JsonObject -> exitsElement.keys.toList()
                    else -> emptyList()
                }
            } ?: emptyList()

            processRoomData(vnum, name, zone, exits)

        } catch (e: Exception) {
            logger.warn("Ошибка парсинга MSDP.ROOM: ${e.message}")
        }
    }

    /**
     * Обрабатывает данные комнаты и обновляет карту
     */
    private fun processRoomData(vnum: String, name: String, zone: String?, exits: List<String>) {
        val direction = lastDirection

        if (direction != null) {
            // Движение в направлении - создаем связь
            val roomInfo = api.handleMovement(direction, name, exits)
            if (roomInfo != null) {
                val roomId = roomInfo["id"] as? String
                if (roomId != null && zone != null) {
                    api.setRoomZone(roomId, zone)
                }
                logger.info("Автомаппер: комната '$name' добавлена/обновлена")
            }
            lastDirection = null
        } else {
            // Телепорт или начальная позиция - проверяем существует ли комната
            val currentRoom = api.getCurrentRoom()
            if (currentRoom == null || currentRoom["id"] != vnum) {
                // Пытаемся найти комнату по vnum или создаем новую
                val existingRooms = api.searchRooms(vnum)
                if (existingRooms.isEmpty()) {
                    // Создаем новую комнату в координатах 0,0,0 (или относительно последней)
                    val created = api.createRoom(vnum, name)
                    if (created && zone != null) {
                        api.setRoomZone(vnum, zone)
                    }
                    api.setCurrentRoom(vnum)
                    logger.info("Автомаппер: создана начальная комната '$name'")
                } else {
                    // Комната найдена - переходим в неё
                    val roomId = existingRooms[0]["id"] as? String
                    if (roomId != null) {
                        api.setCurrentRoom(roomId)
                    }
                }
            }
        }

        // Сбрасываем временные данные
        lastRoomVnum = null
        lastRoomName = null
        lastExits = emptyList()
    }

    /**
     * Создает триггеры для отслеживания направления движения
     */
    private fun createMovementTriggers() {
        // Триггер на русские команды движения
        val directions = listOf(
            "север" to "north",
            "юг" to "south",
            "запад" to "west",
            "восток" to "east",
            "вверх" to "up",
            "вниз" to "down",
            "с" to "north",
            "ю" to "south",
            "з" to "west",
            "в" to "east",
            "вв" to "up",
            "вн" to "down"
        )

        // Алиас для перехвата команд движения
        for ((ruDir, enDir) in directions) {
            api.createAlias(Regex("^$ruDir$")) { _, _ ->
                lastDirection = enDir
                // Не блокируем команду - пусть отправится на сервер
                false
            }
        }
    }
}
