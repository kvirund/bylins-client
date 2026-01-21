package com.bylins.client.bot.perception

import com.bylins.client.bot.*
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * Трекер сущностей в комнате
 * Отслеживает мобов, игроков и предметы
 */
private val logger = KotlinLogging.logger("EntityTracker")

class EntityTracker(private val bot: BotCore) {

    // Текущие мобы в комнате
    private val mobsInRoom = ConcurrentHashMap<String, MobInfo>()

    // Текущие игроки в комнате
    private val playersInRoom = ConcurrentHashMap<String, PlayerInfo>()

    // Предметы на полу
    private val itemsOnGround = ConcurrentHashMap<String, ItemInfo>()

    // ID текущей комнаты
    private var currentRoomId: String? = null

    /**
     * Получить список мобов в комнате
     */
    fun getMobsInRoom(): List<MobInfo> {
        return mobsInRoom.values.toList()
    }

    /**
     * Получить список игроков в комнате
     */
    fun getPlayersInRoom(): List<PlayerInfo> {
        return playersInRoom.values.toList()
    }

    /**
     * Получить список предметов на полу
     */
    fun getItemsOnGround(): List<ItemInfo> {
        return itemsOnGround.values.toList()
    }

    /**
     * Обработать вход в комнату
     */
    fun onRoomEnter(room: Map<String, Any>) {
        val roomId = room["id"] as? String ?: return

        // Если сменилась комната - очищаем данные
        if (roomId != currentRoomId) {
            mobsInRoom.clear()
            playersInRoom.clear()
            itemsOnGround.clear()
            currentRoomId = roomId
        }

        // TODO: Парсить описание комнаты для извлечения мобов
    }

    /**
     * Обработать входящую строку
     */
    fun processLine(line: String) {
        // Паттерны для обнаружения мобов и событий

        // Моб появился
        if (line.contains("пришел") || line.contains("прибежал") || line.contains("прилетел")) {
            parseMobArrival(line)
        }

        // Моб ушёл
        if (line.contains("ушел") || line.contains("убежал") || line.contains("улетел")) {
            parseMobDeparture(line)
        }

        // Моб убит
        if (line.contains("пал") && line.contains("замертво")) {
            parseMobDeath(line)
        }

        // Предмет на полу
        if (line.contains("лежит") || line.contains("валяется")) {
            parseItemOnGround(line)
        }
    }

    /**
     * Добавить моба в трекер
     */
    fun addMob(mob: MobInfo) {
        mobsInRoom[mob.name] = mob
        logger.debug { "Mob added: ${mob.name}" }

        // Сохраняем в БД информацию о спавне
        currentRoomId?.let { roomId ->
            bot.database.saveMobSpawn(roomId, mob.name)
        }
    }

    /**
     * Удалить моба из трекера
     */
    fun removeMob(mobName: String) {
        mobsInRoom.remove(mobName)
        logger.debug { "Mob removed: $mobName" }
    }

    /**
     * Добавить игрока в трекер
     */
    fun addPlayer(player: PlayerInfo) {
        playersInRoom[player.name] = player
        logger.debug { "Player added: ${player.name}" }
    }

    /**
     * Удалить игрока из трекера
     */
    fun removePlayer(playerName: String) {
        playersInRoom.remove(playerName)
        logger.debug { "Player removed: $playerName" }
    }

    /**
     * Добавить предмет на пол
     */
    fun addItem(item: ItemInfo) {
        itemsOnGround[item.name] = item
    }

    /**
     * Удалить предмет с пола
     */
    fun removeItem(itemName: String) {
        itemsOnGround.remove(itemName)
    }

    /**
     * Парсинг появления моба
     */
    private fun parseMobArrival(line: String) {
        // Паттерны типа "Злобный орк пришел с севера"
        val arrivalPatterns = listOf(
            Regex("^(.+) приш[её]л (.+)$"),
            Regex("^(.+) прибежал (.+)$"),
            Regex("^(.+) прилетел (.+)$")
        )

        for (pattern in arrivalPatterns) {
            val match = pattern.find(line)
            if (match != null) {
                val mobName = match.groupValues[1].trim()
                addMob(MobInfo(name = mobName))
                return
            }
        }
    }

    /**
     * Парсинг ухода моба
     */
    private fun parseMobDeparture(line: String) {
        val departurePatterns = listOf(
            Regex("^(.+) уш[её]л (.+)$"),
            Regex("^(.+) убежал (.+)$"),
            Regex("^(.+) улетел (.+)$")
        )

        for (pattern in departurePatterns) {
            val match = pattern.find(line)
            if (match != null) {
                val mobName = match.groupValues[1].trim()
                removeMob(mobName)
                return
            }
        }
    }

    /**
     * Парсинг смерти моба
     */
    private fun parseMobDeath(line: String) {
        // "Злобный орк пал замертво."
        val deathPattern = Regex("^(.+) пал(?:а)? замертво\\.$")
        val match = deathPattern.find(line)
        if (match != null) {
            val mobName = match.groupValues[1].trim()
            removeMob(mobName)

            // Уведомляем combat manager
            bot.combatManager.onMobKilled(mobName)
        }
    }

    /**
     * Парсинг предмета на полу
     */
    private fun parseItemOnGround(line: String) {
        // Простой паттерн - можно расширить
        val itemPatterns = listOf(
            Regex("^(.+) лежит (.+)$"),
            Regex("^(.+) валяется (.+)$")
        )

        for (pattern in itemPatterns) {
            val match = pattern.find(line)
            if (match != null) {
                val itemName = match.groupValues[1].trim()
                addItem(ItemInfo(name = itemName))
                return
            }
        }
    }

    /**
     * Парсить описание комнаты для извлечения сущностей
     */
    @Suppress("UNUSED_PARAMETER")
    fun parseRoomDescription(_description: String, mobList: String?) {
        // Очищаем текущие данные
        mobsInRoom.clear()

        if (mobList.isNullOrBlank()) return

        // Разбираем список мобов
        // Формат обычно: "Здесь находится злобный орк. Огромный тролль стоит тут."
        val mobLines = mobList.split("\n").filter { it.isNotBlank() }
        var index = 1

        for (mobLine in mobLines) {
            val mobInfo = parseMobFromLine(mobLine, index)
            if (mobInfo != null) {
                addMob(mobInfo)
                index++
            }
        }

        logger.debug { "Parsed ${mobsInRoom.size} mobs from room description" }
    }

    /**
     * Парсить одну строку с мобом
     */
    private fun parseMobFromLine(line: String, index: Int): MobInfo? {
        // Убираем типичные префиксы
        val cleanLine = line
            .replace(Regex("^Здесь находи?тся\\s+"), "")
            .replace(Regex("^\\s*"), "")
            .replace(Regex("\\.$"), "")
            .trim()

        if (cleanLine.isBlank()) return null

        // Определяем состояние
        val condition = when {
            cleanLine.contains("невредим") -> MobCondition.EXCELLENT
            cleanLine.contains("слегка ранен") -> MobCondition.SLIGHTLY_HURT
            cleanLine.contains("ранен") -> MobCondition.HURT
            cleanLine.contains("тяжело ранен") -> MobCondition.BADLY_HURT
            cleanLine.contains("при смерти") -> MobCondition.AWFUL
            else -> MobCondition.UNKNOWN
        }

        // Определяем позицию
        val position = when {
            cleanLine.contains("сражается") -> Position.FIGHTING
            cleanLine.contains("стоит") -> Position.STANDING
            cleanLine.contains("сидит") -> Position.SITTING
            cleanLine.contains("отдыхает") -> Position.RESTING
            cleanLine.contains("спит") -> Position.SLEEPING
            else -> Position.STANDING
        }

        // Определяем сражается ли с кем-то
        val fightingTarget: String? = if (position == Position.FIGHTING) {
            val fightMatch = Regex("сражается с (.+)").find(cleanLine)
            fightMatch?.groupValues?.get(1)
        } else null

        // Извлекаем имя моба (первые слова до описания состояния)
        val name = cleanLine
            .replace(Regex("\\s+(стоит|сидит|отдыхает|спит|сражается).+$"), "")
            .replace(Regex("\\s*\\(.+\\)\\s*"), "") // Убираем (ранен) и т.д.
            .trim()

        return MobInfo(
            name = name,
            condition = condition,
            position = position,
            isFighting = position == Position.FIGHTING,
            fightingTarget = fightingTarget,
            roomIndex = index
        )
    }

    /**
     * Получить данные для API
     */
    fun getMobsAsMap(): List<Map<String, Any>> {
        return mobsInRoom.values.map { mob ->
            mapOf(
                "name" to mob.name,
                "shortName" to (mob.shortName ?: ""),
                "level" to (mob.level ?: -1),
                "hpPercent" to (mob.hpPercent ?: -1),
                "condition" to (mob.condition?.name ?: "UNKNOWN"),
                "position" to (mob.position?.name ?: "UNKNOWN"),
                "isAggressive" to (mob.isAggressive ?: false),
                "isFighting" to mob.isFighting,
                "fightingTarget" to (mob.fightingTarget ?: ""),
                "roomIndex" to mob.roomIndex
            )
        }
    }

    fun getPlayersAsMap(): List<Map<String, Any>> {
        return playersInRoom.values.map { player ->
            mapOf(
                "name" to player.name,
                "title" to (player.title ?: ""),
                "clan" to (player.clan ?: ""),
                "level" to (player.level ?: -1),
                "className" to (player.className ?: ""),
                "position" to (player.position?.name ?: "UNKNOWN"),
                "isFighting" to player.isFighting,
                "isGroupMember" to player.isGroupMember
            )
        }
    }

    fun getItemsAsMap(): List<Map<String, Any>> {
        return itemsOnGround.values.map { item ->
            mapOf(
                "name" to item.name,
                "shortName" to (item.shortName ?: ""),
                "type" to (item.type?.name ?: "OTHER"),
                "level" to (item.level ?: -1),
                "count" to item.count
            )
        }
    }
}
