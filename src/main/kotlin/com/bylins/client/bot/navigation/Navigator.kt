package com.bylins.client.bot.navigation

import com.bylins.client.bot.BotCore
import mu.KotlinLogging

/**
 * Навигатор бота
 * Управляет перемещением по карте
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
    private val moveDelay = 500L // Минимальная задержка между движениями

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
        val path = findPathTo(roomId)
        if (path != null) {
            currentPath = path
            pathIndex = 0
            bot.log("Path found to $roomId: ${path.size} steps")
        } else {
            currentPath = emptyList()
            bot.log("No path found to $roomId")
        }
    }

    /**
     * Установить цель по имени комнаты
     */
    fun setTargetByName(roomName: String) {
        targetRoomName = roomName
        // TODO: Поиск комнаты по имени
    }

    /**
     * Найти следующую цель (ближайшую непосещённую комнату с мобами)
     */
    fun findNextTarget(): Boolean {
        val config = bot.config.value

        // Если нужно оставаться в зоне
        if (config.stayInZone) {
            val currentZone = bot.characterState.value?.zoneId ?: return false
            return findTargetInZone(currentZone)
        }

        // Ищем в предпочитаемых зонах
        for (zone in config.preferredZones) {
            if (findTargetInZone(zone)) {
                return true
            }
        }

        // Ищем где угодно
        return findAnyTarget()
    }

    /**
     * Найти цель в зоне
     */
    private fun findTargetInZone(zoneId: String): Boolean {
        // Получаем список комнат с мобами из БД бота
        val mobSpawns = bot.database.getMobsByZone(zoneId)

        // Ищем непосещённую комнату с мобами
        // TODO: Нужен доступ к списку комнат зоны из маппера

        return false
    }

    /**
     * Найти любую цель
     */
    private fun findAnyTarget(): Boolean {
        // TODO: Реализовать
        return false
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
            targetRoomId = null
            currentPath = emptyList()
            pathIndex = 0
            return
        }

        val direction = currentPath[pathIndex]
        lastDirection = direction
        lastMoveTime = now
        pathIndex++

        bot.send(direction)
    }

    /**
     * Исследовать случайное направление
     */
    fun exploreRandom() {
        val now = System.currentTimeMillis()
        if (now - lastMoveTime < moveDelay) {
            return
        }

        // Получаем текущую комнату
        val room = bot.getRoom() ?: return

        @Suppress("UNCHECKED_CAST")
        val exits = room["exits"] as? Map<String, Any> ?: return
        if (exits.isEmpty()) return

        // Выбираем случайное направление (исключая откуда пришли)
        val availableDirections = exits.keys.filter { it != getOppositeDirection(lastDirection) }
        if (availableDirections.isEmpty()) return

        val direction = availableDirections.random()
        lastDirection = direction
        lastMoveTime = now

        bot.send(direction)
    }

    /**
     * Обработать вход в комнату
     */
    fun onRoomEnter(room: Map<String, Any>) {
        val roomId = room["id"] as? String ?: return
        visitedRooms.add(roomId)

        // Проверяем достигли ли цели
        if (roomId == targetRoomId) {
            bot.log("Reached target room: $roomId")
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
    }

    /**
     * Найти путь к комнате
     */
    private fun findPathTo(roomId: String): List<String>? {
        // Используем маппер клиента
        // TODO: Вызвать findPath из API
        return null
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
            "pathLength" to currentPath.size,
            "pathIndex" to pathIndex,
            "remainingSteps" to (currentPath.size - pathIndex),
            "lastDirection" to (lastDirection ?: ""),
            "visitedRooms" to visitedRooms.size
        )
    }

    /**
     * Очистить историю посещений
     */
    fun clearVisitedRooms() {
        visitedRooms.clear()
    }
}
