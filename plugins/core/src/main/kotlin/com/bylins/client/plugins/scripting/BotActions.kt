package com.bylins.client.plugins.scripting

/**
 * Интерфейс действий для AI-бота
 */
interface BotActions {
    // Боевая информация
    fun getMobsInRoom(): List<Map<String, Any>>
    fun getPlayersInRoom(): List<Map<String, Any>>
    fun getMyTarget(): Map<String, Any>?
    fun isInCombat(): Boolean
    fun getCombatLog(limit: Int): List<Map<String, Any>>

    // Инвентарь
    fun getInventory(): List<Map<String, Any>>
    fun getEquipment(): Map<String, Map<String, Any>>
    fun findItem(pattern: String): Map<String, Any>?

    // Состояние персонажа
    fun getAffects(): List<Map<String, Any>>
    fun getSkills(): Map<String, Map<String, Any>>
    fun getPosition(): String
    fun getCharacterState(): Map<String, Any>

    // Управление ботом
    fun botStart(mode: String, config: Map<String, Any>?)
    fun botStop()
    fun getBotStatus(): Map<String, Any>
    fun setBotConfig(config: Map<String, Any>)
    fun getBotConfig(): Map<String, Any>

    // База данных бота
    fun saveMobData(mobId: String, data: Map<String, Any>)
    fun getMobData(mobId: String): Map<String, Any>?
    fun findMobsByName(pattern: String): List<Map<String, Any>>
    fun getZoneStats(zoneId: String): Map<String, Any>?
    fun getZonesForLevel(level: Int): List<Map<String, Any>>

    // LLM парсинг
    fun parseRoomDescription(text: String): Map<String, Any>
    fun parseCombatMessage(text: String): Map<String, Any>?
    fun parseInspectResult(text: String): Map<String, Any>?

    // ML интерфейс
    fun loadModel(name: String, path: String): Boolean
    fun predict(modelName: String, input: List<Double>): List<Double>?
    fun saveExperience(type: String, data: Map<String, Any>)

    // Combat profiles
    fun getCombatProfiles(limit: Int, filter: Map<String, Any>?): List<Map<String, Any>>
    fun getCombatProfile(id: Int): Map<String, Any>?
    fun getCombatStats(): Map<String, Any>
}
