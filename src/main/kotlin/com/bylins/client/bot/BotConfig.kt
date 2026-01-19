package com.bylins.client.bot

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Конфигурация AI-бота
 * Может изменяться в runtime через команды или API
 */
@Serializable
data class BotConfig(
    // Общие настройки
    var enabled: Boolean = false,
    var mode: BotMode = BotMode.IDLE,
    var autoStart: Boolean = false,

    // Настройки безопасности
    var fleeHpPercent: Int = 20,          // Убегать при HP ниже этого %
    var restHpPercent: Int = 70,          // Отдыхать пока HP ниже этого %
    var restManaPercent: Int = 30,        // Отдыхать пока мана ниже этого %
    var maxDeathsPerSession: Int = 3,     // Макс. смертей до автостопа
    var autoRecallOnLowHp: Boolean = true, // Авто-рекол при низком HP
    var safeZones: List<String> = emptyList(), // Безопасные зоны для отдыха

    // Настройки боя
    var autoAttack: Boolean = true,       // Атаковать мобов автоматически
    var autoLoot: Boolean = true,         // Автолут
    var autoLootGold: Boolean = true,     // Автолут золота
    var autoLootItems: Boolean = false,   // Автолут предметов
    var lootFilter: List<String> = emptyList(), // Фильтр предметов для лута
    var avoidAggressive: Boolean = false, // Избегать агрессивных мобов
    var maxMobLevel: Int? = null,         // Макс. уровень мобов для атаки
    var minMobLevel: Int? = null,         // Мин. уровень мобов для атаки
    var targetPriority: TargetPriority = TargetPriority.WEAKEST, // Приоритет выбора цели

    // Настройки навигации
    var autoExplore: Boolean = false,     // Автоисследование
    var stayInZone: Boolean = true,       // Оставаться в текущей зоне
    var preferredZones: List<String> = emptyList(), // Предпочтительные зоны
    var avoidZones: List<String> = emptyList(),     // Избегаемые зоны
    var maxPathLength: Int = 50,          // Макс. длина пути

    // Настройки баффов
    var autoBuffs: Boolean = true,        // Автобаффы
    var buffList: List<String> = emptyList(), // Список баффов для поддержания
    var buffMinDuration: Int = 5,         // Мин. остаточное время баффа для пропуска

    // Настройки отдыха
    var autoRest: Boolean = true,         // Автоотдых
    var autoSleep: Boolean = false,       // Автосон (быстрее восстановление)
    var restInSafeRooms: Boolean = true,  // Отдыхать только в безопасных комнатах

    // Настройки обучения
    var collectTrainingData: Boolean = true, // Собирать данные для ML
    var useMLDecisions: Boolean = false,     // Использовать ML для решений

    // Настройки LLM
    var useLLMParsing: Boolean = true,    // Использовать LLM для парсинга
    var llmModel: String = "llama3",      // Модель Ollama
    var llmBaseUrl: String = "http://localhost:11434", // URL Ollama

    // Настройки логирования
    var verboseLogging: Boolean = false,  // Подробное логирование
    var logToFile: Boolean = true,        // Логирование в файл
    var logCombat: Boolean = true         // Логировать бой
) {
    fun toJson(): String = Json.encodeToString(serializer(), this)

    fun toMap(): Map<String, Any> = mapOf(
        "enabled" to enabled,
        "mode" to mode.name,
        "autoStart" to autoStart,
        "fleeHpPercent" to fleeHpPercent,
        "restHpPercent" to restHpPercent,
        "restManaPercent" to restManaPercent,
        "maxDeathsPerSession" to maxDeathsPerSession,
        "autoRecallOnLowHp" to autoRecallOnLowHp,
        "safeZones" to safeZones,
        "autoAttack" to autoAttack,
        "autoLoot" to autoLoot,
        "autoLootGold" to autoLootGold,
        "autoLootItems" to autoLootItems,
        "lootFilter" to lootFilter,
        "avoidAggressive" to avoidAggressive,
        "maxMobLevel" to (maxMobLevel ?: -1),
        "minMobLevel" to (minMobLevel ?: -1),
        "targetPriority" to targetPriority.name,
        "autoExplore" to autoExplore,
        "stayInZone" to stayInZone,
        "preferredZones" to preferredZones,
        "avoidZones" to avoidZones,
        "maxPathLength" to maxPathLength,
        "autoBuffs" to autoBuffs,
        "buffList" to buffList,
        "buffMinDuration" to buffMinDuration,
        "autoRest" to autoRest,
        "autoSleep" to autoSleep,
        "restInSafeRooms" to restInSafeRooms,
        "collectTrainingData" to collectTrainingData,
        "useMLDecisions" to useMLDecisions,
        "useLLMParsing" to useLLMParsing,
        "llmModel" to llmModel,
        "llmBaseUrl" to llmBaseUrl,
        "verboseLogging" to verboseLogging,
        "logToFile" to logToFile,
        "logCombat" to logCombat
    )

    companion object {
        fun fromJson(json: String): BotConfig = Json.decodeFromString(serializer(), json)

        fun fromMap(map: Map<String, Any>): BotConfig {
            val config = BotConfig()
            map["enabled"]?.let { config.enabled = it as? Boolean ?: false }
            map["mode"]?.let { config.mode = BotMode.valueOf(it.toString()) }
            map["autoStart"]?.let { config.autoStart = it as? Boolean ?: false }
            map["fleeHpPercent"]?.let { config.fleeHpPercent = (it as? Number)?.toInt() ?: 20 }
            map["restHpPercent"]?.let { config.restHpPercent = (it as? Number)?.toInt() ?: 70 }
            map["restManaPercent"]?.let { config.restManaPercent = (it as? Number)?.toInt() ?: 30 }
            map["maxDeathsPerSession"]?.let { config.maxDeathsPerSession = (it as? Number)?.toInt() ?: 3 }
            map["autoRecallOnLowHp"]?.let { config.autoRecallOnLowHp = it as? Boolean ?: true }
            @Suppress("UNCHECKED_CAST")
            map["safeZones"]?.let { config.safeZones = it as? List<String> ?: emptyList() }
            map["autoAttack"]?.let { config.autoAttack = it as? Boolean ?: true }
            map["autoLoot"]?.let { config.autoLoot = it as? Boolean ?: true }
            map["autoLootGold"]?.let { config.autoLootGold = it as? Boolean ?: true }
            map["autoLootItems"]?.let { config.autoLootItems = it as? Boolean ?: false }
            @Suppress("UNCHECKED_CAST")
            map["lootFilter"]?.let { config.lootFilter = it as? List<String> ?: emptyList() }
            map["avoidAggressive"]?.let { config.avoidAggressive = it as? Boolean ?: false }
            map["maxMobLevel"]?.let {
                val level = (it as? Number)?.toInt() ?: -1
                config.maxMobLevel = if (level > 0) level else null
            }
            map["minMobLevel"]?.let {
                val level = (it as? Number)?.toInt() ?: -1
                config.minMobLevel = if (level > 0) level else null
            }
            map["targetPriority"]?.let { config.targetPriority = TargetPriority.valueOf(it.toString()) }
            map["autoExplore"]?.let { config.autoExplore = it as? Boolean ?: false }
            map["stayInZone"]?.let { config.stayInZone = it as? Boolean ?: true }
            @Suppress("UNCHECKED_CAST")
            map["preferredZones"]?.let { config.preferredZones = it as? List<String> ?: emptyList() }
            @Suppress("UNCHECKED_CAST")
            map["avoidZones"]?.let { config.avoidZones = it as? List<String> ?: emptyList() }
            map["maxPathLength"]?.let { config.maxPathLength = (it as? Number)?.toInt() ?: 50 }
            map["autoBuffs"]?.let { config.autoBuffs = it as? Boolean ?: true }
            @Suppress("UNCHECKED_CAST")
            map["buffList"]?.let { config.buffList = it as? List<String> ?: emptyList() }
            map["buffMinDuration"]?.let { config.buffMinDuration = (it as? Number)?.toInt() ?: 5 }
            map["autoRest"]?.let { config.autoRest = it as? Boolean ?: true }
            map["autoSleep"]?.let { config.autoSleep = it as? Boolean ?: false }
            map["restInSafeRooms"]?.let { config.restInSafeRooms = it as? Boolean ?: true }
            map["collectTrainingData"]?.let { config.collectTrainingData = it as? Boolean ?: true }
            map["useMLDecisions"]?.let { config.useMLDecisions = it as? Boolean ?: false }
            map["useLLMParsing"]?.let { config.useLLMParsing = it as? Boolean ?: true }
            map["llmModel"]?.let { config.llmModel = it.toString() }
            map["llmBaseUrl"]?.let { config.llmBaseUrl = it.toString() }
            map["verboseLogging"]?.let { config.verboseLogging = it as? Boolean ?: false }
            map["logToFile"]?.let { config.logToFile = it as? Boolean ?: true }
            map["logCombat"]?.let { config.logCombat = it as? Boolean ?: true }
            return config
        }
    }
}

/**
 * Приоритет выбора цели в бою
 */
@Serializable
enum class TargetPriority {
    WEAKEST,        // Самый слабый (меньше HP)
    STRONGEST,      // Самый сильный (больше HP)
    LOWEST_LEVEL,   // Самый низкоуровневый
    HIGHEST_LEVEL,  // Самый высокоуровневый
    MOST_EXP,       // Больше всего опыта
    NEAREST,        // Ближайший (первый в списке)
    RANDOM          // Случайный
}
