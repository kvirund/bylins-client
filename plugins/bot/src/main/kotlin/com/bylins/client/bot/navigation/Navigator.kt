package com.bylins.client.bot.navigation

import com.bylins.client.bot.BotCore
import com.bylins.client.bot.BotMode
import mu.KotlinLogging

/**
 * Навигатор бота
 * Управляет перемещением по карте с умным исследованием
 */
private val logger = KotlinLogging.logger("Navigator")

class Navigator(private val bot: BotCore) {

    // Текущий путь
    private var currentPath: List<String> = emptyList()
    private var pathIndex = 0

    // Целевая комната
    private var targetRoomId: String? = null
    private var targetRoomName: String? = null

    // Посещённые комнаты в текущей сессии
    private val visitedRooms = mutableSetOf<String>()

    // Последнее направление движения
    private var lastDirection: String? = null

    // Время последнего движения
    private var lastMoveTime = 0L
    private val moveDelay = 1000L // Задержка между движениями (1 сек)

    // Время последнего поиска цели (чтобы не спамить)
    private var lastTargetSearchTime = 0L
    private val targetSearchDelay = 3000L // Поиск цели не чаще чем раз в 3 сек

    /**
     * Есть ли активная цель
     */
    fun hasTarget(): Boolean = targetRoomId != null && currentPath.isNotEmpty()

    /**
     * Установить цель
     */
    fun setTarget(roomId: String) {
        targetRoomId = roomId

        // Ищем путь через маппер клиента
        val path = bot.findPathToRoom(roomId)
        if (path != null && path.isNotEmpty()) {
            currentPath = path
            pathIndex = 0
            bot.log("Путь найден до $roomId: ${path.size} шагов")
        } else {
            currentPath = emptyList()
            bot.log("Путь до $roomId не найден")
        }
    }

    /**
     * Установить цель по имени комнаты
     */
    fun setTargetByName(roomName: String) {
        targetRoomName = roomName
        bot.log("Поиск комнаты: $roomName")
        // TODO: Поиск комнаты по имени через API
    }

    /**
     * Найти следующую цель для текущего режима
     */
    fun findNextTarget(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastTargetSearchTime < targetSearchDelay) {
            return false
        }
        lastTargetSearchTime = now

        val config = bot.config.value
        val charState = bot.characterState.value

        // Оценка возможностей персонажа
        val playerLevel = charState?.level ?: 1
        val hpPercent = charState?.hpPercent ?: 100

        // Если HP низкий, не ищем новые цели
        if (hpPercent < config.restHpPercent) {
            bot.log("findNextTarget: HP слишком низкий ($hpPercent%), нужен отдых")
            return false
        }

        return when (config.mode) {
            BotMode.EXPLORING -> findUnexploredRoom(playerLevel)
            BotMode.LEVELING -> findRoomWithMobs(playerLevel)
            BotMode.FARMING -> findRoomWithMobs(playerLevel)
            else -> false
        }
    }

    /**
     * Найти непосещённую комнату для исследования
     */
    private fun findUnexploredRoom(playerLevel: Int): Boolean {
        bot.log("Ищу непосещённую комнату (уровень персонажа: $playerLevel)...")

        val result = bot.findNearestRoom { room ->
            // Комната не посещена (в маппере)
            val visited = room["visited"] as? Boolean ?: true
            if (visited) return@findNearestRoom false

            // Проверяем зону - не лезем в слишком опасные
            val zone = room["zone"] as? String
            if (zone != null && !isZoneSafeForLevel(zone, playerLevel)) {
                return@findNearestRoom false
            }

            // Не ходим в комнаты которые уже посетили в этой сессии
            val roomId = room["id"] as? String
            if (roomId != null && visitedRooms.contains(roomId)) {
                return@findNearestRoom false
            }

            true
        }

        if (result != null) {
            val (room, path) = result
            val roomId = room["id"] as? String ?: return false
            val roomName = room["name"] as? String ?: "неизвестно"

            bot.log("Найдена непосещённая комната: $roomName ($roomId), путь: ${path.size} шагов")

            targetRoomId = roomId
            currentPath = path
            pathIndex = 0
            return true
        }

        bot.log("Непосещённых комнат не найдено")
        return false
    }

    /**
     * Найти комнату с мобами для фарма
     */
    private fun findRoomWithMobs(playerLevel: Int): Boolean {
        bot.log("Ищу комнату с мобами (уровень персонажа: $playerLevel)...")

        // TODO: getMobsByZone возвращает MobData (информацию о мобах), а не MobSpawn (локации спавнов)
        // Для полноценной работы нужно добавить getMobSpawnsByZone с JOIN таблиц
        // Пока используем fallback - поиск непосещённых комнат
        return findUnexploredRoom(playerLevel)
    }

    /**
     * Проверить безопасность зоны для уровня персонажа
     */
    private fun isZoneSafeForLevel(zoneId: String, playerLevel: Int): Boolean {
        val zoneStats = bot.database.getZoneStats(zoneId)
        if (zoneStats != null) {
            val maxLevel = zoneStats.levelMax ?: 100
            val dangerLevel = zoneStats.dangerLevel ?: 0.0

            // Не ходим в зоны где уровень мобов сильно выше нашего
            if (maxLevel > playerLevel + 5) {
                return false
            }

            // Не ходим в очень опасные зоны
            if (dangerLevel > 0.7) {
                return false
            }
        }

        // Зон в чёрном списке избегаем
        val config = bot.config.value
        if (config.avoidZones.any { zoneId.contains(it, ignoreCase = true) }) {
            return false
        }

        return true
    }

    /**
     * Сделать следующий шаг
     */
    fun moveNext() {
        // Проверяем задержку
        val now = System.currentTimeMillis()
        if (now - lastMoveTime < moveDelay) {
            return
        }

        if (currentPath.isEmpty() || pathIndex >= currentPath.size) {
            // Путь пройден
            if (targetRoomId != null) {
                bot.log("Путь завершён, цель: $targetRoomId")
            }
            targetRoomId = null
            currentPath = emptyList()
            pathIndex = 0
            return
        }

        val direction = currentPath[pathIndex]
        lastDirection = direction
        lastMoveTime = now
        pathIndex++

        val remaining = currentPath.size - pathIndex
        bot.log("Иду: $direction (осталось: $remaining шагов)")
        bot.send(direction)
    }

    /**
     * Исследовать случайное направление (fallback если нет пути)
     */
    fun exploreRandom() {
        val now = System.currentTimeMillis()
        if (now - lastMoveTime < moveDelay) {
            return
        }

        // Получаем выходы из CharacterState (MSDP) или из текущей комнаты
        val charState = bot.characterState.value
        var exits: List<String> = charState?.exits ?: emptyList()

        // Если в CharacterState нет выходов, попробуем из room
        if (exits.isEmpty()) {
            val room = bot.getRoom()
            @Suppress("UNCHECKED_CAST")
            val roomExits = room?.get("exits")
            exits = when (roomExits) {
                is Map<*, *> -> roomExits.keys.mapNotNull { it?.toString() }
                is List<*> -> roomExits.mapNotNull { it?.toString() }
                else -> emptyList()
            }
        }

        if (exits.isEmpty()) {
            bot.log("exploreRandom: нет выходов, стою на месте")
            return
        }

        // Выбираем случайное направление (исключая откуда пришли)
        val availableDirections = exits.filter { it != getOppositeDirection(lastDirection) }
        val directionsToUse = if (availableDirections.isEmpty()) exits else availableDirections

        val direction = directionsToUse.random()
        lastDirection = direction
        lastMoveTime = now

        bot.log("Случайное направление: $direction (из: ${exits.joinToString()})")
        bot.send(direction)
    }

    /**
     * Обработать вход в комнату
     */
    fun onRoomEnter(room: Map<String, Any>) {
        val roomId = room["id"] as? String ?: return
        val roomName = room["name"] as? String ?: "?"
        visitedRooms.add(roomId)

        bot.log("Вошёл в комнату: $roomName ($roomId)")

        // Проверяем достигли ли цели
        if (roomId == targetRoomId) {
            bot.log("Достигнута целевая комната: $roomId")
            targetRoomId = null
            currentPath = emptyList()
            pathIndex = 0
        }
    }

    /**
     * Отменить текущий путь
     */
    fun cancelPath() {
        currentPath = emptyList()
        pathIndex = 0
        targetRoomId = null
        bot.log("Путь отменён")
    }

    /**
     * Получить противоположное направление
     */
    private fun getOppositeDirection(direction: String?): String? {
        return when (direction?.lowercase()) {
            "север", "n", "north" -> "юг"
            "юг", "s", "south" -> "север"
            "восток", "e", "east" -> "запад"
            "запад", "w", "west" -> "восток"
            "вверх", "u", "up" -> "вниз"
            "вниз", "d", "down" -> "вверх"
            "северо-восток", "ne", "northeast" -> "юго-запад"
            "северо-запад", "nw", "northwest" -> "юго-восток"
            "юго-восток", "se", "southeast" -> "северо-запад"
            "юго-запад", "sw", "southwest" -> "северо-восток"
            else -> null
        }
    }

    /**
     * Получить статус навигации
     */
    fun getStatus(): Map<String, Any> {
        return mapOf(
            "hasTarget" to hasTarget(),
            "targetRoomId" to (targetRoomId ?: ""),
            "targetRoomName" to (targetRoomName ?: ""),
            "pathLength" to currentPath.size,
            "pathIndex" to pathIndex,
            "remainingSteps" to (currentPath.size - pathIndex),
            "lastDirection" to (lastDirection ?: ""),
            "visitedRoomsInSession" to visitedRooms.size
        )
    }

    /**
     * Очистить историю посещений
     */
    fun clearVisitedRooms() {
        visitedRooms.clear()
        bot.log("История посещений очищена")
    }
}
